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

package services.testdata

import connectors.testdata.ExchangeObjects.DataGenerationResponse
import model.ApplicationStatuses
import model.Commands.AssessmentCentreAllocation
import model.testdata.GeneratorConfig
import repositories._
import repositories.application.OnlineTestRepository
import services.testdata.faker.DataFaker.Random

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object AllocationStatusGenerator extends AllocationStatusGenerator {
  override val previousStatusGenerator = AwaitingAllocationNotifiedStatusGenerator
  override val otRepository = onlineTestRepository
  override val aaRepository = assessmentCentreAllocationRepository

  val SlotFindingLockObj = new Object()
}

trait AllocationStatusGenerator extends ConstructiveGenerator {
  val otRepository: OnlineTestRepository
  val aaRepository: AssessmentCentreAllocationRepository

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier): Future[DataGenerationResponse] = {

    def getApplicationAssessment(candidate: DataGenerationResponse): Future[AssessmentCentreAllocation] = {
      for {
        availableAssessment <- Random.availableAssessmentVenueAndDate
      } yield {
        AssessmentCentreAllocation(
          candidate.applicationId.get,
          availableAssessment.venue.venueName,
          availableAssessment.date,
          availableAssessment.session,
          availableAssessment.slotNumber,
          confirmed = generatorConfig.confirmedAllocation
        )
      }
    }

    val newStatus = if (generatorConfig.confirmedAllocation) {
      ApplicationStatuses.AllocationConfirmed
    } else {
      ApplicationStatuses.AllocationUnconfirmed
    }

    AllocationStatusGenerator.SlotFindingLockObj.synchronized {
      for {
        candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
        randomAssessment <- getApplicationAssessment(candidateInPreviousStatus)
        _ <- aaRepository.create(List(randomAssessment))
        _ <- otRepository.saveCandidateAllocationStatus(candidateInPreviousStatus.applicationId.get, newStatus, None)
      } yield {
        candidateInPreviousStatus.copy(
          applicationStatus = newStatus,
          applicationAssessment = Some(randomAssessment)
        )
      }
    }
  }
}
