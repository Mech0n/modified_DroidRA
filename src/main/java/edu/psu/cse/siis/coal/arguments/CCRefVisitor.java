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
import java.util.List;

import soot.G;
import soot.Local;
import soot.Unit;
import soot.Value;
import soot.jimple.ArrayRef;
import soot.jimple.AssignStmt;
import soot.jimple.CaughtExceptionRef;
import soot.jimple.FieldRef;
import soot.jimple.InstanceFieldRef;
import soot.jimple.ParameterRef;
import soot.jimple.RefSwitch;
import soot.jimple.StaticFieldRef;
import soot.jimple.Stmt;
import soot.jimple.ThisRef;

/* PROPAGATE CONSTRAINTS TO REFERENCES/CONTAINERS
 Assume we have the following code
 s1:   $r3 = newarray (java.lang.String)[3];
 s2:   $r3[0] = "ARRAY0";
 s2:   $r3[1] = "ARRAY1";
 s3:   $r3[2] = "ARRAY2";
 s4:   r1 = $r3;
 s5:   r2 = r1[1];
 s8:   use(r2);
 (in the following comment, the value of a statement is the value computed by
 that statement and stored/included in the left operand/set)
 We model/abstract an array as a union of the set of its elements.
 Assume that we've just processed s2, and have just figured out the value of
 s2 i.e. "ARRAY0".
 How do we know that $r3 changes? We have to insert a constraint stating that
 the set of s2 is included in the set of s1.
 Similar for field references, using the appropriate abstraction.
 */
public class CCRefVisitor implements RefSwitch {
  public static final boolean ARRAY_FIELDS = false;
  static int verbose_level = 0;
  LanguageConstraints.Box lcb;
  Stmt stmt;
  CCVisitor svis;

  public CCRefVisitor s(LanguageConstraints.Box lcb0, Stmt s) {
    lcb = lcb0;
    stmt = s;
    return this;
  }

  public CCRefVisitor(CCVisitor svis0) {
    svis = svis0;
  }

  protected void dbg0(String what, Value v) {
    System.out.println("DBG:CCRefVisitor." + what + " v=" + v + " class= " + v.getClass().getName()
        + " type= " + v.getType() + " type-class= " + v.getType().getClass().getName());
  }

  protected void ignore(String what, Value v) {
    if (verbose_level > 10)
      dbg0(what, v);
  }

  protected void dbg(String what, Value v) {
    if (verbose_level > 5)
      dbg0(what, v);
  }

  // ///////////////////////////////////////////////////////////////////////////

  @Override
  public void caseArrayRef(ArrayRef v) {// TBD: revise and make this alias aware
    ignore("caseArrayRef", v);
    Value base = v.getBase();
    DBG.dbgValue("caseArrayRef's base", base);
    if (base instanceof Local) {
      Local l = (Local) base;
      List<Unit> bdefs = svis.mld.getDefsOfAt(l, stmt);
      Iterator<Unit> bDefsIt = bdefs.iterator();
      while (bDefsIt.hasNext()) {
        Stmt sdef = (Stmt) bDefsIt.next();
        sdef.apply(svis);// recursive
        LanguageConstraints.Box defb = Res2Constr.getStmt((sdef));
        assert (defb != null);
        LanguageConstraints.Union lcu = new LanguageConstraints.Union();
        lcu.addLCB(new LanguageConstraints.Box(defb.getLC()));
        lcu.addLCB(lcb);
        defb.setLC(lcu);
        if (ARRAY_FIELDS) {
          /*
           * hack to get array fields working This is a hack, because I need to treat
           * references/point2 aliases in a more systematic way
           */
          if (sdef instanceof AssignStmt) {// assignment to field array
            AssignStmt astmt = (AssignStmt) sdef;
            Value rop = astmt.getRightOp();
            boolean hasfr = astmt.containsFieldRef();
            if (hasfr) {
              FieldRef fr = astmt.getFieldRef();
              Res2Constr.putField(fr, lcb);
            }
          }
        }
      }
    }
  }

  @Override
  public void caseStaticFieldRef(StaticFieldRef v) {
    dbg("caseStaticFieldRef", v);
    Res2Constr.putField(v, lcb);
  }

  @Override
  public void caseInstanceFieldRef(InstanceFieldRef v) {
    ignore("caseInstanceFieldRef", v);
    Res2Constr.putField(v, lcb);
  }

  @Override
  public void caseParameterRef(ParameterRef v) {
    ignore("caseParameterRef", v);
  }

  @Override
  public void caseCaughtExceptionRef(CaughtExceptionRef v) {
    ignore("caseCaughtExceptionRef", v);
  }

  @Override
  public void caseThisRef(ThisRef v) {
    ignore("caseThisRef", v);
  }

  @Override
  public void defaultCase(Object obj) {
    System.out.println("Ignore: CCRefVisitor.defaultCase obj=" + obj + " class= "
        + obj.getClass().getName());
  }
}
