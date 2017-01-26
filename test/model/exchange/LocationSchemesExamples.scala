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

package model.exchange

import repositories.LocationSchemes

object LocationSchemesExamples {
  val LocationSchemes1 = LocationSchemes("id1", "testLocation1", 2.0, 5.0,
    schemes = List("SchemeNoALevels", "SchemeALevels", "SchemeALevelsStem"))
  val LocationSchemes2 = LocationSchemes("id2", "testLocation2", 6.0, 2.0,
    schemes = List("SchemeNoALevels", "SchemeALevels", "SchemeALevelsStem"))
  val LocationSchemes3 = LocationSchemes("id3", "testLocation3", 2.5, 2.6,
    schemes = List("SchemeNoALevels", "SchemeALevels", "SchemeALevelsStem"))
  val LocationSchemes4 = LocationSchemes("id4", "testLocation4", 1.0, 1.0,
    schemes = List("SchemeNoALevels", "SchemeALevels", "SchemeALevelsStem"))
}
