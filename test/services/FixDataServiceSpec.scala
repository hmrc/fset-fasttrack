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

package services

import connectors.PassMarkExchangeObjects.OnlineTestPassmarkSettings
import model.{ ApplicationStatuses, EmptyRequestHeader, Scheme }
import model.persisted.SchemeEvaluationResult
import org.joda.time.DateTime
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.{ OneAppPerSuite, PlaySpec }
import repositories.OnlineTestPassMarkSettingsRepository
import repositories.application.{ GeneralApplicationRepository, OnlineTestRepository }
import testkit.MockitoSugar
import org.mockito.Matchers._
import org.mockito.Mockito._
import play.api.mvc.RequestHeader
import uk.gov.hmrc.play.http.HeaderCarrier
import org.mockito.Matchers.{ eq => eqTo, _ }
import scala.concurrent.Future

class FixDataServiceSpec extends PlaySpec with MockitoSugar with ScalaFutures {

  "FixDataServiceSpec" must {

    "promote a candidate to awaiting assessment centre allocation" in new TestFixture {

      when(mockPassMarkSettingsRepo.tryGetLatestVersion()).thenReturn(
        Future.successful(Some(OnlineTestPassmarkSettings(Nil, "version", DateTime.now, "user")))
      )
      when(mockAppRepo.getSchemes(any[String])).thenReturn(Future.successful(
        List(Scheme.Finance, Scheme.Business, Scheme.DigitalAndTechnology)
      ))
      when(mockOnlineTestRepo.savePassMarkScore(any[String], any[String], any[List[SchemeEvaluationResult]],
        any[Option[ApplicationStatuses.EnumVal]])
      ).thenReturn(Future.successful(()))

      val actual = service.promoteToAssessmentCentre("appId").futureValue

      verify(mockAuditService).logEvent(any[String], any[Map[String, String]])(any[HeaderCarrier], any[RequestHeader])

      actual mustBe (())
    }

    "throw an exception if pass marks have not been set" in new TestFixture {
      when(mockPassMarkSettingsRepo.tryGetLatestVersion()).thenReturn(
        Future.successful(None)
      )
      when(mockAppRepo.getSchemes(any[String])).thenReturn(Future.successful(
        List(Scheme.Finance, Scheme.Business, Scheme.DigitalAndTechnology)
      ))

      val actual = service.promoteToAssessmentCentre("appId").failed.futureValue
      actual mustBe an[IllegalStateException]
      verify(mockAuditService, times(0)).logEvent(any[String], any[Map[String, String]])(any[HeaderCarrier], any[RequestHeader])
    }

  }


  trait TestFixture {
    implicit val hc = HeaderCarrier()
    implicit val rh = EmptyRequestHeader
    val mockAppRepo = mock[GeneralApplicationRepository]
    val mockPassMarkSettingsRepo = mock[OnlineTestPassMarkSettingsRepository]
    val mockOnlineTestRepo = mock[OnlineTestRepository]
    val mockAuditService = mock[AuditService]

    val service = new FixDataService {
      val appRepo = mockAppRepo
      val passmarkSettingsRepo = mockPassMarkSettingsRepo
      val onlineTestRepo = mockOnlineTestRepo
      val auditService = mockAuditService
    }
  }
}
