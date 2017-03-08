/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package services.evaluation

import model.EvaluationResults._
import model.persisted.SchemeEvaluationResult

trait FinalResultEvaluator {
  case class OnlineTestAndAssessmentResultPairNotFound(msg: String) extends Exception(msg)

  // Determines the overall result for each scheme based on the online test result and the assessment centre result
  def determineOverallResultForEachScheme(onlineTestEvaluation: List[SchemeEvaluationResult],
                                          assessmentCentreEvaluation: List[SchemeEvaluationResult]): List[SchemeEvaluationResult] = {
    //scalastyle:off
    println("****")
    println(s"**** determineOverallResultForEachScheme onlineTestEvaluation = $onlineTestEvaluation")
    println(s"**** determineOverallResultForEachScheme assessmentCentreEvaluation = $assessmentCentreEvaluation")
    println("****")
    val overallResult = onlineTestEvaluation.map { otEval: SchemeEvaluationResult =>
      val acEval = assessmentCentreEvaluation.filter(_.scheme == otEval.scheme).head
      println(s"**** determineOverallResultForEachScheme otEval = $otEval, acEval = $acEval")
      (otEval.result, acEval.result) match {
        case (Red, _) =>
          println(s"**** determineOverallResultForEachScheme step1 triggered - Red")
          otEval.scheme -> SchemeEvaluationResult(otEval.scheme, Red)
        case (_, Red) =>
          println(s"**** determineOverallResultForEachScheme step2 triggered - Red")
          otEval.scheme -> SchemeEvaluationResult(otEval.scheme, Red)
        case (Green, Green) =>
          println(s"**** determineOverallResultForEachScheme step3 triggered - Green")
          otEval.scheme -> SchemeEvaluationResult(otEval.scheme, Green)
        case (Amber, _) =>
          println(s"**** determineOverallResultForEachScheme step4 triggered - Amber")
          otEval.scheme -> SchemeEvaluationResult(otEval.scheme, Amber)
        case (Green, Amber) =>
          println(s"**** determineOverallResultForEachScheme step5 triggered - Amber")
          otEval.scheme -> SchemeEvaluationResult(otEval.scheme, Amber)
        case _ =>
          throw OnlineTestAndAssessmentResultPairNotFound(s"The pair: Online Test [${otEval.result}] and " +
            s"Assessment Centre result [${acEval.result}] are not in acceptable state: Red/Amber/Green")
      }
    }.map {
      case (name, result) => result
    }
    overallResult
  }
}
