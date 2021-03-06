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

package controllers

import java.io.UnsupportedEncodingException
import java.net.URLEncoder

import config._
import connectors.EmailClient
import model.AssessmentScheduleCommands.Implicits._
import model.AssessmentScheduleCommands.{ ApplicationForAssessmentAllocation, ApplicationForAssessmentAllocationResult }
import model.Commands._
import model.Exceptions.NotFoundException
import model.PersistedObjects.ContactDetails
import model.commands.OnlineTestProgressResponse
import model.persisted.PersonalDetails
import org.joda.time.{ DateTime, LocalDate }
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsArray, JsString, JsValue, Json }
import play.api.mvc.Results
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.AssessmentCentreLocation._
import repositories.application.{ GeneralApplicationRepository, OnlineTestRepository, PersonalDetailsRepository }
import repositories.{ AssessmentCentreLocation, _ }
import services.application.ApplicationService
import services.applicationassessment.{ AssessmentCentreScoresService, AssessmentCentreService }
import testkit.MockitoSugar

import scala.concurrent.{ ExecutionContext, Future }

class AssessmentScheduleControllerSpec extends PlaySpec with Results
  with MockitoSugar with ScalaFutures {
  implicit val ec: ExecutionContext = ExecutionContext.global
  val mockAssessmentCentreRepository = mock[AssessmentCentreLocationRepository]

  "Get Assessment Schedule" should {
    "return a valid schedule" in new TestFixture {
      val result = assessmentScheduleControllerWithSampleSchedule.getAssessmentSchedule()(FakeRequest())
      val jsonResponse = contentAsJson(result)

      status(result) must be(200)

      val expectedJson =
        s"""
        {
           |   "locations":[
           |      {
           |         "name":"Narnia",
           |         "venues":[
           |            {
           |               "name":"Test Venue 1",
           |               "usedCapacityDates":[
           |                  {
           |                     "date":"2015-04-25",
           |                     "amUsedCapacity":{
           |                        "usedCapacity":2,
           |                        "confirmedAttendees":0,
           |                        "minViableAttendees":10,
           |                        "preferredAttendeeMargin":4
           |                     },
           |                     "pmUsedCapacity":{
           |                        "usedCapacity":1,
           |                        "confirmedAttendees":0,
           |                        "minViableAttendees":10,
           |                        "preferredAttendeeMargin":4
           |                     }
           |                  }
           |               ]
           |            },
           |            {
           |               "name":"Test Venue 2",
           |               "usedCapacityDates":[
           |                  {
           |                     "date":"2015-04-27",
           |                     "amUsedCapacity":{
           |                        "usedCapacity":0,
           |                        "confirmedAttendees":0,
           |                        "minViableAttendees":10,
           |                        "preferredAttendeeMargin":4
           |                     },
           |                     "pmUsedCapacity":{
           |                        "usedCapacity":0,
           |                        "confirmedAttendees":0,
           |                        "minViableAttendees":10,
           |                        "preferredAttendeeMargin":4
           |                     }
           |                  }
           |               ]
           |            }
           |         ]
           |      }
           |   ]
           |}
         """.stripMargin

      jsonResponse must equal(Json.parse(expectedJson))
    }
  }

  "get application for assessment allocation" should {
    "return a List of Application for Assessment allocation if there are relevant applications in the repo" in new TestFixture {

      when(mockApplicationRepository.findApplicationsForAssessmentAllocation(any[List[String]], any[Int], any[Int])).thenReturn(
        Future.successful(
          ApplicationForAssessmentAllocationResult(
            List(
              ApplicationForAssessmentAllocation(
                "firstName1", "lastName1", "userId1", "applicationId1", needsAdjustment = false, DateTime.now, 1988
              ),
              ApplicationForAssessmentAllocation(
                "firstName2", "lastName2", "userId2", "applicationId2", needsAdjustment = true, DateTime.now.minusDays(2), 1988
              )
            ),
            2
          )
        )
      )

      val result = controller.getApplicationForAssessmentAllocation("London", 0, 2)(FakeRequest())
      status(result) must be(200)

    }
    "return an empty List of Application for Assessment allocation if there are no relevant applications in the repo" in new TestFixture {
      when(mockApplicationRepository.findApplicationsForAssessmentAllocation(any[List[String]], any[Int], any[Int])).thenReturn(
        Future.successful(ApplicationForAssessmentAllocationResult(Nil, 0))
      )

      val response = assessmentScheduleControllerWithSampleSchedule.getApplicationForAssessmentAllocation("London", 0, 5)(FakeRequest())
      status(response) must be(200)

      val result = contentAsJson(response).as[ApplicationForAssessmentAllocationResult]
      result.result must have size 0
    }
  }

  "get a day's schedule for a venue" should {
    "show the candidate schedule for a venue when there is only one candidate in one session" in new TestFixture {
      val venue = "Copa (Cabana)"
      val date = "2015-04-25"

      val result = assessmentScheduleControllerWithOneVenueDateCandidate.getVenueDayCandidateSchedule(venue, date)(FakeRequest())
      val jsonResponse = contentAsJson(result)

      status(result) must be(200)

      // FYI: Nulls are expected "empty slot" padding.
      val expectedJson =
        s"""
           |{
           |    "amSession": [
           |            {
           |                "slotNumber": 1,
           |                "applicationId": "appid-1",
           |                "confirmed": true,
           |                "firstName": "Bob",
           |                "lastName": "Marley",
           |                "userId": "userid-1"
           |            },
           |            null
           |    ],
           |    "pmSession": [
           |            null,
           |            null,
           |            null
           |    ]
           |
           |}
         """.stripMargin

      jsonResponse must equal(Json.parse(expectedJson))
    }

    "show the candidate schedule for a venue when there are no candidates assigned to any sessions" in new TestFixture {
      val venue = "Copa (Cabana)"
      val date = "2015-04-26"

      val result = assessmentScheduleControllerWithNoVenueDateCandidates.getVenueDayCandidateSchedule(venue, date)(FakeRequest())
      val jsonResponse = contentAsJson(result)

      status(result) must be(200)

      // FYI: Nulls are expected "empty slot" padding.
      val expectedJson =
        s"""
           |{
           |    "amSession": [
           |            null,
           |            null,
           |            null
           |    ],
           |    "pmSession": [
           |            null,
           |            null,
           |            null,
           |            null,
           |            null,
           |            null
           |     ]
           |
           |}
         """.stripMargin

      jsonResponse must equal(Json.parse(expectedJson))
    }

    "throw an illegal argument exception if a bad date string is passed" in new TestFixture {
      intercept[IllegalArgumentException] {
        val venue = "Copa (Cabana)"
        val date = "INVALID_DATE"

        val result = assessmentScheduleControllerWithSampleSchedule.getVenueDayCandidateSchedule(venue, date)(FakeRequest())

        status(result) must be(OK)
      }
    }

    "throw an unsupported encoding exception if a venue string we cannot understand is passed" in new TestFixture {
      intercept[UnsupportedEncodingException] {
        val venue = URLEncoder.encode("a����", "latin-1")
        val date = "2015-06-01"

        val result = assessmentScheduleControllerWithSampleSchedule.getVenueDayCandidateSchedule(venue, date)(FakeRequest())

        status(result) must be(OK)
      }
    }
  }

  "get a day's schedule for a venue with details" should {
    "show the candidate schedule for a venue when there is only one candidate in one session" in new TestFixture {
      val venue = "Copa (Cabana)"
      val date = "2015-04-25"

      val result = assessmentScheduleControllerWithOneVenueDateCandidate.getVenueDayCandidateScheduleWithDetails(venue, date)(FakeRequest())
      val jsonResponse = contentAsJson(result)

      status(result) must be(200)

      val expectedJson =
        s"""
           |[{
           |    "slotNumber": 1,
           |    "firstName": "Bob",
           |    "lastName": "Marley",
           |    "preferredName": "preferredName1",
           |    "email": "email1@mailinator.com",
           |    "phone": "11111111",
           |    "venue": "Copa (Cabana)",
           |    "session": "AM",
           |    "date": "2015-04-25",
           |    "status": "Confirmed"
           |}]
         """.stripMargin

      jsonResponse must equal(Json.parse(expectedJson))
    }

    "show the candidate schedule for a venue when there are several candidates in AM and PM sessions" in new TestFixture {
      val venue = "Copa (Cabana)"
      val date = "2015-04-25"

      val result = assessmentScheduleControllerWithOneVenueOneDateOneCandidateInPMOneInAMSession.getVenueDayCandidateScheduleWithDetails(
        venue,
        date
      )(FakeRequest())
      val jsonResponse = contentAsJson(result)

      status(result) must be(200)

      val expectedJson =
        s"""
           |[{
           |    "slotNumber": 1,
           |    "firstName": "Bob",
           |    "lastName": "Marley",
           |    "preferredName": "preferredName1",
           |    "email": "email1@mailinator.com",
           |    "phone": "11111111",
           |    "venue": "Copa (Cabana)",
           |    "session": "AM",
           |    "date": "2015-04-25",
           |    "status": "Confirmed"
           |},
           |{
           |    "slotNumber": 1,
           |    "firstName": "Michael",
           |    "lastName": "Jackson",
           |    "preferredName": "preferredName2",
           |    "email": "email2@mailinator.com",
           |    "phone": "22222222",
           |    "venue": "Copa (Cabana)",
           |    "session": "PM",
           |    "date": "2015-04-25",
           |    "status": "Confirmed"
           |}
           |]
         """.stripMargin

      jsonResponse must equal(Json.parse(expectedJson))
    }

    "show the candidate schedule for a venue when there are no candidates" in new TestFixture {
      val venue = "Copa (Cabana)"
      val date = "2015-07-30"

      val result = assessmentScheduleControllerWithNoVenueDateCandidates.getVenueDayCandidateScheduleWithDetails(venue, date)(FakeRequest())
      val jsonResponse = contentAsJson(result)

      status(result) must be(200)

      // FYI: Nulls are expected "empty slot" padding.
      val expectedJson = "[]"

      jsonResponse must equal(Json.parse(expectedJson))
    }

    "show the candidate schedule for a venue when there is one non withdrawn and one withdrawn candidate" in new TestFixture {
      val venue = "Copa (Cabana)"
      val date = "2015-04-25"

      val result = assessmentScheduleControllerWithOneVenueOneDateOneNormalCandidateAndWithdrawnCandidate.
        getVenueDayCandidateScheduleWithDetails(venue, date)(FakeRequest())
      val jsonResponse = contentAsJson(result)

      status(result) must be(200)

      val expectedJson =
        s"""
           |[
           |{
           |    "slotNumber": 1,
           |    "firstName": "Michael",
           |    "lastName": "Jackson",
           |    "preferredName": "preferredName2",
           |    "email": "email2@mailinator.com",
           |    "phone": "22222222",
           |    "venue": "Copa (Cabana)",
           |    "session": "PM",
           |    "date": "2015-04-25",
           |    "status": "Confirmed"
           |}
           |]
         """.stripMargin

      jsonResponse must equal(Json.parse(expectedJson))
    }
  }

  "delete an Application Assessment" should {
    "return a deletion success response when an application id exists" in new TestFixture {
      val result = assessmentScheduleControllerWithValidDeletes.deleteApplicationAssessment("applicationid-1")(FakeRequest())

      status(result) must be(OK)
    }
    "return a not found response when an application id does not exist" in new TestFixture {
      val result = assessmentScheduleControllerWithNoDeletes.deleteApplicationAssessment("non-existent-app")(FakeRequest())

      status(result) must be(NOT_FOUND)
    }
    "return unauthorised if the application status is assessment scores accepted" in new TestFixture {
      val result = assessmentScheduleControllerWithUnauthDeletes.deleteApplicationAssessment("someid-1")(FakeRequest())

      status(result) must be(CONFLICT)
    }
  }

  "getting an allocation status" should {
    "return location name, venue description, morning date of assessment and expiry" in new TestFixture {
      val result = assessmentScheduleControllerWithSampleSchedule.allocationStatus("1")(FakeRequest())
      val jsonResponse = contentAsJson(result)

      status(result) must be(OK)

      val jsonTime = new DateTime(2015, 4, 25, 8, 30).getMillis
      val expectedJson =
        s"""
          | {
          |   "location": "Narnia",
          |   "venueDescription": "Test Street 1",
          |   "attendanceDateTime": $jsonTime,
          |   "expirationDate": "2015-04-23"
          | }
        """.stripMargin

      jsonResponse must equal(Json.parse(expectedJson))
    }

    "return location name, venue description, afternoon date of assessment and expiry" in new TestFixture {
      val result = assessmentScheduleControllerWithSampleSchedule.allocationStatus("2")(FakeRequest())
      val jsonResponse = contentAsJson(result)

      status(result) must be(OK)

      val jsonTime = new DateTime(2015, 4, 25, 12, 30).getMillis
      val expectedJson =
        s"""
           | {
           |   "location": "Narnia",
           |   "venueDescription": "Test Street 2",
           |   "attendanceDateTime": $jsonTime,
           |   "expirationDate": "2015-04-23"
           | }
        """.stripMargin

      jsonResponse must equal(Json.parse(expectedJson))
    }

    "return NotFound when an invalid application is passed" in new TestFixture {
      val result = assessmentScheduleControllerWithSampleSchedule.allocationStatus("Non Existent Application")(FakeRequest())

      status(result) must be(NOT_FOUND)
    }
  }

  "converting a location to an assessment centre location" should {
    "return a location when the assessment centre location is valid" in new TestFixture {

      val result = assessmentScheduleControllerWithNoVenueDateCandidates.locationToAssessmentCentreLocation("London")(FakeRequest())
      val jsonResponse = contentAsJson(result)

      status(result) must be(OK)

      val expectedJson = """{"name":"London"}"""

      jsonResponse must equal(Json.parse(expectedJson))
    }
  }

  "get assessment centre capacities" should {
    "do not return past dates" in new TestFixture {
      val locations = List(AssessmentCentreLocation("London", List(AssessmentCentreVenue("London venue", "",
        List(
          AssessmentCentreVenueCapacityDate(LocalDate.now.minusDays(1), 1, 1, 10, 4, 10, 4),
          AssessmentCentreVenueCapacityDate(LocalDate.now, 2, 2, 10, 4, 10, 4),
          AssessmentCentreVenueCapacityDate(LocalDate.now.minusDays(1), 3, 3, 10, 4, 10, 4),
          AssessmentCentreVenueCapacityDate(LocalDate.now.plusDays(1), 4, 4, 10, 4, 10, 4),
          AssessmentCentreVenueCapacityDate(LocalDate.now.plusDays(2), 5, 5, 10, 4, 10, 4)
        )))))
      when(mockAssessmentCentreRepository.assessmentCentreCapacities).thenReturn(Future.successful(locations))

      val result = controller.getAssessmentCentreCapacities("London venue")(FakeRequest())
      status(result) must be(OK)

      val venue = contentAsJson(result).as[AssessmentCentreVenue]
      venue.capacityDates.map(_.amCapacity) must contain only (2, 4, 5)
    }

    "return empty list of slots if all dates are in the past" in new TestFixture {
      val locations = List(AssessmentCentreLocation("London", List(AssessmentCentreVenue("London venue", "",
        List(
          AssessmentCentreVenueCapacityDate(LocalDate.now.minusDays(1), 1, 1, 10, 4, 10, 4),
          AssessmentCentreVenueCapacityDate(LocalDate.now.minusDays(2), 2, 2, 10, 4, 10, 4),
          AssessmentCentreVenueCapacityDate(LocalDate.now.minusDays(3), 2, 2, 10, 4, 10, 4)
        )))))
      when(mockAssessmentCentreRepository.assessmentCentreCapacities).thenReturn(Future.successful(locations))

      val result = controller.getAssessmentCentreCapacities("London venue")(FakeRequest())
      status(result) must be(OK)

      val venue = contentAsJson(result).as[AssessmentCentreVenue]
      venue.capacityDates must be(empty)

    }

    "return a list of assessment centres " in new TestFixture {
      val locations = List(AssessmentCentreLocation(
        "London",
        List(
          AssessmentCentreVenue("London venue", "", List()),
          AssessmentCentreVenue("London venue 2", "", List()),
          AssessmentCentreVenue("London venue 3", "", List())
        )
      ))
      when(mockAssessmentCentreRepository.assessmentCentreCapacities).thenReturn(Future.successful(locations))

      val result = controller.assessmentCentres(FakeRequest())

      status(result) must be(OK)

      val centres: JsValue = contentAsJson(result)

      centres.as[JsArray].value.size must be(3)
      centres.as[JsArray].value.map(_.as[JsString].value) must be(Seq("London venue", "London venue 2", "London venue 3"))

    }
  }

  trait TestFixture extends TestFixtureBase {

    val mockAssessmentCentreScoresService = mock[AssessmentCentreScoresService]
    val mockApplicationAssessmentRepository = mock[AssessmentCentreAllocationRepository]
    val mockApplicationRepository = mock[GeneralApplicationRepository]
    val mockOnlineTestRepository = mock[OnlineTestRepository]
    val mockPersonalDetailsRepository = mock[PersonalDetailsRepository]
    val mockContactDetailsRepository = mock[ContactDetailsRepository]
    val mockEmailClient = mock[EmailClient]
    val mockApplicationAssessmentService = mock[AssessmentCentreService]
    val mockAppService = mock[ApplicationService]

    val controller = new AssessmentScheduleController {
      override val aaRepository = mockApplicationAssessmentRepository
      override val acRepository = mockAssessmentCentreRepository
      override val aRepository = mockApplicationRepository
      override val otRepository = mockOnlineTestRepository
      override val auditService = mockAuditService
      override val pdRepository = mockPersonalDetailsRepository
      override val cdRepository = mockContactDetailsRepository
      override val emailClient = mockEmailClient
      override val aaService = mockApplicationAssessmentService
      override val assessmentScoresService: AssessmentCentreScoresService = mockAssessmentCentreScoresService
      override val appService: ApplicationService = mockAppService
    }

    def makeAssessmentScheduleController(op: => Unit) = {
      op
      new AssessmentScheduleController {
        override val aaRepository = mockApplicationAssessmentRepository
        override val acRepository = mockAssessmentCentreRepository
        override val aRepository = mockApplicationRepository
        override val otRepository = mockOnlineTestRepository
        override val auditService = mockAuditService
        override val pdRepository = mockPersonalDetailsRepository
        override val cdRepository = mockContactDetailsRepository
        override val emailClient = mockEmailClient
        override val aaService = mockApplicationAssessmentService
        override val assessmentScoresService: AssessmentCentreScoresService = mockAssessmentCentreScoresService
        override val appService: ApplicationService = mockAppService
      }
    }

    lazy val assessmentScheduleControllerWithSampleSchedule = makeAssessmentScheduleController {
      applicationAssessmentRepoWithSomeAssessments
      assessmentCentreRepoWithSomeLocations
      applicationRepoWithSomeApplications
    }

    lazy val assessmentScheduleControllerWithOneVenueDateCandidate = makeAssessmentScheduleController {
      assessmentCentreRepoWithOneDate
      applicationRepositoryWithOneVenueDateCandidate
      applicationAssessmentRepoWithOneVenueDateCandidate
      contactDetailsRepository
      personalDetailsRepository
    }

    lazy val assessmentScheduleControllerWithOneVenueOneDateOneCandidateInPMOneInAMSession = makeAssessmentScheduleController {
      assessmentCentreRepoWithOneDate
      applicationRepositoryWithOneVenueOneDateOneCandidateInPMOneInAMSession
      applicationAssessmentRepoWithOneVenueOneDateOneCandidateInPMOneInAMSession
      contactDetailsRepository
      personalDetailsRepository
    }

    lazy val assessmentScheduleControllerWithOneVenueOneDateOneNormalCandidateAndWithdrawnCandidate = makeAssessmentScheduleController {
      assessmentCentreRepoWithOneDate
      applicationRepositoryWithOneVenueOneDateOneNormalCandidateAndWithdrawnCandidate
      applicationAssessmentRepoWithOneVenueOneDateOneCandidateInPMOneInAMSession
      contactDetailsRepository
      personalDetailsRepository
    }

    lazy val assessmentScheduleControllerWithNoVenueDateCandidates = makeAssessmentScheduleController {
      assessmentCentreRepoWithNoDates
      applicationRepositoryWithNoVenueDateCandidates
      applicationAssessmentRepoWithNoVenueDateCandidates
    }

    lazy val assessmentScheduleControllerWithValidDeletes = makeAssessmentScheduleController {

      applicationAssessmentRepositoryThatCanDelete
      onlineTestRepositoryThatCanRemoveAllocationStatus
    }

    lazy val assessmentScheduleControllerWithNoDeletes = makeAssessmentScheduleController {
      applicationAssessmentRepositoryThatCannotDelete
      onlineTestRepositoryThatCanRemoveAllocationStatus
    }

    lazy val assessmentScheduleControllerWithUnauthDeletes = makeAssessmentScheduleController {
      applicationAssessmentRepositoryThatCannotDeleteUnauthorised
      onlineTestRepositoryThatCanRemoveAllocationStatus
    }

    // scalastyle:off
    def applicationAssessmentRepoWithSomeAssessments = {

      when(mockApplicationAssessmentRepository.find(any())).thenReturn(Future.successful(None))

      when(mockApplicationAssessmentRepository.find(eqTo("1"))).thenReturn(Future.successful(
        Some(AssessmentCentreAllocation(
          "1",
          "Test Venue 1",
          DateTime.parse("2015-04-25").toLocalDate,
          "AM",
          1,
          confirmed = false
        ))
      ))

      when(mockApplicationAssessmentRepository.find(eqTo("2"))).thenReturn(Future.successful(
        Some(AssessmentCentreAllocation(
          "2",
          "Test Venue 2",
          DateTime.parse("2015-04-25").toLocalDate,
          "PM",
          1,
          confirmed = false
        ))
      ))

      when(mockApplicationAssessmentRepository.findAll).thenReturn(Future.successful(
        List(
          AssessmentCentreAllocation(
            "1",
            "Test Venue 1",
            DateTime.parse("2015-04-25").toLocalDate,
            "AM",
            1,
            confirmed = false
          ),
          AssessmentCentreAllocation(
            "2",
            "Test Venue 1",
            DateTime.parse("2015-04-25").toLocalDate,
            "AM",
            2,
            confirmed = false
          ),
          AssessmentCentreAllocation(
            "2",
            "Test Venue 1",
            DateTime.parse("2015-04-25").toLocalDate,
            "PM",
            1,
            confirmed = false
          )
        )
      ))
    }
    // scalastyle:on

    def assessmentCentreRepoWithSomeLocations = {
      when(mockAssessmentCentreRepository.assessmentCentreCapacities).thenReturn(Future.successful(
        List(
          AssessmentCentreLocation(
            "Narnia",
            List(
              AssessmentCentreVenue(
                "Test Venue 1",
                "Test Street 1",
                List(
                  AssessmentCentreVenueCapacityDate(
                    DateTime.parse("2015-04-25").toLocalDate,
                    2,
                    3,
                    10,
                    4,
                    10,
                    4
                  )
                )
              ),
              AssessmentCentreVenue(
                "Test Venue 2",
                "Test Street 2",
                List(
                  AssessmentCentreVenueCapacityDate(
                    DateTime.parse("2015-04-27").toLocalDate,
                    1,
                    6,
                    10,
                    4,
                    10,
                    4
                  )
                )
              )
            )
          )
        )
      ))
    }

    def applicationRepoWithSomeApplications = {
      when(mockApplicationRepository.findApplicationsForAssessmentAllocation(
        eqTo(List("Narnia")),
        eqTo(0), eqTo(5)
      )).thenReturn(Future.successful(
        ApplicationForAssessmentAllocationResult(
          List(
            ApplicationForAssessmentAllocation(
              "firstName1", "lastName1", "userId1", "applicationId1", needsAdjustment = false, DateTime.now, 1988
            ),
            ApplicationForAssessmentAllocation(
              "firstName2", "lastName2", "userId2", "applicationId2", needsAdjustment = true, DateTime.now.minusDays(2), 1988
            )
          ), 2
        )
      ))

      when(mockApplicationRepository.findApplicationsForAssessmentAllocation(
        eqTo(List("Narnia")),
        eqTo(0), eqTo(5)
      )).thenReturn(Future.successful(
        ApplicationForAssessmentAllocationResult(List.empty, 0)
      ))

      when(mockApplicationRepository.allocationExpireDateByApplicationId(any())).thenReturn(Future.successful(
        Some(LocalDate.parse("2015-04-23"))
      ))
    }

    def assessmentCentreRepoWithOneDate = {
      when(mockAssessmentCentreRepository.assessmentCentreCapacityDate(any(), any())).thenReturn(Future.successful(
        AssessmentCentreVenueCapacityDate(
          LocalDate.parse("2015-04-25"),
          2,
          3,
          10,
          4,
          10,
          4
        )
      ))
    }

    def applicationRepositoryWithOneVenueDateCandidate = {
      when(mockApplicationRepository.find(any[List[String]])).thenReturn(Future.successful(
        List(
          Candidate(
            "userid-1",
            Some("appid-1"),
            None,
            Some("Bob"),
            Some("Marley"),
            Some("Marley"),
            None,
            None,
            None
          )
        )
      ))
      when(mockApplicationRepository.findProgress(any())).thenReturn(Future.successful(
        ProgressResponse("appid-1", personalDetails = true, hasSchemeLocations = true, hasSchemes = true, assistanceDetails = true,
          review = false, QuestionnaireProgressResponse(), submitted = true, withdrawn = false,
          OnlineTestProgressResponse(invited = true, started = true, completed = true, expired = true, awaitingReevaluation = false,
            failed = false, failedNotified = false, awaitingAllocation = true, awaitingAllocationNotified = true, allocationConfirmed = false,
            allocationUnconfirmed = false),
          failedToAttend = false)
      ))
    }

    def applicationRepositoryWithOneVenueOneDateOneCandidateInPMOneInAMSession = {
      when(mockApplicationRepository.find(any[List[String]])).thenReturn(Future.successful(
        List(
          Candidate(
            "userid-1",
            Some("appid-1"),
            None,
            Some("Bob"),
            Some("Marley"),
            Some("Marley"),
            None,
            None,
            None
          ),
          Candidate(
            "userid-2",
            Some("appid-2"),
            None,
            Some("Michael"),
            Some("Jackson"),
            Some("Jackson"),
            None,
            None,
            None
          )
        )
      ))
      when(mockApplicationRepository.findProgress(eqTo("appid-1"))).thenReturn(Future.successful(
        ProgressResponse("appid-1", personalDetails = true, hasSchemeLocations = true, hasSchemes = true, assistanceDetails = true,
          review = true, QuestionnaireProgressResponse(), submitted = true, withdrawn = false,
          OnlineTestProgressResponse(invited = true, started = true, completed = false, expired = false, awaitingReevaluation = false,
            failed = false, failedNotified = true, awaitingAllocation = true, awaitingAllocationNotified = false, allocationConfirmed = false,
            allocationUnconfirmed = false))
      ))
      when(mockApplicationRepository.findProgress(eqTo("appid-2"))).thenReturn(Future.successful(
        ProgressResponse("appid-2", personalDetails = true, hasSchemeLocations = true, hasSchemes = true, assistanceDetails = true,
          review = true, QuestionnaireProgressResponse(), submitted = true, withdrawn = false,
          OnlineTestProgressResponse(invited = true, started = true, completed = false, expired = false, awaitingReevaluation = false,
            failed = false, failedNotified = true, awaitingAllocation = true, awaitingAllocationNotified = false, allocationConfirmed = false,
            allocationUnconfirmed = false))
      ))
    }

    def applicationRepositoryWithOneVenueOneDateOneNormalCandidateAndWithdrawnCandidate = {
      when(mockApplicationRepository.find(any[List[String]])).thenReturn(Future.successful(
        List(
          Candidate(
            "userid-2",
            Some("appid-2"),
            None,
            Some("Michael"),
            Some("Jackson"),
            Some("Jackson"),
            None,
            None,
            None
          )
        )
      ))
      when(mockApplicationRepository.findProgress(eqTo("appid-1"))).thenReturn(Future.successful(
        ProgressResponse("appid-1", personalDetails = true, hasSchemeLocations = true, hasSchemes = true, assistanceDetails = true,
          review = true, QuestionnaireProgressResponse(), submitted = true, withdrawn = true,
          OnlineTestProgressResponse(invited = true, started = true, completed = false, expired = false, awaitingReevaluation = false,
            failed = false, failedNotified = true, awaitingAllocation = true, awaitingAllocationNotified = false, allocationConfirmed = false,
            allocationUnconfirmed = false))
      ))
      when(mockApplicationRepository.findProgress(eqTo("appid-2"))).thenReturn(Future.successful(
        ProgressResponse("appid-2", personalDetails = true, hasSchemeLocations = true, hasSchemes = true, assistanceDetails = true,
          review = true, QuestionnaireProgressResponse(), submitted = true, withdrawn = false,
          OnlineTestProgressResponse(invited = true, started = true, completed = false, expired = false, awaitingReevaluation = false,
            failed = false, failedNotified = true, awaitingAllocation = true, awaitingAllocationNotified = false, allocationConfirmed = false,
            allocationUnconfirmed = false))
      ))
    }

    def applicationAssessmentRepoWithOneVenueDateCandidate = {
      when(mockApplicationAssessmentRepository.findAllForDate(any(), any())).thenReturn(Future.successful(
        List(
          AssessmentCentreAllocation(
            "appid-1",
            "Test Venue 1",
            LocalDate.parse("2015-04-25"),
            "AM",
            1,
            confirmed = true
          )
        )
      ))
    }

    def applicationAssessmentRepoWithOneVenueOneDateOneCandidateInPMOneInAMSession = {
      when(mockApplicationAssessmentRepository.findAllForDate(any(), any())).thenReturn(Future.successful(
        List(
          AssessmentCentreAllocation(
            "appid-1",
            "Test Venue 1",
            LocalDate.parse("2015-04-25"),
            "AM",
            1,
            confirmed = true
          ),
          AssessmentCentreAllocation(
            "appid-2",
            "Test Venue 1",
            LocalDate.parse("2015-04-25"),
            "PM",
            1,
            confirmed = true
          )
        )
      ))
    }

    def assessmentCentreRepoWithNoDates = {
      when(mockAssessmentCentreRepository.assessmentCentreCapacityDate(any(), any())).thenReturn(Future.successful(
        AssessmentCentreVenueCapacityDate(
          LocalDate.parse("2015-04-25"),
          3,
          6,
          10,
          4,
          10,
          4
        )
      ))

      when(mockAssessmentCentreRepository.assessmentCentreCapacities).thenReturn(Future.successful(List(
        AssessmentCentreLocation(
          locationName = "London",
          venues = List(AssessmentCentreVenue(
            venueName = "London FTAC",
            venueDescription = "London centre",
            capacityDates = List(
              AssessmentCentreVenueCapacityDate(date = LocalDate.now, amCapacity = 18, pmCapacity = 18, 10, 4, 10, 4)
            )
          ))
        )
      )))
      
    }

    def applicationRepositoryWithNoVenueDateCandidates = {
      when(mockApplicationRepository.find(any[List[String]])).thenReturn(Future.successful(
        List()
      ))
    }

    def applicationAssessmentRepoWithNoVenueDateCandidates = {
      when(mockApplicationAssessmentRepository.findAllForDate(any(), any())).thenReturn(Future.successful(
        List()
      ))
    }

    def applicationAssessmentRepositoryThatCanDelete = {
      when(mockApplicationAssessmentService.removeFromAssessmentCentreSlot(any())).thenReturn(
        Future.successful(())
      )
      when(mockApplicationRepository.findProgress(any())).thenReturn(
        Future.successful(ProgressResponse(""))
      )
      when(mockAppService.removeProgressStatuses(any(), any())).thenReturn(Future.successful(unit))
    }
    def applicationAssessmentRepositoryThatCannotDeleteUnauthorised = {
      when(mockApplicationRepository.findProgress(any())).thenReturn(
        Future.successful(ProgressResponse("", assessmentScores = AssessmentScores(accepted = true)))
      )
      when(mockApplicationAssessmentService.removeFromAssessmentCentreSlot(any())).thenReturn(Future.successful(()))
      when(mockAppService.removeProgressStatuses(any(), any())).thenReturn(Future.successful(unit))
    }

    def applicationAssessmentRepositoryThatCannotDelete = {
      when(mockApplicationAssessmentService.removeFromAssessmentCentreSlot(any())).thenReturn(
        Future.failed(new NotFoundException("Non existent app"))
      )
      when(mockApplicationRepository.findProgress(any())).thenReturn(
        Future.successful(ProgressResponse(""))
      )
      when(mockAppService.removeProgressStatuses(any(), any())).thenReturn(Future.successful(unit))
    }

    def onlineTestRepositoryThatCanRemoveAllocationStatus = {
      when(mockOnlineTestRepository.removeCandidateAllocationStatus(any())).thenReturn(
        Future.successful(())
      )
    }

    def contactDetailsRepository = {
      when(mockContactDetailsRepository.find(eqTo("userid-1"))).thenReturn(
        Future.successful(
          ContactDetails(outsideUk = false, Address("address1"), Some("postCode1"), None, "email1@mailinator.com", Some("11111111"))
        )
      )
      when(mockContactDetailsRepository.find(eqTo("userid-2"))).thenReturn(
        Future.successful(
          ContactDetails(outsideUk = false, Address("address2"), Some("postCode2"), None, "email2@mailinator.com", Some("22222222"))
        )
      )
    }

    def personalDetailsRepository = {
      when(mockPersonalDetailsRepository.find(eqTo("appid-1"))).thenReturn(
        Future.successful(PersonalDetails("firstName1", "lastName1", "preferredName1", LocalDate.parse("2016-05-26"),
          aLevel = true, stemLevel = true, civilServant = false, department = None))
      )
      when(mockPersonalDetailsRepository.find(eqTo("appid-2"))).thenReturn(
        Future.successful(PersonalDetails("firstName2", "lastName2", "preferredName2", LocalDate.parse("2016-05-26"),
          aLevel = true, stemLevel = true, civilServant = false, department = None))
      )
    }
  }
}
