/*
 * Copyright 2019 HM Revenue & Customs
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

package model

import model.Scheme.Scheme
import model.persisted.SchemeEvaluationResult
import play.api.libs.json._
import reactivemongo.bson.{ BSON, BSONHandler, BSONString }

object EvaluationResults {
  sealed trait Result {
    def toPassmark: String
  }
  case object Green extends Result {
    def toPassmark: String = "Pass"
  }
  case object Amber extends Result {
    def toPassmark: String = "Amber"
  }
  case object Red extends Result {
    def toPassmark: String = "Fail"
  }

  object Result {
    def apply(s: String): Result = s match {
      case "Red" => Red
      case "Green" => Green
      case "Amber" => Amber
    }

    implicit object BSONEnumHandler extends BSONHandler[BSONString, Result] {
      def read(doc: BSONString) = Result(doc.value)

      def write(result: Result) = BSON.write(result.toString)
    }

    implicit val resultFormat = new Format[Result] {
      def reads(json: JsValue) = JsSuccess(Result(json.as[String]))

      def writes(result: Result) = JsString(result.toString)
    }
  }

  case class CompetencyAverageResult(
                                      leadingAndCommunicatingAverage: Double,
                                      collaboratingAndPartneringAverage: Double,
                                      deliveringAtPaceAverage: Double,
                                      makingEffectiveDecisionsAverage: Double,
                                      changingAndImprovingAverage: Double,
                                      buildingCapabilityForAllAverage: Double,
                                      motivationFitAverage: Double,
                                      overallScore: Double) {

    def scoresWithWeightOne = List(
      leadingAndCommunicatingAverage,
      collaboratingAndPartneringAverage,
      deliveringAtPaceAverage,
      makingEffectiveDecisionsAverage,
      changingAndImprovingAverage,
      buildingCapabilityForAllAverage
    )

    def scoresWithWeightTwo = List(motivationFitAverage)
  }

  object CompetencyAverageResult {
    implicit val CompetencyAverageResultFormats = Json.format[CompetencyAverageResult]
  }

  case class AssessmentRuleCategoryResult(passedMinimumCompetencyLevel: Option[Boolean],
                                          competencyAverageResult: CompetencyAverageResult,
                                          schemesEvaluation: List[SchemeEvaluationResult],
                                          overallEvaluation: List[SchemeEvaluationResult])
}
