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

package model.persisted

import model.ProgressStatuses
import model.Scheme.Scheme
import play.api.libs.json.Json
import reactivemongo.bson.Macros

case class ApplicationForDiversityReport(applicationId: String,
                                         userId: String,
                                         progress: Option[ProgressStatuses.EnumVal],
                                         schemes: List[Scheme],
                                         schemeLocationIds: List[String],
                                         disability: Option[String],
                                         gis: Option[Boolean],
                                         onlineAdjustments: Option[String],
                                         assessmentCentreAdjustments: Option[String])

object ApplicationForDiversityReport {
  implicit val applicationForDiversityReportHandler = Macros.handler[ApplicationForDiversityReport]
  implicit val applicationForDiversityReportFormat = Json.format[ApplicationForDiversityReport]
}