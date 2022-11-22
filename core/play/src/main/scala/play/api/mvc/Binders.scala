/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.mvc

import java.net.URLEncoder
import java.util.Optional
import java.util.OptionalDouble
import java.util.OptionalInt
import java.util.OptionalLong
import java.util.UUID
import scala.annotation.*

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

import reflect.ClassTag

/**
 * Binder for query string parameters.
 *
 * You can provide an implementation of `QueryStringBindable[A]` for any type `A` you want to be able to
 * bind directly from the request query string.
 *
 * For example, if you have the following type to encode pagination:
 *
 * {{{
 *   /**
 *    * @param index Current page index
 *    * @param size Number of items in a page
 *    */
 *   case class Pager(index: Int, size: Int)
 * }}}
 *
 * Play will create a `Pager(5, 42)` value from a query string looking like `/foo?p.index=5&p.size=42` if you define
 * an instance of `QueryStringBindable[Pager]` available in the implicit scope.
 *
 * For example:
 *
 * {{{
 *   object Pager {
 *     implicit def queryStringBinder(implicit intBinder: QueryStringBindable[Int]) = new QueryStringBindable[Pager] {
 *       override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Pager]] = {
 *         for {
 *           index <- intBinder.bind(key + ".index", params)
 *           size <- intBinder.bind(key + ".size", params)
 *         } yield {
 *           (index, size) match {
 *             case (Right(index), Right(size)) => Right(Pager(index, size))
 *             case _ => Left("Unable to bind a Pager")
 *           }
 *         }
 *       }
 *       override def unbind(key: String, pager: Pager): String = {
 *         intBinder.unbind(key + ".index", pager.index) + "&" + intBinder.unbind(key + ".size", pager.size)
 *       }
 *     }
 *   }
 * }}}
 *
 * To use it in a route, just write a type annotation aside the parameter you want to bind:
 *
 * {{{
 *   GET  /foo        controllers.foo(p: Pager)
 * }}}
 */
@implicitNotFound(
  "No QueryString binder found for type ${A}. Try to implement an implicit QueryStringBindable for this type."
)
trait QueryStringBindable[A] {
  self =>

  /**
   * Bind a query string parameter.
   *
   * @param key Parameter key
   * @param params QueryString data
   * @return `None` if the parameter was not present in the query string data. Otherwise, returns `Some` of either
   * `Right` of the parameter value, or `Left` of an error message if the binding failed.
   */
  def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, A]]

  /**
   * Unbind a query string parameter.
   *
   * @param key Parameter key
   * @param value Parameter value.
   * @return a query string fragment containing the key and its value. E.g. "foo=42"
   */
  def unbind(key: String, value: A): String

  /**
   * Transform this QueryStringBindable[A] to QueryStringBindable[B]
   */
  def transform[B](toB: A => B, toA: B => A) = new QueryStringBindable[B] {
    def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, B]] = {
      self.bind(key, params).map(_.map(toB))
    }
    def unbind(key: String, value: B): String = self.unbind(key, toA(value))
  }
}

/**
 * Binder for URL path parameters.
 *
 * You can provide an implementation of `PathBindable[A]` for any type `A` you want to be able to
 * bind directly from the request path.
 *
 * For example, given this class definition:
 *
 * {{{
 *   case class User(id: Int, name: String, age: Int)
 * }}}
 *
 * You can define a binder retrieving a `User` instance from its id, useable like the following:
 *
 * {{{
 *   // In your routes:
 *   // GET  /show/:user      controllers.Application.show(user)
 *   // For example: /show/42
 *
 *   class HomeController @Inject() (val controllerComponents: ControllerComponents) extends BaseController {
 *     def show(user: User) = Action {
 *       ...
 *     }
 *   }
 * }}}
 *
 * The definition of binder can look like the following:
 *
 * {{{
 *   object User {
 *     implicit def pathBinder(implicit intBinder: PathBindable[Int]) = new PathBindable[User] {
 *       override def bind(key: String, value: String): Either[String, User] = {
 *         for {
 *           id <- intBinder.bind(key, value).right
 *           user <- User.findById(id).toRight("User not found").right
 *         } yield user
 *       }
 *       override def unbind(key: String, user: User): String = {
 *         intBinder.unbind(key, user.id)
 *       }
 *     }
 *   }
 * }}}
 */
