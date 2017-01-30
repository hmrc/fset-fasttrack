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

package repositories.application

import config.MicroserviceAppConfig._
import controllers.OnlineTestDetails
import factories.DateTimeFactory
import model.ApplicationStatuses.BSONEnumHandler
import model.EvaluationResults._
import model.Exceptions.{ NotFoundException, OnlineTestFirstLocationResultNotFound, OnlineTestPassmarkEvaluationNotFound, UnexpectedException }
import model.OnlineTestCommands._
import model.ProgressStatuses
import model.PersistedObjects.{ ApplicationForNotification, ApplicationIdWithUserIdAndStatus, ExpiringOnlineTest, OnlineTestPassmarkEvaluation }
import model.Adjustments._
import model.persisted.SchemeEvaluationResult
import model.{ AdjustmentDetail, ApplicationStatuses, Commands }
import org.joda.time.{ DateTime, LocalDate }
import reactivemongo.api.DB
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID }
import repositories._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

trait OnlineTestRepository {
  def nextApplicationPendingExpiry: Future[Option[ExpiringOnlineTest]]

  def nextApplicationPendingFailure: Future[Option[ApplicationForNotification]]

  def nextApplicationReadyForOnlineTesting: Future[Option[OnlineTestApplication]]

  def nextApplicationReadyForReportRetriving: Future[Option[OnlineTestApplicationWithCubiksUser]]

  def nextApplicationReadyForPDFReportRetrieving(): Future[Option[OnlineTestApplicationWithCubiksUser]]

  def getOnlineTestDetails(userId: String): Future[OnlineTestDetails]

  def updateStatus(userId: String, status: ApplicationStatuses.EnumVal): Future[Unit]

  def updateExpiryTime(userId: String, expirationDate: DateTime): Future[Unit]

  def consumeToken(token: String): Future[Unit]

  def storeOnlineTestProfileAndUpdateStatusToInvite(applicationId: String, onlineTestProfile: OnlineTestProfile): Future[Unit]

  def getOnlineTestApplication(appId: String): Future[Option[OnlineTestApplication]]

  def updateXMLReportSaved(applicationId: String): Future[Unit]

  def updatePDFReportSaved(applicationId: String): Future[Unit]

  def nextApplicationPassMarkProcessing(currentVersion: String): Future[Option[ApplicationIdWithUserIdAndStatus]]

  def savePassMarkScore(applicationId: String, version: String, evaluationResult: List[SchemeEvaluationResult],
    applicationStatus: ApplicationStatuses.EnumVal): Future[Unit]

  def removeCandidateAllocationStatus(applicationId: String): Future[Unit]

  def saveCandidateAllocationStatus(applicationId: String, applicationStatus: ApplicationStatuses.EnumVal,
    expireDate: Option[LocalDate]): Future[Unit]

  def findPassmarkEvaluation(appId: String): Future[OnlineTestPassmarkEvaluation]
}

