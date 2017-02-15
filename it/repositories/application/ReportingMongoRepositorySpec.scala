/*
 * Copyright 2016 HM Revenue & Customs
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

package repositories.application

import factories.UUIDFactory
import model.ReportExchangeObjects.ApplicationForCandidateProgressReport
import model._
import reactivemongo.bson.BSONDocument
import repositories.CollectionNames
import services.GBTimeZoneService
import services.testdata.TestDataGeneratorService
import testkit.MongoRepositorySpec

import scala.language.postfixOps

class ReportingMongoRepositorySpec extends MongoRepositorySpec with UUIDFactory {

  val collectionName = CollectionNames.APPLICATION

  def repository = new ReportingMongoRepository(GBTimeZoneService)

  def testDataRepo = new TestDataMongoRepository()

  val testDataGeneratorService = TestDataGeneratorService

  val frameworkId = "FastTrack-2015"

  "Applications for Candidate Progress Report" must {
    "return a report with one application when there is only one application with the corresponding fields when" +
      "all fields are populated" in {
      val userId = UniqueIdentifier.randomUniqueIdentifier
      val appId = UniqueIdentifier.randomUniqueIdentifier

      testDataRepo.createApplicationWithAllFields(userId.toString(), appId.toString(), frameworkId).futureValue

      val result = repository.applicationsForCandidateProgressReport(frameworkId).futureValue

      result must not be empty
      result.head mustBe ApplicationForCandidateProgressReport(
        applicationId = Some(appId),
        userId = userId,
        progress = Some("assistance_details_completed"),
        schemes = List(Scheme.Commercial,Scheme.Business),
        locationIds = List("2643743", "2657613"),
        hasDisability = Some("Yes"),
        gis = Some(false),
        onlineAdjustments = Some(true),
        assessmentCentreAdjustments = Some(true),
        adjustments = Some(Adjustments(
          typeOfAdjustments=Some(List("onlineTestsTimeExtension", "onlineTestsOther", "assessmentCenterTimeExtension",
            "coloured paper", "braille test paper", "room alone", "rest breaks", "reader/assistant",
            "stand up and move around", "assessmentCenterOther")),
          adjustmentsConfirmed = Some(true),
          onlineTests = Some(AdjustmentDetail(
            extraTimeNeeded = Some(25),
            extraTimeNeededNumerical = Some(60),
            otherInfo = Some("other adjustments"))
          ),
          assessmentCenter = Some(AdjustmentDetail(
            extraTimeNeeded = Some(30),
            extraTimeNeededNumerical = None,
            otherInfo = Some("Other assessment centre adjustment"))
          )
        )),
        civilServant = Some(false),
        assessmentCentreIndicator = None
      )
    }
  }

  "Diversity report" must {
    "return a report with one application when there is only one application with all fields populated" in {
      val userId = UniqueIdentifier.randomUniqueIdentifier
      val appId = UniqueIdentifier.randomUniqueIdentifier

      val progressStatusDocument = BSONDocument(
        "progress-status" -> BSONDocument(
          "personal_details_completed" -> true,
          "schemes_preferences_completed" -> true,
          "scheme_locations_completed" -> true,
          "assistance_details_completed" -> true,
          "questionnaire" -> BSONDocument(
            "start_questionnaire" -> true,
            "diversity_questions_completed" -> true
          )
        )
      )

      testDataRepo.createApplicationWithAllFields(userId.toString(), appId.toString(), frameworkId,
        progressStatusBSON = progressStatusDocument).futureValue

      val result = repository.diversityReport(frameworkId).futureValue

      result must not be empty

      result.head mustBe ApplicationForCandidateProgressReport(
        applicationId = Some(appId),
        userId = userId,
        progress = Some("diversity_questions_completed"),
        schemes = List(Scheme.Commercial, Scheme.Business),
        locationIds = List("2643743", "2657613"),
        hasDisability = Some("Yes"),
        gis = Some(false),
        onlineAdjustments = Some(true),
        assessmentCentreAdjustments = Some(true),
        adjustments = Some(Adjustments(
          typeOfAdjustments=Some(List("onlineTestsTimeExtension", "onlineTestsOther", "assessmentCenterTimeExtension",
            "coloured paper", "braille test paper", "room alone", "rest breaks", "reader/assistant",
            "stand up and move around", "assessmentCenterOther")),
          adjustmentsConfirmed = Some(true),
          onlineTests = Some(AdjustmentDetail(
            extraTimeNeeded = Some(25),
            extraTimeNeededNumerical = Some(60),
            otherInfo = Some("other adjustments"))
          ),
          assessmentCenter = Some(AdjustmentDetail(
            extraTimeNeeded = Some(30),
            extraTimeNeededNumerical = None,
            otherInfo = Some("Other assessment centre adjustment"))
          )
        )),
        civilServant = Some(false),
        assessmentCentreIndicator = None
      )
    }
  }
}
