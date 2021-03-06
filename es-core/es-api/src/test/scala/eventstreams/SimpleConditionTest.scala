package eventstreams

/*
 * Copyright 2014-15 Intelix Pty Ltd
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

import com.typesafe.scalalogging.StrictLogging
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers, Tag}

import scalaz.{-\/, \/-}

class SimpleConditionTest extends FlatSpec with Matchers with BeforeAndAfterEach with StrictLogging  {


  def shouldBuild(s: String)(f: Condition => Unit = _ => ()) = SimpleCondition(Some(s)) match {
    case Some(\/-(c)) => f(c)
    case x => fail(s"Failed with $x")
  }

  def shouldNotBuild(s: String) = SimpleCondition(Some(s)) match {
    case Some(\/-(c)) => fail(s"Successfully built when expected to fail $c")
    case x => ()
  }

  def shouldBuildWithAlwaysTrue(s: Option[String])(f: Condition => Unit = _ => ()) = SimpleCondition.conditionOrAlwaysTrue(s) match {
    case Some(c) => f(c)
    case x => fail(s"Failed with $x")
  }

  def shouldMatch(cond: String, json: EventFrame) = shouldBuild(cond) { c =>
    c.metFor(json) shouldBe a[\/-[_]]
  }

  def shouldNotMatch(cond: String, json: EventFrame) = shouldBuild(cond) { c =>
    c.metFor(json) shouldBe a[-\/[_]]
  }

  "conditionOrAlwaysTrue built from None" should "match to empty frame" in {
    shouldBuildWithAlwaysTrue(None) { c =>
      c.metFor(EventFrame()) shouldBe a[\/-[_]]
    }
  }
  it should "match to anything" in {
    shouldBuildWithAlwaysTrue(None) { c =>
      c.metFor((EventFrame("bla" -> 12345))) shouldBe a[\/-[_]]
    }
  }

  "conditionOrAlwaysTrue built from Some()" should "match to empty frame" in {
    shouldBuildWithAlwaysTrue(Some("")) { c =>
      c.metFor((EventFrame())) shouldBe a[\/-[_]]
    }
  }
  it should "match to anything" in {
    shouldBuildWithAlwaysTrue(Some("")) { c =>
      c.metFor((EventFrame("bla" -> 12345))) shouldBe a[\/-[_]]
    }
  }


  "optionalCondition built from None" should "be empty" in {
    SimpleCondition.optionalCondition(None) should be(None)
  }
  "optionalCondition built from Some()" should "be empty" in {
    SimpleCondition.optionalCondition(Some("")) should be(None)
  }
  "optionalCondition built from invalid statement" should "be empty" in {
    SimpleCondition.optionalCondition(Some("invalid")) should be(None)
  }


  "Simple condition" should "match field=abc in field\"->\"abc\"" in {
    shouldMatch("field=abc", EventFrame("field" -> "abc"))
  }
  it should "not match field= in field\"->\"abc\" (missing expected value)" in {
    shouldNotBuild("field=")
  }
  it should "not match field= in field\"->\"\" (missing expected value)" in {
    shouldNotBuild("field=")
  }
  it should "not build from f=1 and f2=" in {
    shouldNotBuild("f=1 and f2=")
  }
  it should "not build from f1 and f2=1" in {
    shouldNotBuild("f1 and f2=1")
  }
  it should "not build from f1 or f2=1" in {
    shouldNotBuild("f1 or f2=1")
  }
  it should "match field!=abc in \"field2\"->\"acc\"" in {
    shouldMatch("field!=abc", EventFrame("field2" -> "acc"))
  }
  it should "match field!=abc in \"field2\"->\"abc\"" in {
    shouldMatch("field!=abc", EventFrame("field2" -> "abc"))
  }
  it should "not match field=abc in \"field2\"->\"abc\"" in {
    shouldNotMatch("field=abc", EventFrame("field2" -> "abc"))
  }
  it should "not match field=abc in \"field\"->\"xyz\"" in {
    shouldNotMatch("field=abc", EventFrame("field" -> "xyz"))
  }

  it should "match field=x in \"field\"->\"xyz\"" in {
    shouldMatch("field=x", EventFrame("field" -> "xyz"))
  }
  it should "match field=xy in \"field\"->\"xyz\"" in {
    shouldMatch("field=xy", EventFrame("field" -> "xyz"))
  }
  it should "match field=xyz.* in \"field\"->\"xyz\"" in {
    shouldMatch("field=xyz.*", EventFrame("field" -> "xyz"))
  }
  it should "not match field=xyzy in \"field\"->\"xyz\"" in {
    shouldNotMatch("field=xyzy", EventFrame("field" -> "xyz"))
  }
  it should "not match field=xyz.+ in \"field\"->\"xyz\"" in {
    shouldNotMatch("field=xyz.+", EventFrame("field" -> "xyz"))
  }
  it should "match field=^xy in \"field\"->\"xyz\"" in {
    shouldMatch("field=^xy", EventFrame("field" -> "xyz"))
  }
  it should "not match field=^y in \"field\"->\"xyz\"" in {
    shouldNotMatch("field=^y", EventFrame("field" -> "xyz"))
  }
  it should "match field=yz$ in \"field\"->\"xyz\"" in {
    shouldMatch("field=^xy", EventFrame("field" -> "xyz"))
  }
  it should "not match field=y$ in \"field\"->\"xyz\"" in {
    shouldNotMatch("field=y$", EventFrame("field" -> "xyz"))
  }
  it should "match field=123 in \"field\"->\"12345\"" in {
    shouldMatch("field=123", EventFrame("field" -> "12345"))
  }
  it should "not match field=123 in \"field\"->\"1245\"" in {
    shouldNotMatch("field=123", EventFrame("field" -> "1245"))
  }
  it should "match field=123 in \"field\"->123" in {
    shouldMatch("field=123", EventFrame("field" -> 123))
  }
  it should "not match field=123a in \"field\"->123 (invalid expected number)" in {
    shouldNotMatch("field=123a", EventFrame("field" -> 123))
  }
  it should "not match field!=123a in \"field\"->123 (invalid expected number)" in {
    shouldNotMatch("field!=123a", EventFrame("field" -> 123))
  }
  it should "match field=123 in \"field\"->123.0" in {
    shouldMatch("field=123", EventFrame("field" -> 123.0))
  }
  it should "not match field=123 in \"field\"->1234" in {
    shouldNotMatch("field=123", EventFrame("field" -> 1234))
  }
  it should "not match field=123 in \"field\"->123.1" in {
    shouldNotMatch("field=123", EventFrame("field" -> 123.1))
  }

  it should "not match field!=x in \"field\"->\"xyz\"" in {
    shouldNotMatch("field!=x", EventFrame("field" -> "xyz"))
  }
  it should "not match field!=xy in \"field\"->\"xyz\"" in {
    shouldNotMatch("field!=xy", EventFrame("field" -> "xyz"))
  }
  it should "not match field!=xyz.* in \"field\"->\"xyz\"" in {
    shouldNotMatch("field!=xyz.*", EventFrame("field" -> "xyz"))
  }
  it should "match field!=xyzy in \"field\"->\"xyz\"" in {
    shouldMatch("field!=xyzy", EventFrame("field" -> "xyz"))
  }
  it should "not match field!= in \"field\"->\"xyz\" (missing expected value)" in {
    shouldNotBuild("field!=")
  }
  it should "not match field1!= in \"field\"->\"xyz\" (missing expected value)" in {
    shouldNotBuild("field1!=")
  }
  it should "match field!=xyz.+ in \"field\"->\"xyz\"" in {
    shouldMatch("field!=xyz.+", EventFrame("field" -> "xyz"))
  }
  it should "not match field!=^xy in \"field\"->\"xyz\"" in {
    shouldNotMatch("field!=^xy", EventFrame("field" -> "xyz"))
  }
  it should "match field!=^y in \"field\"->\"xyz\"" in {
    shouldMatch("field!=^y", EventFrame("field" -> "xyz"))
  }
  it should "not match field!=yz$ in \"field\"->\"xyz\"" in {
    shouldNotMatch("field!=^xy", EventFrame("field" -> "xyz"))
  }
  it should "match field!=y$ in \"field\"->\"xyz\"" in {
    shouldMatch("field!=y$", EventFrame("field" -> "xyz"))
  }
  it should "not match field!=123 in \"field\"->\"12345\"" in {
    shouldNotMatch("field!=123", EventFrame("field" -> "12345"))
  }
  it should "match field!=123 in \"field\"->\"1245\"" in {
    shouldMatch("field!=123", EventFrame("field" -> "1245"))
  }
  it should "not match field!=123 in \"field\"->123" in {
    shouldNotMatch("field!=123", EventFrame("field" -> 123))
  }
  it should "not match field!=123 in \"field\"->123.0" in {
    shouldNotMatch("field!=123", EventFrame("field" -> 123.0))
  }
  it should "match field!=123 in \"field\"->1234" in {
    shouldMatch("field!=123", EventFrame("field" -> 1234))
  }
  it should "match field!=123 in \"field\"->123.1" in {
    shouldMatch("field!=123", EventFrame("field" -> 123.1))
  }



  it should "match field>123 in \"field\"->\"124\"" in {
    shouldMatch("field>123", EventFrame("field" -> "124"))
  }
  it should "not match field<123 in \"field\"->\"124\"" in {
    shouldNotMatch("field<123", EventFrame("field" -> "124"))
  }
  it should "match field>abc in \"field\"->\"abcd\"" in {
    shouldMatch("field>abc", EventFrame("field" -> "abcd"))
  }
  it should "match field<abc in \"field\"->\"abcd\"" in {
    shouldNotMatch("field<abc", EventFrame("field" -> "abcd"))
  }

  it should "match field>123 in \"field\"->124" in {
    shouldMatch("field>123", EventFrame("field" -> 124))
  }
  it should "not match field<123 in \"field\"->124" in {
    shouldNotMatch("field<123", EventFrame("field" -> 124))
  }
  it should "match field>123.1 in \"field\"->124" in {
    shouldMatch("field>123.1", EventFrame("field" -> 124))
  }
  it should "not match field<123.1 in \"field\"->124" in {
    shouldNotMatch("field<123.1", EventFrame("field" -> 124))
  }
  it should "match field>123 in \"field\"->123.1" in {
    shouldMatch("field>123", EventFrame("field" -> 123.1))
  }
  it should "not match field<123 in \"field\"->123.1" in {
    shouldNotMatch("field<123", EventFrame("field" -> 123.1))
  }

  it should "match field<125 in \"field\"->124" in {
    shouldMatch("field<125", EventFrame("field" -> 124))
  }
  it should "not match field>125 in \"field\"->124" in {
    shouldNotMatch("field>125", EventFrame("field" -> 124))
  }
  it should "match field<124.1 in \"field\"->124" in {
    shouldMatch("field<124.1", EventFrame("field" -> 124))
  }
  it should "not match field>124.1 in \"field\"->124" in {
    shouldNotMatch("field>124.1", EventFrame("field" -> 124))
  }
  it should "match field<124 in \"field\"->123.1" in {
    shouldMatch("field<124", EventFrame("field" -> 123.1))
  }
  it should "not match field>124 in \"field\"->123.1" in {
    shouldNotMatch("field>124", EventFrame("field" -> 123.1))
  }

  it should "match field=true in \"field\"->true" in {
    shouldMatch("field=true", EventFrame("field" -> true))
  }
  it should "not match field=true in \"field\"->false" in {
    shouldNotMatch("field=true", EventFrame("field" -> false))
  }
  it should "match field!=true in \"field\"->false" in {
    shouldMatch("field!=true", EventFrame("field" -> false))
  }
  it should "not match field!=true in \"field\"->true" in {
    shouldNotMatch("field!=true", EventFrame("field" -> true))
  }

  "For input {\"tags\"=[\"abc\",\"123\"]} condition" should "match #abc=abc" taggedAs (Tag("OnlyThisTest")) in {
    shouldMatch("#abc=abc", EventFrame("tags" -> Seq("abc", "123")))
  }
  it should "not match #abc!=abc" in {
    shouldNotMatch("#abc!=abc", EventFrame("tags" -> Seq("abc", "123")))
  }
  it should "not match #xyz=xyz" in {
    shouldNotMatch("#xyz=xyz", EventFrame("tags" -> Seq("abc", "123")))
  }
  it should "not match #123!=123" in {
    shouldNotMatch("#123!=123", EventFrame("tags" -> Seq("abc", "123")))
  }
  it should "match #123=123" in {
    shouldMatch("#123=123", EventFrame("tags" -> Seq("abc", "123")))
  }

  "For input {\"tags\"=[\"abc\",\"123\"],\"f1\":{\"f2\":123.1,\"f3\":\"abc\"}} condition" should "match #abc=abc" in {
    shouldMatch("#abc=abc", EventFrame("tags" -> Seq("abc", "123"), "f1" -> EventFrame("f2" -> 123, "f3" -> "abc")))
  }
  it should "match f1.f3=ab" in {
    shouldMatch("f1.f3=ab", EventFrame("tags" -> Seq("abc", "123"), "f1" -> EventFrame("f2" -> 123, "f3" -> "abc")))
  }
  it should "match f1/f3=ab" in {
    shouldMatch("f1/f3=ab", EventFrame("tags" -> Seq("abc", "123"), "f1" -> EventFrame("f2" -> 123, "f3" -> "abc")))
  }
  it should "match #abc=abc and f1.f3=ab" in {
    shouldMatch("#abc=abc and f1.f3=ab", EventFrame("tags" -> Seq("abc", "123"), "f1" -> EventFrame("f2" -> 123, "f3" -> "abc")))
  }
  it should "match #abc=abc or f1.f3=ac" in {
    shouldMatch("#abc=abc or f1.f3=ac", EventFrame("tags" -> Seq("abc", "123"), "f1" -> EventFrame("f2" -> 123, "f3" -> "abc")))
  }
  it should "match #xyz=xyz or f1.f3=ab" in {
    shouldMatch("#xyz=xyz or f1.f3=ab", EventFrame("tags" -> Seq("abc", "123"), "f1" -> EventFrame("f2" -> 123, "f3" -> "abc")))
  }
  it should "match #abc=abc and f1.f3=ab and f1.f3=ab" in {
    shouldMatch("#abc=abc and f1.f3=ab and f1.f3=ab", EventFrame("tags" -> Seq("abc", "123"), "f1" -> EventFrame("f2" -> 123, "f3" -> "abc")))
  }
  it should "match #abc=abc and f1.f3=ab and f1.f2=123" in {
    shouldMatch("#abc=abc and f1.f3=ab and f1.f2=123", EventFrame("tags" -> Seq("abc", "123"), "f1" -> EventFrame("f2" -> 123, "f3" -> "abc")))
  }
  it should "match #abc=abc and f1.f3=ab and f1.f2>1" in {
    shouldMatch("#abc=abc and f1.f3=ab and f1.f2>1", EventFrame("tags" -> Seq("abc", "123"), "f1" -> EventFrame("f2" -> 123, "f3" -> "abc")))
  }
  it should "match #abc=abc and f1.f3!=x and f1.f2>1" in {
    shouldMatch("#abc=abc and f1.f3!=x and f1.f2>1", EventFrame("tags" -> Seq("abc", "123"), "f1" -> EventFrame("f2" -> 123, "f3" -> "abc")))
  }
  it should "match #abc=abc and f1.f3=ab and f1.f2>1 or f1.f3=x" in {
    shouldMatch("#abc=abc and f1.f3=ab and f1.f2>1 or f1.f3=x", EventFrame("tags" -> Seq("abc", "123"), "f1" -> EventFrame("f2" -> 123, "f3" -> "abc")))
  }
  it should "match #abc=abc and f1.f3!=ab and f1.f2>1 or f1.f3=c" in {
    shouldMatch("#abc=abc and f1.f3!=ab and f1.f2>1 or f1.f3=c", EventFrame("tags" -> Seq("abc", "123"), "f1" -> EventFrame("f2" -> 123, "f3" -> "abc")))
  }
  it should "match #abc=abc and f1.f3!=ab and f1.f2>1 or f1.f3=c and f1.f2>2" in {
    shouldMatch("#abc=abc and f1.f3!=ab and f1.f2>1 or f1.f3=c and f1.f2>2", EventFrame("tags" -> Seq("abc", "123"), "f1" -> EventFrame("f2" -> 123, "f3" -> "abc")))
  }

  it should "not match #abc=abc and f1.f3=ac" in {
    shouldNotMatch("#abc=abc and f1.f3=ac", EventFrame("tags" -> Seq("abc", "123"), "f1" -> EventFrame("f2" -> 123, "f3" -> "abc")))
  }
  it should "not match #abc!=abc or f1.f3=ac" in {
    shouldNotMatch("#abc!=abc or f1.f3=ac", EventFrame("tags" -> Seq("abc", "123"), "f1" -> EventFrame("f2" -> 123, "f3" -> "abc")))
  }
  it should "not match #xyz=xyz or f1.f3=ax" in {
    shouldNotMatch("#xyz=xyz or f1.f3=ax", EventFrame("tags" -> Seq("abc", "123"), "f1" -> EventFrame("f2" -> 123, "f3" -> "abc")))
  }
  it should "not match #abc=abc and f1.f3=ab and f1.f3=ac" in {
    shouldNotMatch("#abc=abc and f1.f3=ab and f1.f3=ac", EventFrame("tags" -> Seq("abc", "123"), "f1" -> EventFrame("f2" -> 123, "f3" -> "abc")))
  }
  it should "not match #abc=abc and f1.f3=ab and f1.f2=123.1" in {
    shouldNotMatch("#abc=abc and f1.f3=ab and f1.f2=123.1", EventFrame("tags" -> Seq("abc", "123"), "f1" -> EventFrame("f2" -> 123, "f3" -> "abc")))
  }
  it should "not match #abc=abc and f1.f3=ab and f1.f2<1" in {
    shouldNotMatch("#abc=abc and f1.f3=ab and f1.f2<1", EventFrame("tags" -> Seq("abc", "123"), "f1" -> EventFrame("f2" -> 123, "f3" -> "abc")))
  }
  it should "not match #abc=abc and f1.f3=x and f1.f2>1" in {
    shouldNotMatch("#abc=abc and f1.f3=x and f1.f2>1", EventFrame("tags" -> Seq("abc", "123"), "f1" -> EventFrame("f2" -> 123, "f3" -> "abc")))
  }
  it should "not match #abc=abc and f1.f3=ac and f1.f2>1 or f1.f3=x" in {
    shouldNotMatch("#abc=abc and f1.f3=ac and f1.f2>1 or f1.f3=x", EventFrame("tags" -> Seq("abc", "123"), "f1" -> EventFrame("f2" -> 123, "f3" -> "abc")))
  }
  it should "not match #abc=abc and f1.f3!=ab and f1.f2>1 or f1.f3=z" in {
    shouldNotMatch("#abc=abc and f1.f3!=ab and f1.f2>1 or f1.f3=z", EventFrame("tags" -> Seq("abc", "123"), "f1" -> EventFrame("f2" -> 123, "f3" -> "abc")))
  }
  it should "not match #abc=abc and f1.f3!=ab and f1.f2>1 or f1.f3=c and f1.f2>200" in {
    shouldNotMatch("#abc=abc and f1.f3!=ab and f1.f2>1 or f1.f3=c and f1.f2>200", EventFrame("tags" -> Seq("abc", "123"), "f1" -> EventFrame("f2" -> 123, "f3" -> "abc")))
  }
  it should "not match #abc = abc and   f1.f3!=ab    and       f1.f2 >  1    or  f1.f3= c and       f1.f2  >   200" in {
    shouldNotMatch("#abc = abc and   f1.f3!=ab    and       f1.f2 >  1    or  f1.f3= c and       f1.f2  >   200", EventFrame("tags" -> Seq("abc", "123"), "f1" -> EventFrame("f2" -> 123, "f3" -> "abc")))
  }
  it should "match #abc = abc and   f1.f3 != ab  and       f1.f2 >  1    or  f1.f3= c and       f1.f2 >    2" in {
    shouldMatch("#abc = abc and   f1.f3 != ab  and       f1.f2 >  1    or  f1.f3= c and       f1.f2 >    2", EventFrame("tags" -> Seq("abc", "123"), "f1" -> EventFrame("f2" -> 123, "f3" -> "abc")))
  }

}
