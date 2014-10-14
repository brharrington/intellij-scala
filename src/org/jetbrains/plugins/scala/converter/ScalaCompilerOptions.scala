package org.jetbrains.plugins.scala
package converter


/**
 * @author Pavel Fatin
 */
private case class ScalaCompilerOptions(warnings: Boolean,
                                        deprecationWarnings: Boolean,
                                        uncheckedWarnings: Boolean,
                                        optimiseBytecode: Boolean,
                                        explainTypeErrors: Boolean,
                                        continuations: Boolean,
                                        debuggingInfoLevel: String,
                                        additionalCompilerOptions: Seq[String])

private object ScalaCompilerOptions {
  def generalize(others: Seq[ScalaCompilerOptions]): ScalaCompilerOptions = {
    def exists(predicate: ScalaCompilerOptions => Boolean) = others.exists(predicate)

    ScalaCompilerOptions(
      warnings = exists(_.warnings),
      deprecationWarnings = exists(_.deprecationWarnings),
      uncheckedWarnings = exists(_.uncheckedWarnings),
      optimiseBytecode = exists(_.optimiseBytecode),
      explainTypeErrors = exists(_.explainTypeErrors),
      continuations = exists(_.continuations),
      debuggingInfoLevel = "", // TODO
      additionalCompilerOptions = Seq.empty)
  }
}