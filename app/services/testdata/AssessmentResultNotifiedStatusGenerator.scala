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
import model.testdata.GeneratorConfig
import repositories._
import repositories.application.GeneralApplicationRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object AssessmentCentrePassedNotifiedStatusGenerator extends AssessmentResultNotifiedStatusGenerator {
  override val previousStatusGenerator = AssessmentCentrePassedStatusGenerator
  override val aRepository = applicationRepository
  override val status = ApplicationStatuses.AssessmentCentrePassedNotified
}

object AssessmentCentreFailedNotifiedStatusGenerator extends AssessmentResultNotifiedStatusGenerator {
  override val previousStatusGenerator = AssessmentCentreFailedStatusGenerator
  override val aRepository = applicationRepository
  override val status = ApplicationStatuses.AssessmentCentreFailedNotified
}

trait AssessmentResultNotifiedStatusGenerator extends ConstructiveGenerator {
  val aRepository: GeneralApplicationRepository
  val status: ApplicationStatuses.EnumVal

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier): Future[DataGenerationResponse] = {
    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- aRepository.updateStatus(candidateInPreviousStatus.applicationId.get, status)
    } yield {
      candidateInPreviousStatus.copy(applicationStatus = status)
    }
  }
}
