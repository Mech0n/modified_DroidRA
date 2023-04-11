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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import soot.Body;
import soot.BodyTransformer;
import soot.G;
import soot.PackManager;
import soot.SootMethod;
import soot.Transform;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.InvokeExpr;
import soot.jimple.ReturnStmt;
import soot.jimple.Stmt;
import soot.shimple.ShimpleBody;
import soot.toolkits.graph.ExceptionalUnitGraph;

public class GlobalTester extends BodyTransformer {

  /*
   * Assume we have a class dummy which has a function "String copy1(String s1)" Find the calls to
   * this 'dummy.copy1' function, and figure out the language for its argument
   */
  public static void main(String[] args) {
    System.out.println("in Tester.main");
    PackManager.v().getPack("jtp").add(new Transform("jtp.Tester", new GlobalTester()));
    ToStringVisitor.show_uid = false; // Hackish!
    soot.Main.main(args);
  }

  @Override
  protected void internalTransform(Body body, String phase, Map options) {
    assert (!(body instanceof ShimpleBody)); // Need a different approach for
                                             // SSA
    SootMethod sm = body.getMethod();
    System.out.println("Tester for:" + sm);
    ExceptionalUnitGraph graph = new ExceptionalUnitGraph(body);

    // A ConstraintCollector collects constraints for the languages of string
    // variables
    ConstraintCollector cc = new ConstraintCollector(graph);

    // Now use the collector to perform the analysis
    Iterator<Unit> unitIt = body.getUnits().snapshotIterator();
    System.out.println("in ConstraintCollector for " + body.getMethod());
    while (unitIt.hasNext()) {
      Unit s = unitIt.next();
      handleStatement(s, cc, sm);
    }
  }

  // Look for calls to copy1 and report the set of strings for the parameter.
  static void handleStatement(Unit u, ConstraintCollector cc, SootMethod current_sm) {
    Stmt s = (Stmt) u;
    if (s.containsInvokeExpr()) {
      InvokeExpr iexpr = s.getInvokeExpr();
      SootMethod sm = iexpr.getMethod();
      String m_signature = sm.getSignature();
      if (m_signature.equals("<java.io.PrintStream: void println(java.lang.String)>")) {
        // OK, so this is a call to copy1
        Value v = iexpr.getArg(0);
        handleValue(v, s, cc, current_sm, "arg0:");
      }
    } else if (s instanceof ReturnStmt) {
      ReturnStmt rs = (ReturnStmt) s;
      ValueBox vb = rs.getOpBox();
      Value v = vb.getValue();
      handleValue(v, s, cc, current_sm, "ret:");
    }
  }

  static void
      handleValue(Value v, Stmt s, ConstraintCollector cc, SootMethod current_sm, String msg) {
    // get the constraints for 'l'
    LanguageConstraints.Box lcb = cc.getConstraintOfValueAt(v, s);
    System.out.println("RESULT-lcb:[" + msg + current_sm.getName() + "]\t=======>\t" + lcb);
    /*
     * Now, in theory we can use any one of the available solvers ...
     * 
     * In particular, DAGSolverVisitorLC is a simple solver (and the only one available for now)
     * that only works if there are no recursive constraints (ex. in a loop) and the set is finite.
     */
    DAGSolverVisitorLC dagvlc = new DAGSolverVisitorLC();
    if (dagvlc.solve(lcb)) {
      /*
       * dagvlc.result is a set of strings which can contain .* for unknown substrings.
       */
      ArrayList<String> list = new ArrayList<String>(dagvlc.result);
      Collections.sort(list);
      System.out.print("RESULT-set:[" + msg + current_sm.getName() + "]\t=======>\t{");
      for (Iterator<String> lit = list.iterator(); lit.hasNext();) {
        String lstr = lit.next();
        System.out.print("\"" + lstr + "\"; ");
      }
      System.out.println("}");
    }
  }
}