@implicitNotFound("No URL path binder found for type ${A}. Try to implement an implicit PathBindable for this type.")
trait PathBindable[A] {
  self =>

  /**
   * Bind an URL path parameter.
   *
   * @param key Parameter key
   * @param value The value as String (extracted from the URL path)
   * @return `Right` of the value or `Left` of an error message if the binding failed
   */
  def bind(key: String, value: String): Either[String, A]

  /**
   * Unbind a URL path  parameter.
   *
   * @param key Parameter key
   * @param value Parameter value.
   */
  def unbind(key: String, value: A): String

  /**
   * Transform this PathBinding[A] to PathBinding[B]
   */
  def transform[B](toB: A => B, toA: B => A) = new PathBindable[B] {
    def bind(key: String, value: String): Either[String, B] = self.bind(key, value).map(toB)
    def unbind(key: String, value: B): String               = self.unbind(key, toA(value))
  }
}

/**
 * Default binders for Query String
 */
object QueryStringBindable {
  import scala.language.experimental.macros

  /**
   * URL-encoding for all bindable string-parts.
   *
   * @param source Source char sequence for encoding.
   * @return URL-encoded string, if source string have had special characters.
   */
  private def _urlEncode(source: String): String =
    URLEncoder.encode(source, "utf-8")

  /**
   * A helper class for creating QueryStringBindables to map the value of a single key
   *
   * @param parse a function to parse the param value
   * @param serialize a function to serialize and URL-encode the param value. Remember to encode arbitrary strings,
   *                  for example using URLEncoder.encode.
   * @param error a function for rendering an error message if an error occurs
   * @tparam A the type being parsed
   */
  class Parsing[A](parse: String => A, serialize: A => String, error: (String, Exception) => String)
      extends QueryStringBindable[A] {
    def bind(key: String, params: Map[String, Seq[String]]) =
      params.get(key).flatMap(_.headOption).filter(_.nonEmpty).map { p =>
        try Right(parse(p))
        catch { case e: Exception => Left(error(key, e)) }
      }

    def unbind(key: String, value: A) =
      _urlEncode(key) + "=" + serialize(value)
  }

  /**
   * QueryString binder for String.
   */
  implicit def bindableString: QueryStringBindable[String] = new QueryStringBindable[String] {
    def bind(key: String, params: Map[String, Seq[String]]) =
      params.get(key).flatMap(_.headOption).map(Right(_))
    // No need to URL decode from query string since netty already does that

    // Use an option here in case users call index(null) in the routes -- see #818
    def unbind(key: String, value: String) =
      _urlEncode(key) + "=" + Option(value).fold("")(_urlEncode)
  }

  /**
   * QueryString binder for Char.
   */
  implicit object bindableChar extends QueryStringBindable[Char] {
    def bind(key: String, params: Map[String, Seq[String]]) =
      params.get(key).flatMap(_.headOption).filter(_.nonEmpty).map { value =>
        if (value.length == 1) {
          Right(value.charAt(0))
        } else {
          Left(s"Cannot parse parameter $key with value '$value' as Char: $key must be exactly one digit in length.")
        }
      }
    def unbind(key: String, value: Char) =
      s"${_urlEncode(key)}=$value"
  }

  /**
   * QueryString binder for Java Character.
   */
  implicit def bindableCharacter: QueryStringBindable[java.lang.Character] =
    bindableChar.transform(Char.box, Char.unbox)

  /**
   * QueryString binder for Int.
   */
  implicit object bindableInt
      extends Parsing[Int](_.toInt, _.toString, (s, e) => s"Cannot parse parameter $s as Int: ${e.getMessage}")