class OnlineTestMongoRepository(dateTime: DateTimeFactory)(implicit mongo: () => DB)
  extends ReactiveRepository[OnlineTestDetails, BSONObjectID](CollectionNames.APPLICATION, mongo,
    Commands.Implicits.onlineTestDetailsFormat, ReactiveMongoFormats.objectIdFormats) with OnlineTestRepository with RandomSelection {

  private def applicationStatus(status: ApplicationStatuses.EnumVal): BSONDocument = {
    import model.ApplicationStatuses._

    val flag = status match {
      case OnlineTestInvited => ProgressStatuses.OnlineTestInvitedProgress
      case OnlineTestStarted => ProgressStatuses.OnlineTestStartedProgress
      case OnlineTestCompleted => ProgressStatuses.OnlineTestCompletedProgress
      case OnlineTestExpired => ProgressStatuses.OnlineTestExpiredProgress
      case OnlineTestFailed => ProgressStatuses.OnlineTestFailedProgress
      case OnlineTestFailedNotified => ProgressStatuses.OnlineTestFailedNotifiedProgress
    }

    if (flag == ProgressStatuses.OnlineTestCompletedProgress) {
      BSONDocument("$set" -> BSONDocument(
        s"progress-status.$flag" -> true,
        "applicationStatus" -> status,
        "online-tests.completionDate" -> DateTime.now
      ))
    } else {
      BSONDocument("$set" -> BSONDocument(
        s"progress-status.$flag" -> true,
        "applicationStatus" -> status
      ))
    }
  }

  override def getOnlineTestDetails(userId: String): Future[OnlineTestDetails] = {

    val query = BSONDocument("userId" -> userId)
    val projection = BSONDocument("online-tests" -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(document) if document.getAs[BSONDocument]("online-tests").isDefined =>
        val root = document.getAs[BSONDocument]("online-tests").get
        val onlineTestUrl = root.getAs[String]("onlineTestUrl").get
        val token = root.getAs[String]("token").get
        val invitationDate = root.getAs[DateTime]("invitationDate").get
        val expirationDate = root.getAs[DateTime]("expirationDate").get

        OnlineTestDetails(invitationDate, expirationDate, onlineTestUrl, s"$token@${cubiksGatewayConfig.emailDomain}",
          isOnlineTestEnabled = true
        )

      case _ => throw NotFoundException()
    }
  }

  override def updateStatus(userId: String, status: ApplicationStatuses.EnumVal): Future[Unit] = {
    val query = BSONDocument("userId" -> userId)
    val applicationStatusBSON = applicationStatus(status)

    collection.update(query, applicationStatusBSON, upsert = false) map {
      case r if r.n == 0 => throw new NotFoundException(s"updateStatus didn't update anything for userId:$userId")
      case r if r.n > 1 => throw UnexpectedException(s"updateStatus somehow updated more than one record for userId:$userId")
      case _ =>
    }
  }

  override def updateExpiryTime(userId: String, expirationDate: DateTime): Future[Unit] = {

    val queryUser = BSONDocument("userId" -> userId)
    val queryUserExpired = BSONDocument("userId" -> userId, "applicationStatus" -> ApplicationStatuses.OnlineTestExpired)
    val newExpiryTime = BSONDocument("$set" -> BSONDocument(
      "online-tests.expirationDate" -> expirationDate
    ))
    val newStatus = BSONDocument("$set" -> BSONDocument(
      s"progress-status.${ProgressStatuses.OnlineTestExpiredProgress}" -> false,
      s"progress-status.${ProgressStatuses.OnlineTestInvitedProgress}" -> true,
      "applicationStatus" -> ApplicationStatuses.OnlineTestInvited
    ))

    for {
      status <- collection.update(queryUser, newExpiryTime, upsert = false)
      _ <- collection.update(queryUserExpired, newStatus, upsert = false)
    } yield {
      if (status.n == 0) throw new NotFoundException(s"updateStatus didn't update anything for userId:$userId")
      if (status.n > 1) throw UnexpectedException(s"updateStatus somehow updated more than one record for userId:$userId")
    }
  }

  override def consumeToken(token: String): Future[Unit] = {
    val query = BSONDocument("online-tests.token" -> token)

    val applicationStatusBSON = applicationStatus(ApplicationStatuses.OnlineTestCompleted)

    collection.update(query, applicationStatusBSON, upsert = false).map { _ => () }
  }

  override def storeOnlineTestProfileAndUpdateStatusToInvite(applicationId: String, onlineTestProfile: OnlineTestProfile): Future[Unit] = {
    import model.ProgressStatuses._

    val query = BSONDocument("applicationId" -> applicationId)

    val applicationStatusBSON = BSONDocument("$unset" -> BSONDocument(
      s"progress-status.$OnlineTestStartedProgress" -> "",
      s"progress-status.$OnlineTestCompletedProgress" -> "",
      s"progress-status.$OnlineTestExpiredProgress" -> "",
      s"progress-status.$AwaitingOnlineTestReevaluationProgress" -> "",
      s"progress-status.$OnlineTestFailedProgress" -> "",
      s"progress-status.$OnlineTestFailedNotifiedProgress" -> "",
      s"progress-status.$AwaitingOnlineTestAllocationProgress" -> "",
      s"online-tests.xmlReportSaved" -> "",
      s"online-tests.pdfReportSaved" -> "",
      s"passmarkEvaluation" -> ""
    )) ++ BSONDocument("$set" -> BSONDocument(
      s"progress-status.$OnlineTestInvitedProgress" -> true,
      "applicationStatus" -> ApplicationStatuses.OnlineTestInvited,
      "online-tests.cubiksUserId" -> onlineTestProfile.cubiksUserId,
      "online-tests.token" -> onlineTestProfile.token,
      "online-tests.onlineTestUrl" -> onlineTestProfile.onlineTestUrl,
      "online-tests.invitationDate" -> onlineTestProfile.invitationDate,
      "online-tests.expirationDate" -> onlineTestProfile.expirationDate,
      "online-tests.participantScheduleId" -> onlineTestProfile.participantScheduleId
    ))

    collection.update(query, applicationStatusBSON, upsert = false) map { _ => () }
  }

  def getOnlineTestApplication(appId: String): Future[Option[OnlineTestApplication]] = {
    val query = BSONDocument(
      "applicationId" -> appId
    )
    collection.find(query).one[BSONDocument] map {
      _.map(bsonDocToOnlineTestApplication)
    }
  }

  def nextApplicationPendingExpiry: Future[Option[ExpiringOnlineTest]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument(
        "online-tests.expirationDate" -> BSONDocument("$lte" -> dateTime.nowLocalTimeZone) // Serialises to UTC.
      ),
      BSONDocument("$or" -> BSONArray(
        BSONDocument("applicationStatus" -> ApplicationStatuses.OnlineTestInvited),
        BSONDocument("applicationStatus" -> ApplicationStatuses.OnlineTestStarted)
      ))
    ))

    selectRandom(query).map(_.map(bsonDocToExpiringOnlineTest))
  }

  def nextApplicationPendingFailure: Future[Option[ApplicationForNotification]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> ApplicationStatuses.OnlineTestFailed),
      BSONDocument("online-tests.pdfReportSaved" -> true)
    ))
    selectRandom(query).map(_.map(bsonDocToApplicationForNotification))
  }

  def nextApplicationReadyForOnlineTesting: Future[Option[OnlineTestApplication]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> ApplicationStatuses.Submitted),
      BSONDocument("$or" -> BSONArray(
        BSONDocument("assistance-details.needsSupportForOnlineAssessment" -> false),
        BSONDocument("$and" -> BSONArray(
          BSONDocument("assistance-details.needsSupportForOnlineAssessment" -> true),
          BSONDocument("assistance-details.confirmedAdjustments" -> true)
        ))
      ))
    ))
    selectRandom(query).map(_.map(bsonDocToOnlineTestApplication))
  }

  def nextApplicationReadyForReportRetriving: Future[Option[OnlineTestApplicationWithCubiksUser]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> ApplicationStatuses.OnlineTestCompleted),
      BSONDocument("online-tests.xmlReportSaved" ->
        BSONDocument("$ne" -> true))
    ))

    selectRandom(query).map(_.map(bsonDocToOnlineTestApplicationForReportRetrieving))
  }

  override def nextApplicationReadyForPDFReportRetrieving(): Future[Option[OnlineTestApplicationWithCubiksUser]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("online-tests.pdfReportSaved" -> BSONDocument("$ne" -> true)),
      BSONDocument("online-tests.xmlReportSaved" -> BSONDocument("$eq" -> true))
    ))
    selectRandom(query).map(_.map(bsonDocToOnlineTestApplicationForReportRetrieving))
  }

  private def bsonDocToExpiringOnlineTest(doc: BSONDocument) = {
    val applicationId = doc.getAs[String]("applicationId").get
    val userId = doc.getAs[String]("userId").get
    val personalDetailsRoot = doc.getAs[BSONDocument]("personal-details").get
    val preferredName = personalDetailsRoot.getAs[String]("preferredName").get
    ExpiringOnlineTest(applicationId, userId, preferredName)
  }

  private def bsonDocToApplicationForNotification(doc: BSONDocument) = {
    val applicationId = doc.getAs[String]("applicationId").get
    val userId = doc.getAs[String]("userId").get
    val applicationStatus = doc.getAs[ApplicationStatuses.EnumVal]("applicationStatus").get
    val personalDetailsRoot = doc.getAs[BSONDocument]("personal-details").get
    val preferredName = personalDetailsRoot.getAs[String]("preferredName").get
    ApplicationForNotification(applicationId, userId, preferredName, applicationStatus)
  }

  private def bsonDocToOnlineTestApplication(doc: BSONDocument) = {
    val applicationId = doc.getAs[String]("applicationId").get
    val applicationStatus = doc.getAs[String]("applicationStatus").get
    val userId = doc.getAs[String]("userId").get

    val personalDetailsRoot = doc.getAs[BSONDocument]("personal-details").get
    val preferredName = personalDetailsRoot.getAs[String]("preferredName").get

    val ad = doc.getAs[BSONDocument]("assistance-details")
    val needsSupportForOnlineAssessment = ad.flatMap(_.getAs[Boolean]("needsSupportForOnlineAssessment")).get
    val guaranteedInterview = ad.flatMap(_.getAs[Boolean]("guaranteedInterview")).getOrElse(false)

    val onlineTestsAdjustments = ad.flatMap(_.getAs[AdjustmentDetail]("onlineTests"))

    OnlineTestApplication(applicationId, applicationStatus, userId, guaranteedInterview,
      needsSupportForOnlineAssessment, preferredName, onlineTestsAdjustments)
  }

  private def bsonDocToOnlineTestApplicationForReportRetrieving(doc: BSONDocument) = {
    val applicationId = doc.getAs[String]("applicationId").get
    val userId = doc.getAs[String]("userId").get

    def errorMsg(attr: String) = s"Error retrieving $attr for user with applicationId:$applicationId ${doc.toString()}"

    val onlineTests = doc.getAs[BSONDocument]("online-tests")
      .getOrElse(throw new Exception(errorMsg("online-tests")))
    val cubiksUserId = onlineTests.getAs[Int]("cubiksUserId")
      .getOrElse(throw new Exception(errorMsg("cubiksUserId")))

    OnlineTestApplicationWithCubiksUser(applicationId, userId, cubiksUserId)
  }

  def updateXMLReportSaved(applicationId: String): Future[Unit] = {
    updateFlag(applicationId, "online-tests.xmlReportSaved", value = true)
  }

  def updatePDFReportSaved(applicationId: String): Future[Unit] = {
    updateFlag(applicationId, "online-tests.pdfReportSaved", value = true)
  }

  private def updateFlag(applicationId: String, flag: String, value: Boolean): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val MRAReportGenerated = BSONDocument("$set" -> BSONDocument(
      flag -> value
    ))

    for {
      status <- collection.update(query, MRAReportGenerated, upsert = false)
    } yield {
      if (status.n == 0) throw new NotFoundException(s"updateStatus didn't update anything for applicationId:$applicationId")
      if (status.n > 1) throw UnexpectedException(s"updateStatus updated more than one record for applicationId:$applicationId")
    }
  }

  def nextApplicationPassMarkProcessing(currentVersion: String): Future[Option[ApplicationIdWithUserIdAndStatus]] = {
    val query = BSONDocument("$or" -> BSONArray(
      BSONDocument(
        "applicationStatus" -> ApplicationStatuses.OnlineTestCompleted,
        "online-tests.xmlReportSaved" -> true,
        "passmarkEvaluation.passmarkVersion" -> BSONDocument("$exists" -> false)),
      BSONDocument(
        "applicationStatus" -> ApplicationStatuses.AwaitingOnlineTestReevaluation,
        "passmarkEvaluation.passmarkVersion" -> BSONDocument("$ne" -> currentVersion)),
      BSONDocument(
        "applicationStatus" -> ApplicationStatuses.AssessmentScoresAccepted,
        "passmarkEvaluation.passmarkVersion" -> BSONDocument("$ne" -> currentVersion))

    ))

    selectRandom(query).map(_.map { doc =>
      val applicationId = doc.getAs[String]("applicationId").get
      val userId = doc.getAs[String]("userId").get
      val applicationStatus = doc.getAs[ApplicationStatuses.EnumVal]("applicationStatus")
        .getOrElse(throw new IllegalStateException("applicationStatus must be defined"))

      ApplicationIdWithUserIdAndStatus(applicationId, userId, applicationStatus)
    })
  }

  def savePassMarkScore(applicationId: String, version: String, evaluationResult: List[SchemeEvaluationResult],
                        applicationStatus: ApplicationStatuses.EnumVal): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val progressStatus = applicationStatus match {
      case ApplicationStatuses.AwaitingOnlineTestReevaluation => ProgressStatuses.AwaitingOnlineTestReevaluationProgress
      case ApplicationStatuses.OnlineTestFailed => ProgressStatuses.OnlineTestFailedProgress
      case ApplicationStatuses.AwaitingAllocation => ProgressStatuses.AwaitingOnlineTestAllocationProgress
    }

    val passMarkEvaluation = BSONDocument("$set" ->
      BSONDocument(
        "passmarkEvaluation" -> BSONDocument(
          "passmarkVersion" -> version,
          "result" -> evaluationResult
        ),
        "applicationStatus" -> applicationStatus,
        s"progress-status.$progressStatus" -> true
      ))

    collection.update(query, passMarkEvaluation, upsert = false).map(checkUpdateWriteResult)
  }

  private def schemeToBSON(scheme: (String, Option[Result])) = scheme._2 match {
    case Some(s) => BSONDocument(scheme._1 -> s.toString)
    case _ => BSONDocument.empty
  }

  def saveCandidateAllocationStatus(applicationId: String, applicationStatus: ApplicationStatuses.EnumVal,
    expireDate: Option[LocalDate]
  ): Future[Unit] = {
    import ApplicationStatuses._

    val query = BSONDocument("applicationId" -> applicationId)

    val progressStatus = Try(applicationStatus match {
      case AllocationConfirmed => ProgressStatuses.AllocationConfirmedProgress
      case AllocationUnconfirmed => ProgressStatuses.AllocationUnconfirmedProgress
    })

    val update = (progressStatus: String) => BSONDocument("$set" -> {
      def withExpireDate =
        BSONDocument(
          "applicationStatus" -> applicationStatus,
          s"progress-status.$progressStatus" -> true,
          s"progress-status-dates.$progressStatus" -> LocalDate.now(),
          "allocation-expire-date" -> expireDate.get
        )

      def withoutExpireDate =
        BSONDocument(
          "applicationStatus" -> applicationStatus,
          s"progress-status.$progressStatus" -> true,
          s"progress-status-dates.$progressStatus" -> LocalDate.now()
        )

      if (expireDate.isDefined) {
        withExpireDate
      } else {
        withoutExpireDate
      }
    })

    Future.fromTry(progressStatus).flatMap { progress =>
      collection.update(query, update(progress), upsert = false).map(checkUpdateWriteResult)
    }
  }

  def removeCandidateAllocationStatus(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val deAllocationSet = BSONDocument("$set" -> {
      BSONDocument(
        "applicationStatus" -> ApplicationStatuses.AwaitingAllocation,
        s"progress-status.${ProgressStatuses.AwaitingOnlineTestAllocationProgress}" -> true,
        s"progress-status-dates.${ProgressStatuses.AwaitingOnlineTestAllocationProgress}" -> LocalDate.now()
      )
    })

    val deAllocationUnset = BSONDocument("$unset" -> {
      BSONDocument(
        s"progress-status.${ProgressStatuses.AllocationConfirmedProgress}" -> "",
        s"progress-status.${ProgressStatuses.AllocationUnconfirmedProgress}" -> "",
        s"progress-status-dates.${ProgressStatuses.AllocationConfirmedProgress}" -> "",
        s"progress-status-dates.${ProgressStatuses.AllocationUnconfirmedProgress}" -> "",
        "allocation-expire-date" -> ""
      )
    })

    collection.update(query, deAllocationSet, upsert = false).map(checkUpdateWriteResult).flatMap(_ =>
      collection.update(query, deAllocationUnset, upsert = false).map(checkUpdateWriteResult))
  }

  def findPassmarkEvaluation(appId: String): Future[OnlineTestPassmarkEvaluation] = {
    val query = BSONDocument("applicationId" -> appId)
    val projection = BSONDocument("passmarkEvaluation" -> 1)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(doc) if doc.getAs[BSONDocument]("passmarkEvaluation").isDefined =>
        val pe = doc.getAs[BSONDocument]("passmarkEvaluation").get
        val otLocation1Scheme1Result = pe.getAs[String]("location1Scheme1").map(Result(_))
        val otLocation1Scheme2Result = pe.getAs[String]("location1Scheme2").map(Result(_))
        val otLocation2Scheme1Result = pe.getAs[String]("location2Scheme1").map(Result(_))
        val otLocation2Scheme2Result = pe.getAs[String]("location2Scheme2").map(Result(_))
        val otAlternativeResult = pe.getAs[String]("alternativeScheme").map(Result(_))

        OnlineTestPassmarkEvaluation(
          otLocation1Scheme1Result.getOrElse(throw OnlineTestFirstLocationResultNotFound(appId)),
          otLocation1Scheme2Result,
          otLocation2Scheme1Result,
          otLocation2Scheme2Result,
          otAlternativeResult
        )
      case _ => throw OnlineTestPassmarkEvaluationNotFound(appId)
    }
  }

  private def checkUpdateWriteResult(writeResult: UpdateWriteResult): Unit = {
    writeResult.errmsg.map(msg => throw UnexpectedException(s"Database update failed: $msg"))
  }
}
