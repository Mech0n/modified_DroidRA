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

public interface switchLC {
  public boolean setFieldMode(boolean mode);

  /***********************************************************/
  public abstract void caseTop(LanguageConstraints.Top lc);

  public abstract void caseBottom(LanguageConstraints.Bottom lc);

  public abstract void caseTerminal(LanguageConstraints.Terminal lc);

  public abstract void caseUnion(LanguageConstraints.Union lc);

  public abstract void caseConcatenate(LanguageConstraints.Concatenate lc);

  public abstract void caseEq(LanguageConstraints.Eq lc);

  public abstract void casePending(LanguageConstraints.Pending lc);

  public abstract void caseParameter(LanguageConstraints.Parameter lc);

  public abstract void caseCall(LanguageConstraints.Call lc);
}
