package xyz.hyperreal.pattern_matcher

import scala.collection.mutable.ArrayBuffer

import xyz.hyperreal.dllist.DLList


object Main extends App with Matchers[Reader] {
  //reserved += ()
  //delimiters += ()

  def input = matchall(singleStringLit)

  val s =
    """
      |'asdf'
    """.stripMargin

  input( Reader.fromString(s) ) match {
    case Match( result, _ ) => println( result )
    case m: Mismatch => m.error
  }

}


//  val matcher =
//    new Matchers[StringReader] {
//      delimiters += ("+", "-", "*", "/", "(", ")")
//
//      case class Precedence( precedence: Int, var first: rules.Node, var last: rules.Node )
//      case class Rule( operators: ArrayBuffer[String], var precedence: precedences.Node, name: String, parser: Matcher[Product] )
//
//      val precedences = new DLList[Precedence]
//      val rules = new DLList[Rule]
//
//      def yfx( prec: Int ) =
//
//      def below( prec: Int ) =
//
//      def addOperator( operator: String, precedence: Int, name: String, parser: Matcher[Product] ): Unit = {
//        def addPrecedence( after: precedences.Node ) = {
//          val prec = Precedence( precedence, null, null )
//
//          precedences += prec
//          prec
//        }
//
//        def addRule = {
//          val prec = Rule( precedence, null )
//
//          precedences += prec
//          prec
//        }
//
//        rules.find( _.precedence.element.precedence <= precedence ) match {
//          case None => addPrecedence( precedences.endSentinel.preceding )
//          case Some( rule ) =>
//            if (rule.precedence.element.precedence == precedence)
//
//        }
//
//      }
//
//      rules += Rule( new ArrayBuffer[String], , yfx )
//
//      def expression: Matcher[Product] = { in =>
//
//      }
//    }
//
//  println( matcher.expression(Reader.fromString("3")) )
