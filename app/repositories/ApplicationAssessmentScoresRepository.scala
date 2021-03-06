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

package repositories

import factories.{ DateTimeFactory, UUIDFactory }
import model.AssessmentExercise.AssessmentExercise
import model.CandidateScoresCommands._
import model.Exceptions.ApplicationNotFound
import model.{ AssessmentExercise, CandidateScoresCommands }
import play.api.Logger
import play.api.libs.json.Json
import reactivemongo.api.{ DB, ReadPreference }
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID, _ }
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AssessorApplicationAssessmentScoresMongoRepository(dateTime: DateTimeFactory)(implicit mongo: () => DB)
    extends ReactiveRepository[CandidateScoresAndFeedback, BSONObjectID](CollectionNames.APPLICATION_ASSESSMENT_SCORES, mongo,
      CandidateScoresCommands.CandidateScoresAndFeedback.CandidateScoresAndFeedbackFormats, ReactiveMongoFormats.objectIdFormats)
    with ApplicationAssessmentScoresRepository with ReactiveRepositoryHelpers {

  val rolePrefix = ""

  def docToDomain(doc: BSONDocument): Option[CandidateScoresAndFeedback] = for {
    appId <- doc.getAs[String]("applicationId")
  } yield {
    CandidateScoresAndFeedback(
      appId,
      doc.getAs[ScoresAndFeedback]("interview"),
      doc.getAs[ScoresAndFeedback]("groupExercise"),
      doc.getAs[ScoresAndFeedback]("writtenExercise")
    )
  }

  def saveAllBson(scores: CandidateScoresAndFeedback): BSONDocument = BSONDocument("$set" -> scores)

  // For fixdata only
  def noDateScoresAndFeedback: Future[List[String]] = {
    val query = BSONDocument("$or" -> BSONArray(
      andMatchForNoDateExercise(CandidateScoresAndFeedback.Interview),
      andMatchForNoDateExercise(CandidateScoresAndFeedback.GroupExercise),
      andMatchForNoDateExercise(CandidateScoresAndFeedback.WrittenExercise)
      ))

    collection.find(query).cursor[BSONDocument]().collect[List]().map {
      scoresAndFeedbackWithNoSavedOrSubmittedDates => scoresAndFeedbackWithNoSavedOrSubmittedDates.map { scoresAndFeedback =>
        scoresAndFeedback.getAs[String]("applicationId").get
      }
    }
  }

  private def andMatchForNoDateExercise(exercise: String): BSONDocument = {
    BSONDocument("$and" -> BSONArray(
      BSONDocument(exercise -> BSONDocument("$exists" -> true)),
      BSONDocument(exercise + ".savedDate" -> BSONDocument("$exists" -> false)),
      BSONDocument(exercise + ".submittedDate" -> BSONDocument("$exists" -> false))
    ))
  }

  // For fixdata only
  def fixNoDateScoresAndFeedback(applicationId: String): Future[Unit] = {
    val appIdQuery = BSONDocument("applicationId" -> applicationId)

    val query = BSONDocument("$and" -> BSONArray(
      appIdQuery,
      BSONDocument("$or" -> BSONArray(
        andMatchForNoDateExercise(CandidateScoresAndFeedback.Interview),
        andMatchForNoDateExercise(CandidateScoresAndFeedback.GroupExercise),
        andMatchForNoDateExercise(CandidateScoresAndFeedback.WrittenExercise)
      ))
    ))

    val timeNow = DateTimeFactory.nowLocalTimeZone

    val addDateQueriesFut = collection.find(query).one[BSONDocument].map {
      case Some(doc) =>
        Logger.warn("[NoSaveDateFix] Scores and feedback before operation = " + Json.toJson(doc))
        List(CandidateScoresAndFeedback.Interview, CandidateScoresAndFeedback.GroupExercise,
          CandidateScoresAndFeedback.WrittenExercise).flatMap { exercise =>
            doc.getAs[BSONDocument](exercise).map { exerciseDoc =>
              // If failed to attend set a submitted date, for anything else set saved to be safe
              exerciseDoc.getAs[Boolean]("attended").map {
                case false =>
                  BSONDocument("$set" -> BSONDocument(s"$exercise.submittedDate" -> timeNow))
                case true =>
                  BSONDocument("$set" -> BSONDocument(s"$exercise.savedDate" -> timeNow))
                }.getOrElse(BSONDocument("$set" -> BSONDocument(s"$exercise.savedDate" -> timeNow)))
              }
            }
      case _ => throw new Exception(s"Application ID '$applicationId' does not have scores and feedback entries that need " +
        s"the date adding")
    }

    val validator = singleUpdateValidator(applicationId, s"Passed applicationid '$applicationId' to update but no rows " +
      s"could be updated")

    addDateQueriesFut.map { addDateQueries =>
      Logger.warn("[NoSaveDateFix] Running " + addDateQueries.length + " correction queries for appId = " + applicationId)
      addDateQueries.map { addDateQuery =>
        collection.update(appIdQuery, addDateQuery).map(validator)
      }
    }
  }
}

