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

import config.AssessmentEvaluationMinimumCompetencyLevel
import model.AssessmentEvaluationCommands.AssessmentPassmarkPreferencesAndScores
import model.CandidateScoresCommands.{ CandidateScores, CandidateScoresAndFeedback }
import model.Commands.AssessmentCentrePassMarkSettingsResponse
import model.EvaluationResults.{ Amber, Red, _ }
import model.PassmarkPersistedObjects.{ AssessmentCentrePassMarkInfo, AssessmentCentrePassMarkScheme, PassMarkSchemeThreshold }
import model.PersistedObjects.{ OnlineTestPassmarkEvaluation, PreferencesWithQualification }
import model.persisted.SchemeEvaluationResult
import model.{ Alternatives, LocationPreference, Preferences }
import org.joda.time.DateTime
import org.scalatest.MustMatchers
import org.scalatestplus.play.PlaySpec
import model.Schemes._

class AssessmentCentrePassmarkRulesEngineSpec extends PlaySpec with MustMatchers {
  val PassmarkSettings = AssessmentCentrePassMarkSettingsResponse(List(
    AssessmentCentrePassMarkScheme(Business, Some(PassMarkSchemeThreshold(1.0, 32.0))),
    AssessmentCentrePassMarkScheme(Commercial, Some(PassMarkSchemeThreshold(5.0, 30.0))),
    AssessmentCentrePassMarkScheme(DigitalAndTechnology, Some(PassMarkSchemeThreshold(27.0, 30.0))),
    AssessmentCentrePassMarkScheme(Finance, Some(PassMarkSchemeThreshold(12.0, 19.0))),
    AssessmentCentrePassMarkScheme(ProjectDelivery, Some(PassMarkSchemeThreshold(23.0, 30.0)))
  ), Some(AssessmentCentrePassMarkInfo("1", DateTime.now, "user")))
  val CandidateScoresWithFeedback = CandidateScoresAndFeedback("app1", Some(true), assessmentIncomplete = false,
    CandidateScores(Some(2.1), Some(3.4), Some(3.3)),
    CandidateScores(None, Some(2.0), Some(3.0)),
    CandidateScores(Some(4.0), None, Some(3.0)),
    CandidateScores(None, Some(3.0), Some(4.0)),
    CandidateScores(Some(4.0), None, Some(4.0)),
    CandidateScores(Some(4.0), Some(4.0), None),
    CandidateScores(Some(2.0), Some(4.0), None))

  val CandidateSchemes = List(model.Scheme.Business)
  //TODO IS: don't think we need the qualifications any more
  val CandidatePreferencesWithQualification = PreferencesWithQualification(CandidateSchemes, aLevel = true, stemLevel = true)

  val CandidateOnlineTestEvaluation = List(
    SchemeEvaluationResult(model.Scheme.Business, Green)
  )

  val rulesEngine = AssessmentCentrePassmarkRulesEngine

  "Assessment Centre Passmark Rules engine evaluation" should {
    "evaluate to passedMinimumCompetencyLevel=false when minimum competency level is enabled and not met" in {
      val config = AssessmentEvaluationMinimumCompetencyLevel(enabled = true, minimumCompetencyLevelScore = Some(2.0),
        motivationalFitMinimumCompetencyLevelScore = Some(4.0))
      val scores = CandidateScoresWithFeedback.copy(collaboratingAndPartnering = CandidateScores(None, Some(1.0), Some(2.0)))
      val candidateScore = AssessmentPassmarkPreferencesAndScores(PassmarkSettings, CandidatePreferencesWithQualification, scores)

      val result = rulesEngine.evaluate2(CandidateOnlineTestEvaluation, candidateScore, config)

      val expected = AssessmentRuleCategoryResultNEW(
        passedMinimumCompetencyLevel = Some(false),
        competencyAverageResult = None, //Option[CompetencyAverageResult],
        schemesEvaluation = List(PerSchemeEvaluation(Business, Red)),
        overallEvaluation = List(PerSchemeEvaluation(Business, Red))
      )

      result mustBe expected
    }

    "evaluate to passedMinimumCompetencyLevel=true and evaluate the schemes" in {
      val config = AssessmentEvaluationMinimumCompetencyLevel(enabled = true, Some(2.0), Some(4.0))
      val scores = CandidateScoresWithFeedback
      val assessmentPassmarkAndScores = AssessmentPassmarkPreferencesAndScores(PassmarkSettings, CandidatePreferencesWithQualification, scores)

      val result = rulesEngine.evaluate2(CandidateOnlineTestEvaluation, assessmentPassmarkAndScores, config)

      result.passedMinimumCompetencyLevel mustBe Some(true)

      val expectedCompetencyAverage = CompetencyAverageResult(2.9333333333333336, 2.5, 3.5, 3.5, 4.0, 4.0, 6.0, 26.433333333333334)
      result.competencyAverageResult mustBe Some(expectedCompetencyAverage)

      result.schemesEvaluation mustBe List(PerSchemeEvaluation(Business, Amber))
      result.overallEvaluation mustBe List(PerSchemeEvaluation(Business, Amber))
    }
  }
}
