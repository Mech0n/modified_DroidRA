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

class CheckRecursionVisitorLC implements switchLC {
  boolean recursive = false;
  Set<LanguageConstraints> seen;
  boolean follow_calls;

  CheckRecursionVisitorLC() {
    seen = new HashSet<LanguageConstraints>();
  }

  CheckRecursionVisitorLC(boolean follow_calls0) {
    seen = new HashSet<LanguageConstraints>();
    follow_calls = follow_calls0;
  }

  @Override
  public boolean setFieldMode(boolean mode) {
    return false;
  }

  /***************************************************************************/
  @Override
  public void caseTop(LanguageConstraints.Top lc) {
    ;
  }

  @Override
  public void caseBottom(LanguageConstraints.Bottom lc) {
    ;
  }

  @Override
  public void caseTerminal(LanguageConstraints.Terminal lc) {
    ;
  }

  @Override
  public void caseParameter(LanguageConstraints.Parameter lc) {
    ;
  }

  @Override
  public void caseUnion(LanguageConstraints.Union lc) {
    if (seen.contains(lc)) {
      recursive = true;
      return;
    }
    seen.add(lc);
    Iterator<LanguageConstraints.Box> it = lc.elements.iterator();
    while (it.hasNext()) {
      LanguageConstraints.Box lcb = it.next();
      lcb.apply(this);
    }
    assert (seen.contains(lc));
    seen.remove(lc);
  }

  @Override
  public void caseConcatenate(LanguageConstraints.Concatenate lc) {
    if (seen.contains(lc)) {
      recursive = true;
      return;
    }
    seen.add(lc);
    lc.left.apply(this);
    lc.right.apply(this);
    assert (seen.contains(lc));
    seen.remove(lc);
  }

  @Override
  public void caseEq(LanguageConstraints.Eq lc) {
    if (seen.contains(lc)) {
      recursive = true;
      return;
    }
    seen.add(lc);
    lc.lcb.apply(this);
    assert (seen.contains(lc));
    seen.remove(lc);
  }

  @Override
  public void casePending(LanguageConstraints.Pending lc) {
    recursive = true;
  }

  @Override
  public void caseCall(Call lc) {
    assert (!follow_calls); // TBD Recursion within a procedure, or overall?
  }
}