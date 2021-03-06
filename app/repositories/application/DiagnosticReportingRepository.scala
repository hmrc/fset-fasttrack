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

package repositories.application

import model.Commands
import model.Commands.CreateApplicationRequest
import model.Exceptions.ApplicationNotFound
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.{ JsObject, JsValue, Json }
import reactivemongo.api.{ DB, ReadPreference }
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers._
import repositories._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait DiagnosticReportingRepository {

  def findByApplicationId(applicationId: String): Future[JsObject]
  def findAll(): Enumerator[JsValue]

}

class DiagnosticReportingMongoRepository(implicit mongo: () => DB)
    extends ReactiveRepository[CreateApplicationRequest, BSONObjectID](CollectionNames.APPLICATION, mongo,
      Commands.Implicits.createApplicationRequestFormats, ReactiveMongoFormats.objectIdFormats) with DiagnosticReportingRepository {

  private val defaultExclusions = Json.obj(
    "_id" -> 0,
    "personal-details" -> 0
  ) // these reports should not export personally identifiable data

  // Add "field" -> 0 here to exclude fields from report
  private val largeFields = Json.obj()

  override def findByApplicationId(applicationId: String): Future[JsObject] = {
    val projection = defaultExclusions

    val results = collection.find(Json.obj("applicationId" -> applicationId), projection)
      .cursor[JsObject](ReadPreference.primaryPreferred)
      .collect[List]()

    results.map { r =>
      if (r.isEmpty) { throw ApplicationNotFound(applicationId) }
      else { r.head }
    }
  }

  override def findAll(): Enumerator[JsValue] = {
    val projection = defaultExclusions ++ largeFields
    collection.find(Json.obj(), projection)
      .cursor[JsValue](ReadPreference.primaryPreferred)
      .enumerate()
  }
}