class ReviewerApplicationAssessmentScoresMongoRepository(dateTime: DateTimeFactory)(implicit mongo: () => DB)
    extends ReactiveRepository[CandidateScoresAndFeedback, BSONObjectID](CollectionNames.APPLICATION_ASSESSMENT_SCORES, mongo,
      CandidateScoresCommands.CandidateScoresAndFeedback.CandidateScoresAndFeedbackFormats, ReactiveMongoFormats.objectIdFormats)
    with ApplicationAssessmentScoresRepository {

  val rolePrefix = "reviewer."

  def docToDomain(doc: BSONDocument): Option[CandidateScoresAndFeedback] = for {
    appId <- doc.getAs[String]("applicationId")
    roleDoc <- doc.getAs[BSONDocument]("reviewer")
  } yield {
    CandidateScoresAndFeedback(
      appId,
      roleDoc.getAs[ScoresAndFeedback]("interview"),
      roleDoc.getAs[ScoresAndFeedback]("groupExercise"),
      roleDoc.getAs[ScoresAndFeedback]("writtenExercise")
    )
  }

  def saveAllBson(scores: CandidateScoresAndFeedback): BSONDocument =
    BSONDocument("$set" -> BSONDocument("reviewer" -> scores))

}

trait ApplicationAssessmentScoresRepository extends ReactiveRepositoryHelpers {
  this: ReactiveRepository[CandidateScoresAndFeedback, BSONObjectID] =>

  def rolePrefix: String

  def docToDomain(doc: BSONDocument): Option[CandidateScoresAndFeedback]

  def saveAllBson(scores: CandidateScoresAndFeedback): BSONDocument

  def tryFind(applicationId: String): Future[Option[CandidateScoresAndFeedback]] = {
    val query = BSONDocument("applicationId" -> applicationId)

    collection.find(query).one[BSONDocument].map(_.flatMap(docToDomain))
  }

  def findByApplicationIds(applicationIds: List[String]): Future[List[CandidateScoresAndFeedback]] = {
    val query = BSONDocument("applicationId" -> BSONDocument("$in" -> applicationIds))

    collection.find(query).cursor[BSONDocument]().collect[List]().map(_.flatMap(docToDomain))
  }

  def findNonSubmittedScores(assessorId: String): Future[List[CandidateScoresAndFeedback]] = {
    val query = BSONDocument("$or" -> BSONArray(
      BSONDocument(
        s"$rolePrefix${AssessmentExercise.interview}.submittedDate" -> BSONDocument("$exists" -> BSONBoolean(false)),
        s"$rolePrefix${AssessmentExercise.interview}.updatedBy" -> assessorId
      ),
      BSONDocument(
        s"$rolePrefix${AssessmentExercise.groupExercise}.submittedDate" -> BSONDocument("$exists" -> BSONBoolean(false)),
        s"$rolePrefix${AssessmentExercise.groupExercise}.updatedBy" -> assessorId
      ),
      BSONDocument(
        s"$rolePrefix${AssessmentExercise.writtenExercise}.submittedDate" -> BSONDocument("$exists" -> BSONBoolean(false)),
        s"$rolePrefix${AssessmentExercise.writtenExercise}.updatedBy" -> assessorId
      )
    ))

    collection.find(query).cursor[BSONDocument]().collect[List]().map { _.flatMap(docToDomain) }
  }

