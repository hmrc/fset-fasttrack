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

package services.allocation

import connectors.{ CSREmailClient, EmailClient }
import model.Commands.AssessmentCentreAllocation
import model.PersistedObjects.{ AllocatedCandidate, ContactDetails }
import org.joda.time.DateTime
import play.api.Logger
import repositories._
import repositories.application.{ CandidateAllocationMongoRepository, CandidateAllocationRepository }
import services.AuditService

import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.http.HeaderCarrier

object CandidateAllocationService extends CandidateAllocationService {
  val caRepository: CandidateAllocationMongoRepository = candidateAllocationMongoRepository
  val aaRepository: AssessmentCentreAllocationMongoRepository = assessmentCentreAllocationRepository
  val cdRepository: ContactDetailsMongoRepository = contactDetailsRepository
  val emailClient: CSREmailClient.type = CSREmailClient
  val auditService: AuditService.type = AuditService
}

trait CandidateAllocationService {
  val caRepository: CandidateAllocationRepository
  val aaRepository: AssessmentCentreAllocationRepository
  val cdRepository: ContactDetailsRepository
  val emailClient: EmailClient
  val auditService: AuditService

  implicit def headerCarrier: HeaderCarrier = HeaderCarrier()
  implicit val ec: ExecutionContext = ExecutionContext.global

  private val ReminderEmailDaysBeforeExpiration = 3

  def nextUnconfirmedCandidateForSendingReminder: Future[Option[AllocatedCandidate]] =
    caRepository.nextUnconfirmedCandidateToSendReminder(ReminderEmailDaysBeforeExpiration)

  def sendEmailConfirmationReminder(candidate: AllocatedCandidate): Future[Unit] = {
    for {
      assessment <- aaRepository.findOne(candidate.applicationId)
      contactDetails <- cdRepository.find(candidate.candidateDetails.userId)
      _ <- sendEmail(candidate, contactDetails, assessment)
      _ <- caRepository.saveAllocationReminderSentDate(candidate.applicationId, DateTime.now())
    } yield {
    }
  }

  private def sendEmail(candidate: AllocatedCandidate, contactDetails: ContactDetails,
    assessment: AssessmentCentreAllocation): Future[Unit] = {
    emailClient.sendReminderToConfirmAttendance(contactDetails.email, candidate.candidateDetails.preferredName,
      assessment.assessmentDateTime, candidate.expireDate) map { _ =>
      audit("AllocationReminderEmailSent", candidate.candidateDetails.userId, contactDetails.email)
    }
  }

  private def audit(event: String, userId: String, email: String): Unit = {
    Logger.info(s"$event for user $userId")
    auditService.logEventNoRequest(event, Map("userId" -> userId, "email" -> email))
  }
}
