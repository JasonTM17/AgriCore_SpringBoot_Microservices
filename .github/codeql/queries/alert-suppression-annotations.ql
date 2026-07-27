/**
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2006-2025 GitHub, Inc.
 *
 * Derived from github/codeql at codeql-cli/v2.26.1
 * (373814b4300341b090f18e6a75c92a65cb2f193a), then kept local so CodeQL
 * analysis does not depend on cloning an external repository at runtime.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * @name Alert suppression using annotations
 * @description Generates information about alert suppressions using
 *              'SuppressWarnings' annotations.
 * @kind alert-suppression
 * @id java/alert-suppression-annotations
 */

import java
import Metrics.Internal.Extents

/** Gets the LGTM suppression annotation text in the string `s`, if any. */
bindingset[s]
string getAnnotationText(string s) {
  // Match `lgtm[...]` or `codeql[...]` anywhere in the annotation value.
  result = s.regexpFind("(?i)\\b(lgtm|codeql)\\s*\\[[^\\]]*\\]", _, _).trim()
}

/** An alert suppression annotation. */
class SuppressionAnnotation extends SuppressWarningsAnnotation {
  string text;

  SuppressionAnnotation() {
    text = this.getASuppressedWarning() and
    exists(getAnnotationText(text))
  }

  /** Gets the text of this suppression annotation. */
  string getText() { result = text }

  private Annotation getASiblingAnnotation() {
    result = this.getAnnotatedElement().(Annotatable).getAnAnnotation() and
    (
      this.getAnnotatedElement() instanceof Callable or
      this.getAnnotatedElement() instanceof RefType
    )
  }

  private Annotation firstAnnotation() {
    result =
      min(this.getASiblingAnnotation() as m
        order by
          m.getLocation().getStartLine(), m.getLocation().getStartColumn()
      )
  }

  /**
   * Holds if this annotation applies to the range from column `startcolumn` of
   * line `startline` to column `endcolumn` of line `endline` in `filepath`.
   */
  predicate covers(string filepath, int startline, int startcolumn, int endline, int endcolumn) {
    if this.firstAnnotation().hasLocationInfo(filepath, _, _, _, _)
    then
      this.getAnnotatedElement().hasLocationInfo(filepath, _, _, endline, endcolumn) and
      this.firstAnnotation().hasLocationInfo(filepath, startline, startcolumn, _, _)
    else
      this.getAnnotatedElement()
          .hasLocationInfo(filepath, startline, startcolumn, endline, endcolumn)
  }

  /** Gets the scope of this suppression. */
  SuppressionScope getScope() { this = result.getSuppressionAnnotation() }
}

/** The scope of an alert suppression annotation. */
class SuppressionScope extends @annotation instanceof SuppressionAnnotation {
  /** Gets a suppression annotation with this scope. */
  SuppressionAnnotation getSuppressionAnnotation() { result = this }

  /**
   * Holds if this element is at the specified location. The location spans
   * column `startcolumn` of line `startline` to column `endcolumn` of line
   * `endline` in `filepath`.
   */
  predicate hasLocationInfo(
    string filepath, int startline, int startcolumn, int endline, int endcolumn
  ) {
    super.covers(filepath, startline, startcolumn, endline, endcolumn)
  }

  /** Gets a textual representation of this element. */
  string toString() { result = "suppression range" }
}

from SuppressionAnnotation c, string text, string annotationText
where
  text = c.getText() and
  annotationText = getAnnotationText(text)
select c, // suppression entity
  text, // full text of suppression string
  annotationText.regexpReplaceAll("(?i)^codeql", "lgtm"), // LGTM suppression annotation text
  c.getScope() // scope of suppression
