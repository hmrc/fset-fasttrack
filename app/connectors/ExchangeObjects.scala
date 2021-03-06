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

package connectors

import java.util.UUID

import org.joda.time.{ DateTime, LocalDate }
import play.api.libs.json.{ Format, Json }

object ExchangeObjects {

  val frameworkId = "FastTrack-2015"

  case class SendEmailRequest(
    to: List[String],
    templateId: String,
    parameters: Map[String, String],
    force: Boolean = false,
    eventUrl: Option[String] = None,
    onSendUrl: Option[String] = None,
    auditData: Map[String, String] = Map.empty
  )

  object SendEmailRequest {
    implicit val format: Format[SendEmailRequest] = Json.format[SendEmailRequest]
  }

  case class Candidate(firstName: String, lastName: String, preferredName: Option[String], email: String, userId: String)
  case class AuthProviderUserDetails(firstName: String, lastName: String, email: Option[String], preferredName: Option[String],
    role: Option[String], disabled: Option[Boolean])

  // Cubiks Gateway Requests
  case class RegisterApplicant(firstName: String, lastName: String, email: String)
  case class InviteApplicant(scheduleID: Int, userId: Int, scheduleCompletionURL: String, resultsURL: Option[String] = None,
    timeAdjustments: List[TimeAdjustments] = Nil)
  case class TimeAdjustments(assessmentId: Int, sectionId: Int, absoluteTime: Int)
  case class ReportNorm(assessmentId: Int, normId: Int)

  // Cubiks Gateway Response
  case class Registration(userId: Int)
  case class Invitation(userId: Int, email: String, accessCode: String, logonUrl: String, authenticateUrl: String, participantScheduleId: Int)

  case class AllocationDetails(location: String, venueDescription: String, attendanceDateTime: DateTime, expirationDate: Option[LocalDate])

  case class AddUserRequest(email: String, password: String, firstName: String, lastName: String, role: String, service: String)
  case class UserResponse(firstName: String, lastName: String, preferredName: Option[String],
    isActive: Boolean, userId: UUID, email: String, lockStatus: String, role: String)
  case class ActivateEmailRequest(service: String, email: String, token: String)

  // Find by first/last
  case class FindByFirstNameRequest(roles: List[String], firstName: String)
  case class FindByLastNameRequest(roles: List[String], lastName: String)
  case class FindByFirstNameLastNameRequest(roles: List[String], firstName: String, lastName: String)
  case class FindByUserIdRequest(userId: String)
  case class FindByUserIdsRequest(userIds: List[String])
  case class UpdateDetailsRequest(firstName: String, lastName: String,
    email: Option[String], preferredName: Option[String], role: Option[String], disabled: Option[Boolean])

  object Implicits {
    implicit val authProviderUserDetailsFormat = Json.format[AuthProviderUserDetails]
    implicit val userEmailFormat = Json.format[SendEmailRequest]
    implicit val candidateFormat = Json.format[Candidate]
    implicit val registrationFormat = Json.format[Registration]
    implicit val registerApplicantFormat = Json.format[RegisterApplicant]
    implicit val timeAdjustmentsFormat = Json.format[TimeAdjustments]
    implicit val inviteApplicantFormat = Json.format[InviteApplicant]
    implicit val invitationFormat = Json.format[Invitation]
    implicit val reportNormFormat = Json.format[ReportNorm]
    implicit val allocationDetailsFormat = Json.format[AllocationDetails]

    implicit val addUserRequestFormat = Json.format[AddUserRequest]
    implicit val userResponseFormat = Json.format[UserResponse]
    implicit val activateEmailRequestFormat = Json.format[ActivateEmailRequest]

    implicit val findByFirstNameRequestFormat = Json.format[FindByFirstNameRequest]
    implicit val findByLastNameRequestFormat = Json.format[FindByLastNameRequest]
    implicit val findByFirstNameLastNameRequestFormat = Json.format[FindByFirstNameLastNameRequest]
    implicit val findByUserIdRequestFormat = Json.format[FindByUserIdRequest]
    implicit val findByUserIdsRequestFormat = Json.format[FindByUserIdsRequest]
    implicit val updateDetailsRequestFormat = Json.format[UpdateDetailsRequest]

  }
}
