package eventstreams

import eventstreams.core.Tools.configHelper
import eventstreams.core._
import eventstreams.support.TestHelpers
import org.joda.time.format.DateTimeFormat
import play.api.libs.json._


class ToolsTest extends TestHelpers {

  "~> abc (extract string)" should "be None for blank input" in {
    Json.obj() ~> 'abc should be (None)
  }
  it should "be None for empty field" in {
    Json.obj("abc" -> "") ~> 'abc should be (None)
  }
  it should "be None for field with only spaces" in {
    Json.obj("abc" -> "   ") ~> 'abc should be (None)
  }
  it should "be None for field with numeric value" in {
    Json.obj("abc" -> 1) ~> 'abc should be (None)
  }
  it should "be None for field with bool value" in {
    Json.obj("abc" -> true) ~> 'abc should be (None)
  }
  it should "be None for field with object" in {
    Json.obj("abc" -> Json.obj("a"->1)) ~> 'abc should be (None)
  }
  it should "be a value otherwise" in {
    Json.obj("abc" -> "value123") ~> 'abc should be (Some("value123"))
  }
  it should "not be trimmed" in {
    Json.obj("abc" -> "  value123  ") ~> 'abc should be (Some("  value123  "))
  }
  it should "should work for both strings and symbols" in {
    Json.obj("abc" -> "  value123  ") ~> "abc" should be (Some("  value123  "))
  }

  "~*> abc (extract exact string)" should "be None for blank input" in {
    Json.obj() ~*> 'abc should be (None)
  }
  it should "be some value for empty field" in {
    Json.obj("abc" -> "") ~*> 'abc should be (Some(""))
  }
  it should "be untrimmed value for field with only spaces" in {
    Json.obj("abc" -> "   ") ~*> 'abc should be (Some("   "))
  }
  it should "be None for field with numeric value" in {
    Json.obj("abc" -> 1) ~*> 'abc should be (None)
  }
  it should "be None for field with bool value" in {
    Json.obj("abc" -> true) ~*> 'abc should be (None)
  }
  it should "be None for field with object" in {
    Json.obj("abc" -> Json.obj("a"->1)) ~*> 'abc should be (None)
  }
  it should "be a value otherwise" in {
    Json.obj("abc" -> "value123") ~*> 'abc should be (Some("value123"))
  }
  it should "not be trimmed" in {
    Json.obj("abc" -> "  value123  ") ~*> 'abc should be (Some("  value123  "))
  }
  it should "it should work for both string and symbols" in {
    Json.obj("abc" -> "  value123  ") ~*> "abc" should be (Some("  value123  "))
  }


  "#> abc (extract object)" should "be None for blank input" in {
    Json.obj() #> 'abc should be (None)
  }
  it should "be JsString(\"\") value for empty field" in {
    Json.obj("abc" -> "") #> 'abc should be (Some(JsString("")))
  }
  it should "be JsString(\"   \") for field with only spaces" in {
    Json.obj("abc" -> "   ") #> 'abc should be (Some(JsString("   ")))
  }
  it should "be JsNumber() for field with numeric value" in {
    Json.obj("abc" -> 1) #> 'abc should be (Some(JsNumber(1)))
  }
  it should "be JsBoolean() for field with bool value" in {
    Json.obj("abc" -> true) #> 'abc should be (Some(JsBoolean(value = true)))
  }
  it should "be the object for field with object" in {
    Json.obj("abc" -> Json.obj("a"->1)) #> 'abc should be (Some(Json.obj("a"->1)))
  }
  it should "it should work for string and symbols" in {
    Json.obj("abc" -> Json.obj("a"->1)) #> "abc" should be (Some(Json.obj("a"->1)))
  }

  "+> abc (extract int)" should "be None for blank input" in {
    Json.obj() +> 'abc should be (None)
  }
  it should "be None for string" in {
    Json.obj("abc" -> "") +> 'abc should be (None)
  }
  it should "be None for field with bool value" in {
    Json.obj("abc" -> true) +> 'abc should be (None)
  }
  it should "be None for field with object" in {
    Json.obj("abc" -> Json.obj("a"->1)) +> 'abc should be (None)
  }
  it should "be a value otherwise" in {
    Json.obj("abc" -> 123) +> 'abc should be (Some(123))
  }
  it should "work with ints only" in {
    Json.obj("abc" -> 123.12) +> 'abc should be (Some(123))
  }
  it should "work for both strings and symbols" in {
    Json.obj("abc" -> 123.12) +> "abc" should be (Some(123))
  }
  it should "not support longs" in {
    Json.obj("abc" -> 98765432198765L) +> 'abc should be (Some(-1635740051))
  }

