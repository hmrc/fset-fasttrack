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

import model.PersistedObjects.{PersistedAnswer, PersistedQuestion}
import common.Constants.{ Yes, No }
import connectors.testdata.ExchangeObjects.DataGenerationResponse
import model.Commands.{ Address }
import model.PersistedObjects.{ ContactDetails, PersistedAnswer, PersistedQuestion, PersonalDetails }
import model.{ Alternatives, LocationPreference, Preferences }
import org.joda.time.LocalDate
import repositories._
import repositories.application.{AssistanceDetailsRepository, GeneralApplicationRepository}
import services.testdata.faker.DataFaker._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

object InProgressQuestionnaireStartStatusGenerator extends InProgressQuestionnaireStartStatusGenerator {
  override val previousStatusGenerator = InProgressAssistanceDetailsStatusGenerator
  override val appRepository = applicationRepository
  override val qRepository = questionnaireRepository
}

trait InProgressQuestionnaireStartStatusGenerator extends ConstructiveGenerator {
  val appRepository: GeneralApplicationRepository
  val qRepository: QuestionnaireRepository

  // scalastyle:off method.length
  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier) = {
    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- appRepository.updateQuestionnaireStatus(candidateInPreviousStatus.applicationId.get, "start_questionnaire")
    } yield {
      candidateInPreviousStatus
    }
  }
  // scalastyle:on method.length
}
