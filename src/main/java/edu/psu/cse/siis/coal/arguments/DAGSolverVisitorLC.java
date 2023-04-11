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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import edu.psu.cse.siis.coal.arguments.LanguageConstraints.Call;

class DAGSolverVisitorLC implements switchLC {
  // Must NOT be applied to a recursive graph

  int warnings = 0;
  Set<String> result;
  int inline_depth = 0;

  DAGSolverVisitorLC() {
    result = new HashSet<String>();
  }

  DAGSolverVisitorLC(int inline_depth0) {
    result = new HashSet<String>();
    inline_depth = inline_depth0;
  }

  boolean solve(LanguageConstraints.Box lcb) {
    if (lcb == null)
      return false;
    CheckRecursionVisitorLC checker = new CheckRecursionVisitorLC();
    lcb.apply(checker);
    if (checker.recursive)
      return false;
    lcb.apply(this);
    return true;
  }

  @Override
  public boolean setFieldMode(boolean mode) {
    return false;
  }

  /***************************************************************************/
  @Override
  public void caseTop(LanguageConstraints.Top lc) {
    warnings++;
  }

  @Override
  public void caseBottom(LanguageConstraints.Bottom lc) {
    result.add("(.*)");
  }

  @Override
  public void caseTerminal(LanguageConstraints.Terminal lc) {
    result.add(lc.term);
  }

  @Override
  public void caseParameter(LanguageConstraints.Parameter lc) {
    result.add("(.*)");
  }

  @Override
  public void caseUnion(LanguageConstraints.Union lc) {
    Iterator<LanguageConstraints.Box> it = lc.elements.iterator();
    while (it.hasNext()) {
      LanguageConstraints.Box lcb = it.next();
      lcb.apply(this);
    }
  }

  @Override
  public void caseConcatenate(LanguageConstraints.Concatenate lc) {
    DAGSolverVisitorLC solveLeft = new DAGSolverVisitorLC();
    lc.left.apply(solveLeft);
    ToStringVisitor.show_uid = false; // Hackish ...
    DAGSolverVisitorLC solveRight = new DAGSolverVisitorLC();
    lc.right.apply(solveRight);
    for (Iterator<String> lit = solveLeft.result.iterator(); lit.hasNext();) {
      String lstr = lit.next();
      for (Iterator<String> rit = solveRight.result.iterator(); rit.hasNext();) {
        String rstr = rit.next();
        result.add(lstr + rstr);
      }
    }
    warnings += solveLeft.warnings + solveRight.warnings;
  }

  @Override
  public void caseEq(LanguageConstraints.Eq lc) {
    lc.lcb.apply(this);
  }

  @Override
  public void casePending(LanguageConstraints.Pending lc) {
    throw new RuntimeException("BAD PENDING!");
  }

  @Override
  public void caseCall(Call lc) {

    if (inline_depth == 0)
      result.add("(.*)");
    else
      assert (inline_depth == 0); // TBD if > 0
  }
}