  "+&> abc (extract double)" should "be None for blank input" in {
    Json.obj() +&> 'abc should be (None)
  }
  it should "be None for string" in {
    Json.obj("abc" -> "") +&> 'abc should be (None)
  }
  it should "be None for field with bool value" in {
    Json.obj("abc" -> true) +&> 'abc should be (None)
  }
  it should "be None for field with object" in {
    Json.obj("abc" -> Json.obj("a"->1)) +&> 'abc should be (None)
  }
  it should "be a value otherwise" in {
    Json.obj("abc" -> 123) +&> 'abc should be (Some(123))
  }
  it should "work with decimals only" in {
    Json.obj("abc" -> 123.12) +&> 'abc should be (Some(123.12))
  }
  it should "work for both strings and symbols" in {
    Json.obj("abc" -> 123.12) +&> "abc" should be (Some(123.12))
  }

  "++> abc (extract long)" should "be None for blank input" in {
    Json.obj() ++> 'abc should be (None)
  }
  it should "be None for string" in {
    Json.obj("abc" -> "") ++> 'abc should be (None)
  }
  it should "be None for field with bool value" in {
    Json.obj("abc" -> true) ++> 'abc should be (None)
  }
  it should "be None for field with object" in {
    Json.obj("abc" -> Json.obj("a"->1)) ++> 'abc should be (None)
  }
  it should "be a value otherwise" in {
    Json.obj("abc" -> 123) ++> 'abc should be (Some(123))
  }
  it should "work with ints only" in {
    Json.obj("abc" -> 123.12) ++> 'abc should be (Some(123))
  }
  it should "work for both strings and symbols" in {
    Json.obj("abc" -> 123.12) ++> "abc" should be (Some(123))
  }
  it should "support longs" in {
    Json.obj("abc" -> 98765432198765L) ++> 'abc should be (Some(98765432198765L))
  }

  "?> abc (extract boolean)" should "be None for blank input" in {
    Json.obj() ?> 'abc should be (None)
  }
  it should "be None for empty field" in {
    Json.obj("abc" -> "") ?> 'abc should be (None)
  }
  it should "be None for field with only spaces" in {
    Json.obj("abc" -> "   ") ?> 'abc should be (None)
  }
  it should "be None for field with numeric value" in {
    Json.obj("abc" -> 1) ?> 'abc should be (None)
  }
  it should "be None for field with string value" in {
    Json.obj("abc" -> "true") ?> 'abc should be (None)
  }
  it should "be None for field with object" in {
    Json.obj("abc" -> Json.obj("a"->1)) ?> 'abc should be (None)
  }
  it should "be a value otherwise" in {
    Json.obj("abc" -> true) ?> 'abc should be (Some(true))
  }
  it should "should work for both strings and symbols" in {
    Json.obj("abc" -> true) ?> "abc" should be (Some(true))
  }

  "##> abc (extract array)" should "be None for blank input" in {
    Json.obj() ##> 'abc should be (None)
  }
  it should "be None for empty field" in {
    Json.obj("abc" -> "") ##> 'abc should be (None)
  }
  it should "be None for field with only spaces" in {
    Json.obj("abc" -> "   ") ##> 'abc should be (None)
  }
  it should "be None for field with numeric value" in {
    Json.obj("abc" -> 1) ##> 'abc should be (None)
  }
  it should "be None for field with string value" in {
    Json.obj("abc" -> "true") ##> 'abc should be (None)
  }
  it should "be None for field with bool value" in {
    Json.obj("abc" -> true) ##> 'abc should be (None)
  }
  it should "be None for field with object" in {
    Json.obj("abc" -> Json.obj("a"->1)) ##> 'abc should be (None)
  }
  it should "be a value otherwise" in {
    Json.obj("abc" -> Json.arr()) ##> 'abc should be (Some(List()))
  }
  it should "return array containing values" in {
    Json.obj("abc" -> Json.arr("1",2)) ##> 'abc should be (Some(List(JsString("1"), JsNumber(2))))
  }
  it should "should work for both strings and symbols" in {
    Json.obj("abc" -> Json.arr()) ##> "abc" should be (Some(List()))
  }