  /**
   * QueryString binder for Long.
   */
  implicit object bindableLong
      extends Parsing[Long](_.toLong, _.toString, (s, e) => s"Cannot parse parameter $s as Long: ${e.getMessage}")

  /**
   * QueryString binder for Short.
   */
  implicit object bindableShort
      extends Parsing[Short](_.toShort, _.toString, (s, e) => s"Cannot parse parameter $s as Short: ${e.getMessage}")

  /**
   * QueryString binder for Double.
   */
  implicit object bindableDouble
      extends Parsing[Double](
        _.toDouble,
        _.toString,
        (s, e) => s"Cannot parse parameter $s as Double: ${e.getMessage}"
      )

  /**
   * QueryString binder for Float.
   */
  implicit object bindableFloat
      extends Parsing[Float](_.toFloat, _.toString, (a, e) => s"Cannot parse parameter $a as Float: ${e.getMessage}")

  /**
   * QueryString binder for Boolean.
   */
  implicit object bindableBoolean
      extends Parsing[Boolean](
        _.trim match {
          case "true" | "1"  => true
          case "false" | "0" => false
        },
        _.toString,
        (s, _) => s"Cannot parse parameter $s as Boolean: should be true, false, 0 or 1"
      ) {}

  /**
   * QueryString binder for java.util.UUID.
   */
  implicit object bindableUUID
      extends Parsing[UUID](
        UUID.fromString(_),
        _.toString,
        (s, e) => s"Cannot parse parameter $s as UUID: ${e.getMessage}"
      )

  /**
   * QueryString binder for Option.
   */
  implicit def bindableOption[T: QueryStringBindable]: QueryStringBindable[Option[T]] =
    new QueryStringBindable[Option[T]] {
      def bind(key: String, params: Map[String, Seq[String]]) = {
        Some(
          implicitly[QueryStringBindable[T]]
            .bind(key, params)
            .map(_.map(Some(_)))
            .getOrElse(Right(None))
        )
      }
      def unbind(key: String, value: Option[T]) =
        value.map(implicitly[QueryStringBindable[T]].unbind(key, _)).getOrElse("")
    }

  /**
   * QueryString binder for Seq
   */
  implicit def bindableSeq[T: QueryStringBindable]: QueryStringBindable[Seq[T]] = new QueryStringBindable[Seq[T]] {
    def bind(key: String, params: Map[String, Seq[String]]) = bindSeq[T](key, params)
    def unbind(key: String, values: Seq[T])                 = unbindSeq(key, values)
  }

  /**
   * QueryString binder for List
   */
  implicit def bindableList[T: QueryStringBindable]: QueryStringBindable[List[T]] =
    bindableSeq[T].transform(_.toList, _.toSeq)

  private def bindSeq[T: QueryStringBindable](
      key: String,
      params: Map[String, Seq[String]]
  ): Option[Either[String, Seq[T]]] = {
    @tailrec
    def collectResults(values: List[String], results: List[T]): Either[String, Seq[T]] = {
      values match {
        case Nil => Right(results.reverse) // to preserve the original order
        case head :: rest =>
          implicitly[QueryStringBindable[T]].bind(key, Map(key -> Seq(head))) match {
            case None                => collectResults(rest, results)
            case Some(Right(result)) => collectResults(rest, result :: results)
            case Some(Left(err))     => collectErrs(rest, err :: Nil)
          }
      }
    }

    @tailrec
    def collectErrs(values: List[String], errs: List[String]): Left[String, Seq[T]] = {
      values match {
        case Nil => Left(errs.reverse.mkString("\n"))
        case head :: rest =>
          implicitly[QueryStringBindable[T]].bind(key, Map(key -> Seq(head))) match {
            case Some(Left(err))       => collectErrs(rest, err :: errs)
            case Some(Right(_)) | None => collectErrs(rest, errs)
          }
      }
    }

    params.get(key) match {
      case None         => Some(Right(Nil))
      case Some(values) => Some(collectResults(values.toList, Nil))
    }
  }

