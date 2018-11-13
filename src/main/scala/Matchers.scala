//@
package xyz.hyperreal.matcher

import scala.collection.mutable.{HashMap, ListBuffer}

/**
  * Provides methods for coding character pattern matchers.
  *
  * @tparam Input the type of [[Reader]] that will be used
  */
class Matchers[Input <: Reader] {

  private val groupmap = new HashMap[String, (Input, Input)]

  /**
    * Abstract class for [[Matcher]] results
    *
    * @tparam R type of result value
    */
  abstract class MatcherResult[+R] {
    /**
      * Next character of input.
      */
    val next: Input

    /**
      * Applies of function to matcher result value.
      *
      * @param f funciton to be applied to result value
      * @tparam S type of new result value
      * @return new matcher result
      */
    def map[S]( f: R => S ): MatcherResult[S]
  }

  /**
    * Represents a successful match.
    *
    * @param result result value of the match
    * @param next next character of input
    * @tparam R type of result value
    */
  case class Match[R]( result: R, next: Input ) extends MatcherResult[R] {
    def map[S]( f: R => S ) = Match( f(result), next )
  }

  /**
    * Represents an unsuccessful match.
    *
    * @param next character at which the mismatch occurred
    */
  case class Mismatch( next: Input ) extends MatcherResult {
    def map[S]( f: Nothing => S ) = this
  }

  /**
    * Abstract matcher.  A matcher is a function that maps character input to a result value.
    *
    * @tparam R type of result value
    */
  abstract class Matcher[+R] extends (Input => MatcherResult[R]) {

    /**
      * Returns a new matcher whose result value is transformed by a function.
      *
      * @param f function applied to the result value of this matcher
      * @tparam S type of the result value of the matcher being returned
      * @return a new matcher with a mapped result value
      */
    def map[S]( f: R => S ): Matcher[S] = this( _ ) map f

    /**
      * Returns a matcher that applies this matcher and another one sequentially and whose result value is a tuple containing the result values of the two matchers.
      *
      * @param m the second matcher to be applied only if this matcher succeeds
      * @tparam S the type of the result value of the second matcher
      * @return the new sequential matcher
      */
    def ~ [S]( m: => Matcher[S] ): Matcher[R ~ S] = { in =>
      this( in ) match {
        case Match( a, in1 ) =>
          m( in1 ) match {
            case Match( b, in2 ) => Match( new ~(a, b), in2 )
            case f => f.asInstanceOf[Mismatch]
          }
        case f => f.asInstanceOf[Mismatch]
      }
    }

    /**
      * Returns a matcher that applies this matcher and another one sequentially and whose result value is that of this matcher.
      *
      * @param m the second matcher
      * @tparam S the type of the result value of the second matcher
      * @return the new matcher
      */
    def <~ [S]( m: => Matcher[S] ) = (this ~ m) ^^ { case a ~ _ => a }

    /**
      * Returns a matcher that applies this matcher and another one sequentially and whose result value is that of the second matcher.
      *
      * @param m the second matcher
      * @tparam S the type of the result value of the second matcher
      * @return the new matcher
      */
    def ~> [S]( m: => Matcher[S] ) = (this ~ m) ^^ { case _ ~ b => b }

    /**
      * Returns a matcher that applies this matcher or alternatively another matcher if this matcher fails.
      *
      * @param m the alternate matcher
      * @tparam S the type of the result value of the alternate
      * @return the new matcher
      */
    def | [S >: R]( m: => Matcher[S] ): Matcher[S] = { in =>
      this( in ) match {
        case res: Match[R] => res
        case _ => m( in )
      }
    }

    /**
      * Returns a matcher whose result value is transformed by a function.
      *
      * @param f the function applied to the result value
      * @tparam S the type of the transformed result value
      */
    def ^^ [S]( f: R => S ) = map( f )

    def ^^^ [S]( v: => S ) = map (_ => v)

    def * = rep(this)

    def + = rep1(this)

    def ? = opt(this)

    def pos: Matcher[Input] = { in => Match( in, in ) }

  }

  /**
    * Clears capture groups.
    */
  def clear = groupmap.clear