  "toPath" should "work for p1" in {
    Tools.toPath("p1") should be (__ \ 'p1)
  }
  it should "work for p1.p2" in {
    Tools.toPath("p1.p2") should be (__ \ 'p1 \ 'p2)
  }
  it should "work for p1.p2.p3" in {
    Tools.toPath("p1.p2.p3") should be (__ \ 'p1 \ 'p2 \ 'p3)
  }
  it should "work for p1/p2.p3" in {
    Tools.toPath("p1/p2.p3") should be (__ \ 'p1 \ 'p2 \ 'p3)
  }
  it should "work for p1/p2/p3" in {
    Tools.toPath("p1/p2/p3") should be (__ \ 'p1 \ 'p2 \ 'p3)
  }
  it should "work for p1(1)" in {
    Tools.toPath("p1(1)") should be ((__ \ 'p1)(1))
  }
  it should "work for p1(1).p2" in {
    Tools.toPath("p1(1).p2") should be ((__ \ 'p1)(1) \ 'p2)
  }
  it should "work for p1(2).p2(1).p3" in {
    Tools.toPath("p1(2).p2(1).p3") should be (((__ \ 'p1)(2) \ 'p2)(1) \ 'p3)
  }
  it should "work for p1(1)/p2(0).p3(3)" in {
    Tools.toPath("p1(1)/p2(0).p3(3)") should be ((((__ \ 'p1)(1) \ 'p2)(0) \ 'p3)(3))
  }
  it should "work for p1/p2/p3(1)" in {
    Tools.toPath("p1/p2/p3(3)") should be ((__ \ 'p1 \ 'p2 \ 'p3)(3))
  }
  it should "work for p1 (1)/p2( 0).p3 ( 3 ) " in {
    Tools.toPath("p1 (1)/p2( 0).p3 ( 3 ) ") should be ((((__ \ 'p1)(1) \ 'p2)(0) \ 'p3)(3))
  }



  val testDate = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").parseDateTime("2014-02-03 23:10:11")
  val testInput = EventFrame("date_ts" -> testDate.getMillis, "f1"->"abc", "f2"->true,"f3"->123.1,"f4"->Seq("a1","a2"),"f5"->Map("z1"->"xyz"))
  val testFrame = testInput

  s"macroReplacement for input $testInput" should "resolve mnb into mnb" in {
    Tools.macroReplacement(testFrame,"mnb") should be ("mnb")
  }
  it should "resolve ${f1} into abc" in {
    Tools.macroReplacement(testFrame,"${f1}") should be ("abc")
  }
  it should "resolve ${f1} into abc if we use overloaded method" in {
    Tools.macroReplacement(testInput, Map[String,String](), "${f1}") should be ("abc")
  }
  it should "resolve ${f1} into abc if we use overloaded method with JsString as param" in {
    Tools.macroReplacement(testInput, Map[String,String](), "${f1}") should be ("abc")
  }
  it should "resolve ${fx} into abc1 if we provide value in the context" in {
    Tools.macroReplacement(testInput, Map[String,String]("fx" -> "abc1"), "${fx}") should be ("abc1")
  }

  it should "resolve ${f1}_x into abc_x" in {
    Tools.macroReplacement(testFrame,"${f1}_x") should be ("abc_x")
  }
  it should "resolve x_${f1} into x_abc" in {
    Tools.macroReplacement(testFrame,"x_${f1}") should be ("x_abc")
  }
  it should "resolve x_${fz} into x_" in {
    Tools.macroReplacement(testFrame,"x_${fz}") should be ("x_")
  }
  it should "resolve x_${f2} into x_true" in {
    Tools.macroReplacement(testFrame,"x_${f2}") should be ("x_true")
  }
  it should "resolve x_${f2}${f1} into x_trueabc" in {
    Tools.macroReplacement(testFrame,"x_${f2}${f1}") should be ("x_trueabc")
  }
  it should "resolve x_${f2.f1}${f1.f2} into x_" in {
    Tools.macroReplacement(testFrame,"x_${f2.f1}${f1.f2}") should be ("x_")
  }
  it should "resolve x_${f3} into x_123.1" in {
    Tools.macroReplacement(testFrame,"x_${f3}") should be ("x_123.1")
  }
  it should "resolve x_${f4} into x_\"a1\",\"a2\"" in {
    Tools.macroReplacement(testFrame,"x_${f4}") should be ("x_\"a1\",\"a2\"")
  }
  it should "resolve x_${f5.z1} into x_xyz" in {
    Tools.macroReplacement(testFrame,"x_${f5.z1}") should be ("x_xyz")
  }
  it should "resolve x_${f5/z1} into x_xyz" in {
    Tools.macroReplacement(testFrame,"x_${f5/z1}") should be ("x_xyz")
  }

  val expectedNow = DateTimeFormat.forPattern("yyyy-MMM").print(System.currentTimeMillis())
  val expectedEvt = DateTimeFormat.forPattern("yyyy-MMM-dd_HH:mm:ss").print(testDate)
  it should "resolve now:${now:yyyy-MMM} into "+expectedNow in {
    Tools.macroReplacement(testFrame,"now:${now:yyyy-MMM}") should be (s"now:$expectedNow")
  }
  it should "resolve evt:${eventts:yyyy-MMM-dd_HH:mm:ss} into "+expectedEvt in {
    Tools.macroReplacement(testFrame,"evt:${eventts:yyyy-MMM-dd_HH:mm:ss}") should be (s"evt:$expectedEvt")
  }

  val testInputNoDateTs = EventFrame("f1"->"abc", "f2"->true,"f3"->123.1,"f4"->Seq("a1","a2"),"f5"->Map("z1"->"xyz"))
  val expectedEvtBasedOnNow = DateTimeFormat.forPattern("yyyy-MMM-dd_HH").print(System.currentTimeMillis())

  s"macroReplacement for input $testInputNoDateTs" should "resolve evt:${eventts:yyyy-MMM-dd_HH} into "+expectedEvtBasedOnNow in {
    Tools.macroReplacement(testInputNoDateTs, Map[String,String](),"evt:${eventts:yyyy-MMM-dd_HH}") should be (s"evt:$expectedEvtBasedOnNow")
  }

  s"setValue(s,JsString(qwe)...)for input $testInputNoDateTs " should "set string into path" in {
    Tools.setValue("s","qwe",'path, testInputNoDateTs) ~> "path" should be (Some("qwe"))
  }
  it should "set string into f1" in {
    Tools.setValue("s","qwe",'f1, testInputNoDateTs) ~> "f1" should be (Some("qwe"))
  }
  it should "set string into f2" in {
    Tools.setValue("s","qwe",'f2, testInputNoDateTs) ~> "f2" should be (Some("qwe"))
  }
  it should "set string into f3" in {
    Tools.setValue("s","qwe",'f3, testInputNoDateTs) ~> "f3" should be (Some("qwe"))
  }
  it should "set string into f4" in {
    Tools.setValue("s","qwe",'f4, testInputNoDateTs) ~> "f4" should be (Some("qwe"))
  }

  s"setValue(n,JsString(qwe)...)for input $testInputNoDateTs " should "set string or num into path" in {
    Tools.setValue("n","qwe",'path, testInputNoDateTs) ~> "path" should be (Some("0"))
    Tools.setValue("n","qwe",'path, testInputNoDateTs) +> "path" should be (Some(0))
  }

  s"setValue(n,JsString(12)...)for input $testInputNoDateTs " should "set number into path" in {
    Tools.setValue("n","12",'path, testInputNoDateTs) +> "path" should be (Some(12))
  }
  it should "set number into f1" in {
    Tools.setValue("n","12",'f1, testInputNoDateTs) +> "f1" should be (Some(12))
  }
  it should "set number into f2" in {
    Tools.setValue("n","12",'f2, testInputNoDateTs) +> "f2" should be (Some(12))
  }
  it should "set number into f3" in {
    Tools.setValue("n","12",'f3, testInputNoDateTs) +> "f3" should be (Some(12))
  }
  it should "set number into f4" in {
    Tools.setValue("n","12",'f4, testInputNoDateTs) +> "f4" should be (Some(12))
  }

  s"setValue(n,12...)for input $testInputNoDateTs " should "set number into path" in {
    Tools.setValue("n","12",'path, testInputNoDateTs) +> "path" should be (Some(12))
  }
  it should "set number into f1" in {
    Tools.setValue("n","12",'f1, testInputNoDateTs) +> "f1" should be (Some(12))
  }
  it should "set number into f2" in {
    Tools.setValue("n","12",'f2, testInputNoDateTs) +> "f2" should be (Some(12))
  }
  it should "set number into f3" in {
    Tools.setValue("n","12",'f3, testInputNoDateTs) +> "f3" should be (Some(12))
  }
  it should "set number into f4" in {
    Tools.setValue("n","12",'f4, testInputNoDateTs) +> "f4" should be (Some(12))
  }
  s"setValue(s,12...)for input $testInputNoDateTs " should "set string into path" in {
    Tools.setValue("s","12",'path, testInputNoDateTs) ~> "path" should be (Some("12"))
  }
  it should "set string into f1" in {
    Tools.setValue("s","12",'f1, testInputNoDateTs) ~> "f1" should be (Some("12"))
  }
  it should "set string into f2" in {
    Tools.setValue("s","12",'f2, testInputNoDateTs) ~> "f2" should be (Some("12"))
  }
  it should "set string into f3" in {
    Tools.setValue("s","12",'f3, testInputNoDateTs) ~> "f3" should be (Some("12"))
  }
  it should "set string into f4" in {
    Tools.setValue("s","12",'f4, testInputNoDateTs) ~> "f4" should be (Some("12"))
  }

  s"setValue(b,JsString(true)...)for input $testInputNoDateTs " should "set bool into path" in {
    Tools.setValue("b","true",'path, testInputNoDateTs) ?> "path" should be (Some(true))
  }
  it should "set bool into f1" in {
    Tools.setValue("b","true",'f1, testInputNoDateTs) ?> "f1" should be (Some(true))
  }
  it should "set bool into f2" in {
    Tools.setValue("b","true",'f2, testInputNoDateTs) ?> "f2" should be (Some(true))
  }
  it should "set bool into f3" in {
    Tools.setValue("b","true",'f3, testInputNoDateTs) ?> "f3" should be (Some(true))
  }
  it should "set bool into f4" in {
    Tools.setValue("b","true",'f4, testInputNoDateTs) ?> "f4" should be (Some(true))
  }

  s"setValue(as,JsString(qwe)...)for input $testInputNoDateTs " should "set string arr into path" in {
    Tools.setValue("as","qwe",'path, testInputNoDateTs) ##> "path" should be (Some(List(EventDataValueString("qwe"))))
  }
  it should "set string into f1" in {
    Tools.setValue("as","qwe",'f1, testInputNoDateTs) ##> "f1" should be (Some(List(EventDataValueString("abc"),EventDataValueString("qwe"))))
  }
  it should "set string into f2" in {
    Tools.setValue("as","qwe",'f2, testInputNoDateTs) ##> "f2" should be (Some(List(EventDataValueBoolean(true),EventDataValueString("qwe"))))
  }
  it should "set string into f3" in {
    Tools.setValue("as","qwe",'f3, testInputNoDateTs) ##> "f3" should be (Some(List(EventDataValueNumber(123.1),EventDataValueString("qwe"))))
  }
  it should "set string into f4" in {
    Tools.setValue("as","qwe",'f4, testInputNoDateTs) ##> "f4" should be (Some(List(EventDataValueString("a1"),EventDataValueString("a2"),EventDataValueString("qwe"))))
  }

  "Utils.generateShortUUID" should "generate a random short uuid" in {
    Utils.generateShortUUID should not be Utils.generateShortUUID
  }
  it should "do it consistently" in {
    (1 to 10000).foreach { _ =>
      Utils.generateShortUUID should not be Utils.generateShortUUID
    }
  }
  it should "be a string" in {
    Utils.generateShortUUID shouldBe a [String]
  }
  it should "be longer than 10 symbols" in {
    Utils.generateShortUUID.length should be > 10
  }

}