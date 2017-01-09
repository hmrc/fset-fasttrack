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

package model.exchange

import play.api.libs.json.Json

import scala.language.implicitConversions

case class AssistanceDetails(hasDisability: String,
                             hasDisabilityDescription: Option[String],
                             guaranteedInterview: Option[Boolean],
                             needsSupportForOnlineAssessment: Boolean,
                             needsSupportForOnlineAssessmentDescription: Option[String],
                             needsSupportAtVenue: Boolean,
                             needsSupportAtVenueDescription: Option[String],
                             // TODO: Change adjustments
                             confirmedAdjustments: Option[Boolean],
                             numericalTimeAdjustmentPercentage: Option[Int],
                             verbalTimeAdjustmentPercentage: Option[Int])

object AssistanceDetails {
  implicit val assistanceDetailsFormat = Json.format[AssistanceDetails]

  implicit def fromPersisted(assistanceDetails: model.persisted.AssistanceDetails): AssistanceDetails = {
    AssistanceDetails(
      hasDisability = assistanceDetails.hasDisability,
      hasDisabilityDescription = assistanceDetails.hasDisabilityDescription,
      guaranteedInterview = assistanceDetails.guaranteedInterview,
      needsSupportForOnlineAssessment = assistanceDetails.needsSupportForOnlineAssessment,
      needsSupportForOnlineAssessmentDescription = assistanceDetails.needsSupportForOnlineAssessmentDescription,
      needsSupportAtVenue = assistanceDetails.needsSupportAtVenue,
      needsSupportAtVenueDescription = assistanceDetails.needsSupportAtVenueDescription,
      confirmedAdjustments = assistanceDetails.confirmedAdjustments,
      numericalTimeAdjustmentPercentage = assistanceDetails.numericalTimeAdjustmentPercentage,
      verbalTimeAdjustmentPercentage = assistanceDetails.verbalTimeAdjustmentPercentage
    )
  }
}