  def capture[S]( name: String, m: => Matcher[S] ): Matcher[S] = { in =>
    m( in ) match {
      case res@Match( _, next ) =>
        groupmap(name) = (in, next)
        res
      case res => res
    }
  }

  def matched[S]( m: => Matcher[S] ): Matcher[(Input, Input)] = { in =>
    m( in ) match {
      case Match( _, next ) => Match( (in, next), next )
      case res => res.asInstanceOf[Mismatch]
    }
  }

  def string[S]( m: => Matcher[S] ) = matched(m) ^^ { case (s, e) => s substring e }

  /**
    * Returns capture group.
    *
    * @param name the name of the capture group to return
    * @return a capture group with is a pair of input objects: the first is the first character in the group, the second is the next input character after the end of the group.
    */
  def group( name: String ) = groupmap get name

  /**
    * Returns the substring from a capture group.
    *
    * @param name the name of the capture group
    */
  def substring( name: String ) =
    groupmap get name map { case (start, end) => start substring end }

  def rep1[S]( m: => Matcher[S] ) = m ~ rep(m) ^^ { case f ~ r => f :: r }

  def rep[S]( m: => Matcher[S] ): Matcher[List[S]] = { in => lazy val m1 = m
    val buf = new ListBuffer[S]
    val m2 = m1

    def rep( in1: Input ): MatcherResult[List[S]] =
      m2( in1 ) match {
        case Match( v, r ) =>
          buf += v
          rep( r )
        case Mismatch( _ ) => Match( buf.toList, in1 )
      }

      rep( in )
  }

  def rep1sep[T, U]( m: => Matcher[T], sep: => Matcher[U] ) = {
    val m1 = m

    m1 ~ rep(sep ~> m1) ^^ { case r ~ rs => r :: rs } | succeed( Nil )
  }

  def repsep[T, U]( m: => Matcher[T], sep: => Matcher[U] ) =
    opt(rep1sep( m, sep )) ^^ {
      case None => Nil
      case Some( l ) => l
    }

  /**
    * Returns a matcher that will match any of a list of characters.
    *
    * @param cs argument list of characters
    * @return a matcher that only matches a character from a list
    */
  def anyOf( cs: Char* ) = cls( cs contains _ )

  /**
    * Returns a matcher that will matcher any character not in a list of characters.
    *
    * @param cs argument list of characters
    * @return a matcher that only matches a character not on a list
    */
  def noneOf( cs: Char* ) = cls( !cs.contains(_) )

  /**
    * Returns a matcher that allows a matcher to succeed optionally.
    *
    * @param m the matcher to apply to the input.
    * @tparam S the type of the optional result value
    * @return a matcher with an optional result value
    */
  def opt[S]( m: => Matcher[S] ) = m ^^ (Some( _ )) | succeed( None )

  /**
    * Returns a matcher that negates the result of the given matcher. No input is consumed.
    *
    * @param m the matcher whose result is negated
    * @tparam S the type of the result value of the given matcher
    * @return the new matcher
    */
  def not[S]( m: Matcher[S] ): Matcher[Unit] = { in =>
    m( in ) match {
      case Match( _, _ ) => Mismatch( in )
      case Mismatch( _ ) => Match( (), in )
    }
  }

  /**
    * Returns a matcher whose result is the same as the given matcher, but without consuming any input.
    *
    * @param m the given matcher
    * @tparam S the type of the result value
    * @return the new matcher
    */
  def guard[S]( m: => Matcher[S] ): Matcher[S] = { in =>
    m( in ) match {
      case Match( r, _ ) => Match( r, in )
      case f => f
    }
  }

  /**
    * Returns a zero-length matcher that succeeds at the start of input.
    *
    * @return a matcher that succeeds at the start of input, fails otherwise.
    */
  def soi: Matcher[Unit] =
    in =>
      if (in.soi)
        Match( (), in )
      else
        Mismatch( in )

  /**
    * Returns a zero-length matcher that succeeds at the end of input.
    *
    * @return a matcher that succeeds at the end of input, fails otherwise.
    */
  def eoi: Matcher[Unit] =
    in =>
      if (in.eoi)
        Match( (), in )
      else
        Mismatch( in )