  def allScores: Future[Map[String, CandidateScoresAndFeedback]] = {
    val query = BSONDocument()
    val queryResult = collection.find(query).cursor[BSONDocument](ReadPreference.nearest).collect[List]()
    queryResult.map { docs =>
      docs.flatMap { doc =>
        docToDomain(doc).map { cf =>
          (cf.applicationId, cf)
        }
      }.toMap
    }
  }

  def save(exerciseScoresAndFeedback: ExerciseScoresAndFeedback,
           newVersion: Option[String] = Some(UUIDFactory.generateUUID())): Future[Unit] = {
    val applicationId = exerciseScoresAndFeedback.applicationId
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> applicationId),
      BSONDocument("$or" -> BSONArray(
        BSONDocument(s"$rolePrefix${exerciseScoresAndFeedback.exercise}.version" -> BSONDocument("$exists" -> BSONBoolean(false))),
        BSONDocument(s"$rolePrefix${exerciseScoresAndFeedback.exercise}.version" -> exerciseScoresAndFeedback.scoresAndFeedback.version))
      ))
    )

    val scoresAndFeedback = exerciseScoresAndFeedback.scoresAndFeedback
    val applicationScoresBSON = exerciseScoresAndFeedback.scoresAndFeedback.version match {
      case Some(_) => BSONDocument(
        s"$rolePrefix${exerciseScoresAndFeedback.exercise}" -> scoresAndFeedback.copy(version = newVersion)
      )
      case _ => BSONDocument(
        "applicationId" -> exerciseScoresAndFeedback.applicationId,
        s"$rolePrefix${exerciseScoresAndFeedback.exercise}" -> scoresAndFeedback.copy(version = newVersion)
      )
    }

    val candidateScoresAndFeedbackBSON = BSONDocument("$set" -> applicationScoresBSON)

    val validator = singleUpdateValidator(applicationId, "Application with correct version not found")

    collection.update(query, candidateScoresAndFeedbackBSON, upsert = exerciseScoresAndFeedback.scoresAndFeedback.version.isEmpty) map validator
  }

  def saveAll(scoresAndFeedback: CandidateScoresAndFeedback,
              newVersion: Option[String] = Some(UUIDFactory.generateUUID())): Future[Unit] = {
    val applicationId = scoresAndFeedback.applicationId
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> applicationId),
      BSONDocument("$or" -> BSONArray(
        BSONDocument(s"$rolePrefix${AssessmentExercise.interview}.version" -> BSONDocument("$exists" -> BSONBoolean(false))),
        BSONDocument(s"$rolePrefix${AssessmentExercise.interview}.version" -> scoresAndFeedback.interview.flatMap(_.version)))
      ),
      BSONDocument("$or" -> BSONArray(
        BSONDocument(s"$rolePrefix${AssessmentExercise.groupExercise}.version" -> BSONDocument("$exists" -> BSONBoolean(false))),
        BSONDocument(s"$rolePrefix${AssessmentExercise.groupExercise}.version" -> scoresAndFeedback.groupExercise.flatMap(_.version)))
      ),
      BSONDocument("$or" -> BSONArray(
        BSONDocument(s"$rolePrefix${AssessmentExercise.writtenExercise}.version" -> BSONDocument("$exists" -> BSONBoolean(false))),
        BSONDocument(s"$rolePrefix${AssessmentExercise.writtenExercise}.version" -> scoresAndFeedback.writtenExercise.flatMap(_.version)))
      )
    ))

    val candidateScoresAndFeedbackBSON = saveAllBson(scoresAndFeedback.setVersion(newVersion))

    val validator = singleUpdateValidator(applicationId, "Application with correct version not found for 'Review scores'")

    collection.update(query, candidateScoresAndFeedbackBSON, upsert = scoresAndFeedback.allVersionsEmpty) map validator
  }

  def removeExercise(applicationId: String, exercise: AssessmentExercise): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val update = BSONDocument("$unset" -> BSONDocument(rolePrefix + exercise.toString -> ""))

    val validator = singleUpdateValidator(applicationId, s"attempting to remove exercise '$exercise'",
      ApplicationNotFound(s"Could not find application '$applicationId'"))

    collection.update(query, update).map(validator)
  }

  def removeDocument(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    collection.remove(query, firstMatchOnly = false).map( _ => () )
  }
}
