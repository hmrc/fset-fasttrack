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

import connectors.ExchangeObjects.ReportNorm
import connectors.PassMarkExchangeObjects.Settings
import model.PersistedObjects.CandidateTestReport
import model.Scheme.Scheme
import org.joda.time.DateTime
import play.api.libs.json.Json

object OnlineTestCommands {
  case class OnlineTestProfile(cubiksUserId: Int, token: String, onlineTestUrl: String,
    invitationDate: DateTime, expirationDate: DateTime, participantScheduleId: Int)

  case class OnlineTestApplication(applicationId: String, applicationStatus: ApplicationStatuses.EnumVal, userId: String,
    guaranteedInterview: Boolean, needsAdjustments: Boolean, preferredName: String, adjustmentDetail: Option[AdjustmentDetail])

  case class OnlineTestApplicationWithCubiksUser(applicationId: String, userId: String, cubiksUserId: Int)
  case class OnlineTestApplicationForReportRetrieving(userId: Int, locale: String, reportId: Int, norms: List[ReportNorm])
  case class OnlineTestReportAvailability(reportId: Int, available: Boolean)
  case class OnlineTestReport(xml: Option[String])

  case class CandidateEvaluationData(passmarkSettings: Settings, schemes: List[Scheme], scores: CandidateTestReport)

  case class TimeAdjustmentsOnlineTestApplication(verbalTimeAdjustmentPercentage: Int, numericalTimeAdjustmentPercentage: Int)
  case class TestResult(status: String, norm: String,
    tScore: Option[Double], percentile: Option[Double], raw: Option[Double], sten: Option[Double])

  object Implicits {

    implicit val TimeAdjustmentsOnlineTestApplicationFormats = Json.format[TimeAdjustmentsOnlineTestApplication]
    implicit val ApplicationForOnlineTestingFormats = Json.format[OnlineTestApplication]
    implicit val OnlineTestReportNormFormats = Json.format[ReportNorm]
    implicit val OnlineTestApplicationForReportRetrievingFormats = Json.format[OnlineTestApplicationForReportRetrieving]
    implicit val OnlineTestApplicationUserFormats = Json.format[OnlineTestApplicationWithCubiksUser]
    implicit val OnlineTestProfileFormats = Json.format[OnlineTestProfile]
    implicit val OnlineTestReportIdMRAFormats = Json.format[OnlineTestReportAvailability]
    implicit val testFormat = Json.format[TestResult]
  }
}