  /**
    * Returns a matcher that always succeeds.
    *
    * @param r the result value
    * @tparam R the type of result value
    * @return a matcher that always succeeds with a result value
    */
  def succeed[R]( r: => R ): Matcher[R] = Match( r, _ )

  /**
    * Returns a matcher that always fails.
    *
    * @return a matcher that always fails with a result containing the current point in the input stream
    */
  def fail: Matcher[Nothing] = Mismatch( _ )

  /**
    * Returns a matcher that matches the current input character if it is a member of a class of characters.
    *
    * @param pred predicate that determines if the current input character matches
    * @return a matcher for matching character classes
    */
  def cls( pred: Char => Boolean ): Matcher[Char] = { in =>
    if (in.more && pred( in.ch ))
      Match( in.ch, in.next.asInstanceOf[Input] )
    else
      Mismatch( in )
  }

  /**
    * Returns a matcher that always succeeds as long as there is input remaining.
    *
    * @return a matcher with the next input character as its result value, failing if there is no more input
    */
  def char = cls( _ => true )

  /**
    * Returns a matcher for a specific character. This combinator is an implicit function to that character literals can be lifted to the corresponding character matcher.
    *
    * For example
    *
    * {{{
    *   def stringLit: Matcher[List[Char]] = '"' ~> rep(noneOf('"')) <~ '"'
    * }}}
    *
    * @param c the character to be matched
    * @return the character matcher
    */
  implicit def ch( c: Char ): Matcher[Char] = cls( _ == c )

  /**
    * Returns a matcher to match against a string. This combinator is an implicit function to that string literals can be lifted to the corresponding string matcher.
    *
    * For example
    *
    * {{{
    *   def bracketed: Matcher[List[Char]] = "[[" ~> rep(not("]]") ~> char) <~ "]]"
    * }}}
    *
    * @param s the string to match
    * @return a matcher that matches against a string, with that string as its result value if it succeeds
    */
  implicit def str( s: String ): Matcher[String] = { in =>
    def str( idx: Int, in1: Input ): MatcherResult[String] =
      if (idx < s.length)
        if (in1.more && s.charAt( idx ) == in1.ch)
          str( idx + 1, in1.next.asInstanceOf[Input] )
        else
          Mismatch( in1 )
      else
        Match( s, in1 )

    str( 0, in )
  }

  /**
    * Case class for return sequence matcher results that can be pattern matched.
    *
    * @param a left result value
    * @param b right result value
    * @tparam A type of left result value
    * @tparam B type of right result value
    */
  case class ~[+A, +B]( a: A, b: B )

  private val HEXDIGITSET = ('a' to 'f') ++ ('A' to 'F') ++ ('0' to '9') toSet

  /**
    * Returns a hex digit character matcher.
    */
  def hexdigit: Matcher[Char] = cls( HEXDIGITSET )

  /**
    * Returns a letter or digit character matcher.
    */
  def letterOrDigit: Matcher[Char] = cls( _.isLetterOrDigit )

  /**
    * Returns a letter character matcher.
    */
  def letter: Matcher[Char] = cls( _.isLetter )

  /**
    * Returns a lower case character matcher.
    */
  def lower: Matcher[Char] = cls( _.isLower )

  /**
    * Returns an upper case character matcher.
    */
  def upper: Matcher[Char] = cls( _.isUpper )

  /**
    * Returns a digit character matcher.
    */
  def digit: Matcher[Char] = cls( _.isDigit )

  /**
    * Returns a space character matcher.
    */
  def space: Matcher[Char] = cls( _.isSpaceChar )

  /**
    * Returns a zero-length matcher that succeeds if the previous input character is a member of a character class.
    *
    * @param pred predicate that determines inclusion in a character class
    */
  def lookbehind( pred: Char => Boolean ): Matcher[Char] = { in =>
    if (!in.soi && pred( in.prev ))
      Match( in.prev, in )
    else
      Mismatch( in )
  }

//  def boundary( start: Boolean, predb: Char => Boolean,
//                end: Boolean, predf: Char => Boolean ) =
//    (lookbehind( predb ) | (if (start) soi else fail)) ~ guard(cls(predf) | (if (end) eoi else fail))

}