  private def unbindSeq[T: QueryStringBindable](key: String, values: Iterable[T]): String = {
    (for (value <- values) yield {
      implicitly[QueryStringBindable[T]].unbind(key, value)
    }).mkString("&")
  }
}

/**
 * Default binders for URL path part.
 */
object PathBindable {
  import scala.language.experimental.macros

  /**
   * A helper class for creating PathBindables to map the value of a path pattern/segment
   *
   * @param parse a function to parse the path value
   * @param serialize a function to serialize the path value to a string
   * @param error a function for rendering an error message if an error occurs
   * @tparam A the type being parsed
   */
  class Parsing[A](parse: String => A, serialize: A => String, error: (String, Exception) => String)
      extends PathBindable[A] {
    // added for bincompat
    @deprecated("Use constructor without codec", "2.6.2")
    private[mvc] def this(
        parse: String => A,
        serialize: A => String,
        error: (String, Exception) => String,
        codec: Codec
    ) = {
      this(parse, serialize, error)
    }

    def bind(key: String, value: String): Either[String, A] = {
      try Right(parse(value))
      catch { case e: Exception => Left(error(key, e)) }
    }

    def unbind(key: String, value: A): String = serialize(value)
  }

  /**
   * Path binder for String.
   */
  implicit object bindableString
      extends Parsing[String](identity, identity, (s, e) => s"Cannot parse parameter $s as String: ${e.getMessage}")

  /**
   * Path binder for Char.
   */
  implicit object bindableChar extends PathBindable[Char] {
    def bind(key: String, value: String) = {
      if (value.length != 1)
        Left(s"Cannot parse parameter $key with value '$value' as Char: $key must be exactly one digit in length.")
      else Right(value.charAt(0))
    }
    def unbind(key: String, value: Char) = value.toString
  }

  /**
   * Path binder for Java Character.
   */
  implicit def bindableCharacter: PathBindable[java.lang.Character] = bindableChar.transform(Char.box, Char.unbox)

  /**
   * Path binder for Int.
   */
  implicit object bindableInt
      extends Parsing[Int](_.toInt, _.toString, (s, e) => s"Cannot parse parameter $s as Int: ${e.getMessage}")

  /**
   * Path binder for Long.
   */
  implicit object bindableLong
      extends Parsing[Long](_.toLong, _.toString, (s, e) => s"Cannot parse parameter $s as Long: ${e.getMessage}")

  /**
   * Path binder for Double.
   */
  implicit object bindableDouble
      extends Parsing[Double](
        _.toDouble,
        _.toString,
        (s, e) => s"Cannot parse parameter $s as Double: ${e.getMessage}"
      )

  /**
   * Path binder for Float.
   */
  implicit object bindableFloat
      extends Parsing[Float](_.toFloat, _.toString, (s, e) => s"Cannot parse parameter $s as Float: ${e.getMessage}")

  /**
   * Path binder for Boolean.
   */
  implicit object bindableBoolean
      extends Parsing[Boolean](
        _.trim match {
          case "true" | "1"  => true
          case "false" | "0" => false
        },
        _.toString,
        (key: String, e: Exception) => "Cannot parse parameter %s as Boolean: should be true, false, 0 or 1".format(key)
      )

  /**
   * Path binder for java.util.UUID.
   */
  implicit object bindableUUID
      extends Parsing[UUID](
        UUID.fromString(_),
        _.toString,
        (s, e) => s"Cannot parse parameter $s as UUID: ${e.getMessage}"
      )

  /**
   * This is used by the Java RouterBuilder DSL.
   */
  private[play] lazy val pathBindableRegister: Map[Class[?], PathBindable[?]] = {
    import scala.language.existentials
    def register[T](implicit pb: PathBindable[T], ct: ClassTag[T]) = ct.runtimeClass -> pb
    Map(
      register[String],
      register[UUID]
    )
  }
}
