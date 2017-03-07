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

package model

import model.CandidateScoresCommands.CandidateScoresAndFeedback
import model.Commands.AssessmentCentrePassMarkSettingsResponse
import model.PersistedObjects.PreferencesWithQualification
import model.persisted.SchemeEvaluationResult
import play.api.libs.json.Json

object AssessmentEvaluationCommands {

  case class AssessmentPassmarkPreferencesAndScores(
    passmark: AssessmentCentrePassMarkSettingsResponse,
    preferencesWithQualification: PreferencesWithQualification,
    scores: CandidateScoresAndFeedback
  )

  case class OnlineTestEvaluationAndAssessmentCentreScores(
    onlineTestEvaluation: List[SchemeEvaluationResult],
    assessmentScores: AssessmentPassmarkPreferencesAndScores
  )

  object Implicits {
    import CandidateScoresCommands.Implicits.CandidateScoresAndFeedbackFormats
    import model.Commands.Implicits.assessmentCentrePassMarkSettingsResponseFormat
    import PassmarkPersistedObjects.Implicits.PersistedAssessmentCentrePassMarkSettingsFormat
    import PersistedObjects.Implicits.preferencesWithQualificationFormats
    implicit val AssessmentPassmarkPreferencesAndScoresFormats = Json.format[AssessmentPassmarkPreferencesAndScores]
  }
}
