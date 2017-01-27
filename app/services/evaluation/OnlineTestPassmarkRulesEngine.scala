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

package services.evaluation

import connectors.PassMarkExchangeObjects.{ SchemeThreshold, SchemeThresholds }
import model.EvaluationResults._
import model.OnlineTestCommands.{ CandidateScoresWithPreferencesAndPassmarkSettings, TestResult }
import model.PersistedObjects.CandidateTestReport
import model.Scheme.Scheme
import model.Schemes

trait OnlineTestPassmarkRulesEngine {

  def evaluate(score: CandidateScoresWithPreferencesAndPassmarkSettings): Map[Scheme, Result]
}

object OnlineTestPassmarkRulesEngine extends OnlineTestPassmarkRulesEngine {

  // TODO LT: Next to work on START HERE
  def evaluate(candidateData: CandidateScoresWithPreferencesAndPassmarkSettings): Map[Scheme, Result] = {
    val schemes = candidateData.schemes

//    def evaluateAgainstScheme = evaluateScore(score) _
//    def evaluateAgainstScheme(scheme: Scheme) =
//      evaluateScore(score) _

//    val location1Scheme1Result = evaluateAgainstScheme(location1Scheme1)
//    val location1Scheme2Result = location1Scheme2 map evaluateAgainstScheme
//    val location2Scheme1Result = location2Scheme1 map evaluateAgainstScheme
//    val location2Scheme2Result = location2Scheme2 map evaluateAgainstScheme
//    val alternativeSchemeResult = alternativeScheme collect { case true => evaluateScoreForAllSchemes(score) }

    val res = schemes.map{ scheme =>
      val result = evaluateScore(candidateData, scheme.toString)
      scheme -> result
    }.toMap
    res
  }

  private def evaluateScore(candidateScores: CandidateScoresWithPreferencesAndPassmarkSettings, schemeName: String) = {
//  private def evaluateScore(candidateScores: CandidateScoresWithPreferencesAndPassmarkSettings)(schemeName: String) = {
    val passmark = candidateScores.passmarkSettings.schemes.find(_.schemeName == schemeName)
      .getOrElse(throw new IllegalStateException(s"schemeName=$schemeName is not set in Passmark settings"))

//    passmark.schemeThresholds match {
//      case threshold @ SchemeThresholds(_, _, _, _, Some(_)) =>
//        CombinationScoreProcessor.determineResult(candidateScores.scores, threshold)
//      case threshold @ SchemeThresholds(_, _, _, _, None) => IndividualScoreProcessor.determineResult(candidateScores.scores, threshold)
//    }
    IndividualScoreProcessor.determineResult(candidateScores.scores, passmark.schemeThresholds)
  }
/*
  private def evaluateScoreForAllSchemes(score: CandidateScoresWithPreferencesAndPassmarkSettings) = {
    val evaluation = Schemes.AllSchemes.map { scheme =>
      evaluateScore(score)(scheme)
    }

    if (evaluation.contains(Green)) {
      Green
    } else if (evaluation.contains(Amber)) {
      Amber
    } else {
      Red
    }
  }
*/
}

trait ScoreProcessor {

  def determineResult(scores: CandidateTestReport, passmarkThreshholds: SchemeThresholds): Result

}

object IndividualScoreProcessor extends ScoreProcessor {

  def determineResult(scores: CandidateTestReport, thresholds: SchemeThresholds): Result = {
    val resultsToPassmark = List(
      (scores.competency, thresholds.competency),
      (scores.verbal, thresholds.verbal),
      (scores.numerical, thresholds.numerical),
      (scores.situational, thresholds.situational)
    )

    // TODO: Should we add explicit flag isGISCandidate to make sure only 2 tests are empty?
    val testResults: Seq[Result] = resultsToPassmark.map {
      case (None, _) => Green
      case (Some(TestResult(_, _, Some(tScore), _, _, _)), passMark) => schemeResult(tScore, passMark)
      case (Some(TestResult(_, _, None, _, _, _)), expectedPassmark) =>
        throw new IllegalArgumentException(s"Candidate report does not have tScore: $scores")
    }

    if (testResults.contains(Red)) {
      Red
    } else if (testResults.forall(_ == Green)) {
      Green
    } else {
      Amber
    }
  }

  private def schemeResult(tScore: Double, passMark: SchemeThreshold) = {
    if (tScore >= passMark.passThreshold) {
      Green
    } else if (tScore > passMark.failThreshold) {
      Amber
    } else {
      Red
    }
  }
}

object CombinationScoreProcessor extends ScoreProcessor {

  def determineResult(scores: CandidateTestReport, thresholds: SchemeThresholds): Result = {
    val individualResult = IndividualScoreProcessor.determineResult(scores, thresholds)

    val average = averageTScore(scores)
    val combinedThresholds = thresholds.combination
      .getOrElse(throw new IllegalStateException("Cannot find combined passmark settings"))

    if (individualResult == Red) {
      Red
    } else if (individualResult == Amber) {
      if (average <= combinedThresholds.failThreshold) Red else Amber
    } else {
      if (average >= combinedThresholds.passThreshold) {
        Green
      } else if (average > combinedThresholds.failThreshold) {
        Amber
      } else {
        Red
      }
    }
  }

  private def averageTScore(scores: CandidateTestReport) = {
    val allScores = List(
      scores.competency,
      scores.verbal,
      scores.numerical,
      scores.situational
    ).flatten.flatMap(_.tScore)

    allScores.sum / allScores.length
  }
}
