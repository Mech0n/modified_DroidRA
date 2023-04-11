/*
 * Copyright (C) 2015 The University of Wisconsin and the Pennsylvania State University
 *
 * Author: Daniel Luchaup
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.psu.cse.siis.coal.arguments;

import java.util.Iterator;
import java.util.Map;

import soot.Body;
import soot.BodyTransformer;
import soot.G;
import soot.Local;
import soot.PackManager;
import soot.SootMethod;
import soot.Transform;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.SpecialInvokeExpr;
import soot.jimple.Stmt;
import soot.shimple.Shimple;
import soot.shimple.ShimpleBody;
import soot.util.Chain;

class AliasAdjuster extends BodyTransformer {

  /*
   * Assume we have a class dummy which has a function "String copy1(String s1)" Find the calls to
   * this 'dummy.copy1' function, and figure out the language for its argument
   */
  public static void main(String[] args) {
    System.out.println("in AliasAdjuster.main");
    PackManager.v().getPack("jtp")// wjtp
        .add(new Transform("jtp.AliasAdjuster", new AliasAdjuster()));
    ToStringVisitor.show_uid = false; // Hackish!
    soot.Main.main(args);
  }

  static class ArgDescriptor {
    Local left;
    Local base;
    Value arg1;
    InvokeExpr iexpr;

    ArgDescriptor() {
      clear();
    }

    void clear() {
      left = null;
      base = null;
      arg1 = null;
      iexpr = null;
    }

    @Override
    public String toString() {
      return "[left=" + left + "; base=" + base + "; arg1=" + arg1 + "; +iexpr=" + iexpr + "]";
    }
  }

  // specialinvoke $r1.<java.lang.StringBuilder: void <init>()>();
  // left=null; base = $r1; arg1 = null
  // specialinvoke $r1.<java.lang.StringBuilder: void <init>(java.lang.String)>(r10);
  // left=null; base = $r1; arg1 = r10
  static boolean match_specialinvoke_StringBuilder_init(Stmt stmt, ArgDescriptor ad) {
    ad.clear();
    if (stmt instanceof InvokeStmt) {
      InvokeStmt istmt = (InvokeStmt) stmt;
      InvokeExpr iexpr = istmt.getInvokeExpr();
      if (iexpr instanceof SpecialInvokeExpr) {
        String signature = iexpr.getMethod().getSignature();
        if (signature.equals("<java.lang.StringBuilder: void <init>()>")) {
          Value base = ((SpecialInvokeExpr) iexpr).getBase();
          if (base instanceof Local) {
            ad.base = (Local) base;
            ad.iexpr = iexpr;
            return true;
          }
          DBG.imprecise("base could be non local");
        }
        if (signature.equals("<java.lang.StringBuilder: void <init>(java.lang.String)>")) {
          Value base = ((SpecialInvokeExpr) iexpr).getBase();
          if (base instanceof Local) {
            ad.base = (Local) base;
            ad.arg1 = iexpr.getArg(1);
            ad.iexpr = iexpr;
            return true;
          }
          DBG.imprecise("base could be non local");
        }
      }
    }
    return false;
  }

  // $r3 = virtualinvoke r2.<java.lang.StringBuilder: java.lang.StringBuilder
  // append(java.lang.String)>("CALL_");
  // left=$r3; base = r2; arg1 = "CALL_"
  // virtualinvoke $r3.<java.lang.StringBuilder: java.lang.StringBuilder
  // append(java.lang.String)>("CHAIN");
  // left=null; base = $r3; arg1 = "CHAIN"
  static boolean match_virtualinvoke_StringBuilder_XXX(Stmt stmt, ArgDescriptor ad) {
    ad.clear();
    InstanceInvokeExpr iiexpr = null;
    Value lop = null;
    if (stmt instanceof InvokeStmt) {
      InvokeStmt istmt = (InvokeStmt) stmt;
      InvokeExpr iexpr = istmt.getInvokeExpr();
      if (iexpr instanceof InstanceInvokeExpr)
        iiexpr = (InstanceInvokeExpr) iexpr;
    } else if (stmt instanceof AssignStmt) {
      AssignStmt astmt = (AssignStmt) stmt;
      lop = astmt.getLeftOp();
      Value rop = astmt.getRightOp();
      if (rop instanceof InstanceInvokeExpr)
        iiexpr = (InstanceInvokeExpr) rop;
    }
    if (iiexpr != null) {
      String signature = iiexpr.getMethod().getSignature();
      // if(signature.equals("<java.lang.StringBuilder: java.lang.StringBuilder append(java.lang.String)>"))
      // if(signature.startsWith("<java.lang.StringBuilder: java.lang.StringBuilder append("))
      if (signature.startsWith("<java.lang.StringBuilder: java.lang.StringBuilder ")) {
        // Assume that all StringBuilder's methods that return a StringBuilder, change it in place
        Value base = iiexpr.getBase();
        if (base instanceof Local) {
          ad.base = (Local) base;
          ad.arg1 = iiexpr.getArg(0);
          ad.iexpr = iiexpr;
          if (lop != null && (lop instanceof Local))
            ad.left = (Local) lop;
          return true;
        }
      }
    }
    return false;
  }

  static boolean changeBody(Body body) {
    // boolean flag =
    // body.getMethod().getSignature()
    // .equals("<com.google.ads.a.j: java.lang.String a(java.util.Map,android.app.Activity)>");
    // if (flag) {
    // System.out.println("Changing body");
    // }
    Chain<Unit> units = body.getUnits();
    SootMethod sm = body.getMethod();
    String sm_string = sm.toString();
    boolean try2change = true;
    LocalDefUse ldu = new LocalDefUse(body);

    Iterator<Unit> stmtIt = units.snapshotIterator();
    ArgDescriptor ad = new ArgDescriptor();

    boolean changed = false;
    boolean last_changed = true;
    while (last_changed) {
      last_changed = false;
      while (stmtIt.hasNext()) {
        Stmt s = (Stmt) stmtIt.next();
        // DBG.print("dbg:LookingAt:"+s);

        // if (match_specialinvoke_StringBuilder_init(s, ad))
        // System.out.println("dbg:match_specialinvoke_StringBuilder_init:"+ad);
        if (match_virtualinvoke_StringBuilder_XXX(s, ad)) {
          DBG.print("dbg:match_virtualinvoke_StringBuilder_XXX%s", ad);
          if (ad.left == null) {
            // Changing this is always safe...
            // virtualinvoke $r3.<java.lang.StringBuilder: java.lang.StringBuilder
            // append(java.lang.String)>("CHAIN");
            if (try2change) {
              AssignStmt newInvoke = Jimple.v().newAssignStmt(ad.base, ad.iexpr);
              // if (flag) {
              // System.out.println("Inserting " + newInvoke);
              // }
              units.insertAfter(newInvoke, s);
              units.remove(s);
              last_changed = true;
            }
          } else {
            // left = virtualinvoke $r3.<java.lang.StringBuilder: java.lang.StringBuilder
            // append(java.lang.String)>("CHAIN");
            if (try2change) {
              if (ldu.shouldMergeResultAndBase(ad.left, ad.base, s)) {
                if (ldu.canReplaceResult(ad.left, ad.base, s)) {
                  ldu.replaceResultAndUses(ad.left, ad.base, s);
                  last_changed = true;
                } else {
                  System.out.println("WARNING:ALIASES-NOT-ACCOUNTED-FOR: at statement: " + s);
                }
              }
            }
          }
        }
      }
      if (last_changed)
        changed = true;
    }
    return changed;
  }

  @Override
  protected void internalTransform(Body body, String phaseName, Map options) {
    SootMethod sm = body.getMethod();
    String sm_string = sm.toString();
    if (!(sm_string.startsWith("<dummy:") || sm_string.startsWith("<examples:")))
      return;
    System.out.println("BEFORE:" + sm_string);
    System.out.println("BEFORE:" + body);

    boolean changed = changeBody(body);

    System.out.println("AFTER:" + body);
    // System.out.println("AFTER:"+sm.getActiveBody());
    if (changed)
      dbgTestConversions(body);
  }

  void dbgTestConversions(Body body) {
    SootMethod method = body.getMethod();
    ShimpleBody shimpleBody = Shimple.v().newBody(body);
    System.out.println("SSA:" + shimpleBody);
    ShimpleBody shimpleBody2 = Shimple.v().newBody(shimpleBody);
    System.out.println("SSA2:" + shimpleBody2);

    System.out.println("METHOD-BODY:" + method.getActiveBody());

    Body jbody = shimpleBody2.toJimpleBody();
    System.out.println("JIMPLE:" + jbody);
    ShimpleBody shimpleBody3 = Shimple.v().newBody(jbody);
    System.out.println("SSA3:" + shimpleBody3);
  }
}
