package org.jetbrains.plugins.scala
package codeInspection.methodSignature

import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.PsiMethod
import quickfix.RemoveCallParentheses

/**
 * Pavel Fatin
 */

class JavaAccessorMethodCalledAsEmptyParenInspection extends AbstractMethodSignatureInspection(
  "ScalaJavaAccessorMethodCalledAsEmptyParen", "Java accessor method called as empty-paren") {

  def actionFor(holder: ProblemsHolder) = {
    case e: ScReferenceExpression => e.getParent match {
      case call: ScMethodCall if (call.getParent == null || !call.getParent.isInstanceOf[ScMethodCall]) =>
      e.resolve() match {
        case _: ScalaPsiElement => // do nothing
        case m: PsiMethod => holder.registerProblem(e.nameId, getDisplayName, new RemoveCallParentheses(call))
        case _ =>
      }
      case _ =>
    }
  }
}
