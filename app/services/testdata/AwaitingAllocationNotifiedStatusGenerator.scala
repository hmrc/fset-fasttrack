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

package services.testdata

import model.ApplicationStatuses
import model.ApplicationStatuses.EnumVal
import model.testdata.GeneratorConfig
import repositories._
import repositories.application.OnlineTestRepository
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

object AwaitingAllocationNotifiedStatusGenerator extends StatusUpdateGenerator {
  override val previousStatusGenerator = AwaitingAllocationStatusGenerator
  override val otRepository = onlineTestRepository
  override val newApplicationStatus: EnumVal = ApplicationStatuses.AwaitingAllocationNotified
}

trait StatusUpdateGenerator extends ConstructiveGenerator {
  val otRepository: OnlineTestRepository
  val newApplicationStatus: ApplicationStatuses.EnumVal

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier) = {
    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- otRepository.updateStatus(candidateInPreviousStatus.userId, newApplicationStatus)
    } yield {
      candidateInPreviousStatus.copy(applicationStatus = newApplicationStatus)
    }
  }
}