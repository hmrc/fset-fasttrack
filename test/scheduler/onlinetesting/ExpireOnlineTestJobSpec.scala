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

package scheduler.onlinetesting

import org.mockito.Mockito._
import org.scalatestplus.play.OneAppPerSuite
import play.test.WithApplication
import services.onlinetesting.OnlineTestExpiryService
import testkit.{ ShortTimeout, UnitWithAppSpec }

import scala.concurrent.{ ExecutionContext, Future }

class ExpireOnlineTestJobSpec extends UnitWithAppSpec with ShortTimeout {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val serviceMock = mock[OnlineTestExpiryService]

  "send invitation job" should {

    "complete successfully when service completes successfully" in {
      object TestableExpireOnlineTestJob extends ExpireOnlineTestJob {
        val service = serviceMock
      }
      when(serviceMock.processNextExpiredTest()).thenReturn(Future.successful(()))
      TestableExpireOnlineTestJob.tryExecute().futureValue mustBe unit
    }

    "fail when the service fails" in {
      object TestableExpireOnlineTestJob extends ExpireOnlineTestJob {
        val service = serviceMock
      }
      when(serviceMock.processNextExpiredTest()).thenReturn(Future.failed(new Exception))
      TestableExpireOnlineTestJob.tryExecute().failed.futureValue mustBe an[Exception]
    }
  }
}
