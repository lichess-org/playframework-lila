package io.methvin.play.autoconfig

import com.typesafe.sslconfig.util.ConfigLoader.apply
import play.api.ConfigLoader
import scala.quoted.*
import scala.deriving.Mirror.ProductOf

// based on https://github.com/gmethvin/play-autoconfig
private object AutoConfigImpl:

  private type IsProduct[U <: Product] = U

  def loader[T: Type](using q: Quotes): Expr[ConfigLoader[T]] = {
    import q.reflect.*

    val tpe  = Type.of[T]
    val repr = TypeRepr.of[T](using tpe)

    // def typeName(sym: Symbol): String =
    //   sym.fullName.replaceAll("(\\.package\\$|\\$|java\\.lang\\.|scala\\.Predef\\$\\.)", "")
    // // To print the implicit types in the compiler messages
    // def prettyType(t: TypeRepr): String = t match {
    //   case _ if t <:< TypeRepr.of[EmptyTuple] =>
    //     "EmptyTuple"

    //   case AppliedType(ty, a :: b :: Nil) if ty <:< TypeRepr.of[*:] =>
    //     s"${prettyType(a)} *: ${prettyType(b)}"

    //   case AppliedType(_, args) =>
    //     typeName(t.typeSymbol) + args.map(prettyType).mkString("[", ", ", "]")

    //   case OrType(a, b) =>
    //     s"${prettyType(a)} | ${prettyType(b)}"

    //   case _ => {
    //     val sym = t.typeSymbol

    //     if (sym.isTypeParam) {
    //       sym.name
    //     } else {
    //       typeName(sym)
    //     }
    //   }
    // }

    val name = repr.typeSymbol.name

    // val caseFields: List[(String, String)] = repr.typeSymbol.caseFields.map { s =>
    //   s.tree match {
    //     case v: ValDef => s.name -> v.tpt.tpe.typeSymbol.name
    //   }
    // }
    val caseFields = repr.typeSymbol.caseFields.map { s =>
      val name = s.name
      val tpe = s.tree match {
        case v: ValDef =>
          v.tpt.tpe.typeSymbol.name
      }
      s"$name: $tpe"
    }

    val e = Expr(
      s"$name(${caseFields.mkString(",")})"
    )
    report.info(s"/* Generated loader:\n${e.asTerm.show(using Printer.TreeAnsiCode)}\n*/")

    // val args = Varargs(caseFields.map { case (name, tpe) =>
    //   '{summon[_root_.play.api.ConfigLoader[tpe]].load(subConfig, $name)}
    // })

    // val expr = Expr(
    //   s"$name(${caseFields.mkString(",")})"
    // )
    val expr = '{
      new _root_.play.api.ConfigLoader[T] {
        override def load(config: _root_.com.typesafe.config.Config, path: String) = {
          val subConfig = if (path.isEmpty) config else config.getConfig(path)
          ???
        }
      }
    }

    // val expr = tpe match {
    //   case '[IsProduct[t]] =>
    //     Expr.summon[ProductOf[t]] match {
    //       case Some(pof) => '{
    //         new _root_.play.api.ConfigLoader[T] {
    //           override def load(config: _root_.com.typesafe.config.Config, path: String) = {
    //             val subConfig = if (path.isEmpty) config else config.getConfig(path)
    //             ???
    //           }
    //         }
    //       }
    //       case _ =>
    //         report.errorAndAbort(
    //           s"Instance not found: 'ProductOf[${prettyType(repr)}]'"
    //         )
    //     }
    // }

    report.info(s"/* Generated loader:\n${expr.asTerm.show(using Printer.TreeAnsiCode)}\n*/")

    expr
  }


    // val tpe = t.tpe
    // // Look at all constructors
    // val constructors: Seq[MethodSymbol] = tpe.members.collect {
    //   case m if m.isConstructor && !m.fullName.endsWith("$init$") =>
    //     m.asMethod
    // }.toSeq

    // val configConstructors = constructors.filter(_.annotations.exists(_.tree.tpe =:= typeOf[ConfigConstructor]))
    // val constructor = configConstructors match {
    //   case Seq() =>
    //     // Find the primary constructor
    //     constructors
    //       .find(_.isPrimaryConstructor)
    //       .getOrElse(c.abort(c.enclosingPosition, s"No primary constructor found!"))
    //   case Seq(cons) =>
    //     cons
    //   case _ =>
    //     // multiple annotated constructors found
    //     c.abort(c.enclosingPosition, s"Multiple constructors found annotated with @ConfigConstructor")
    // }

    // val confTerm = TermName(c.freshName("conf$"))
    // val argumentLists = constructor.paramLists.map { params =>
    //   params.map { p =>
    //     val name = p.annotations
    //       .collectFirst {
    //         case ann if ann.tree.tpe =:= typeOf[ConfigName] =>
    //           ann.tree.children.collectFirst { case Literal(Constant(str: String)) => str }.get
    //       }
    //       .getOrElse(p.name.decodedName.toString)
    //     q"implicitly[_root_.play.api.ConfigLoader[${p.typeSignature}]].load($confTerm, $name)"
    //   }
    // }
    // q"""
    //   new _root_.play.api.ConfigLoader[$tpe] {
    //     override def load(config: _root_.com.typesafe.config.Config, path: String) = {
    //       val $confTerm = if (path.isEmpty) config else config.getConfig(path)
    //       new $tpe(...$argumentLists)
    //     }
    //   }
    // """

  def fun[T](x: Expr[T])(using Type[T], Quotes): Expr[T] =
    println(x.show)
    x

  def pow(x: Double, n: Int): Double =
    if n == 0 then 1 else x * pow(x, n - 1)

  def powerCode( x: Expr[Double], n: Expr[Int])(using Quotes): Expr[Double] =
    import quotes.reflect.report
    (x.value, n.value) match
      case (Some(base), Some(exponent)) =>
        val value: Double = pow(base, exponent)
        Expr(value)
      case (Some(_), _) => report.errorAndAbort("Expected a known value for the exponent, but was " + n.show, n)
      case _ => report.errorAndAbort("Expected a known value for the base, but was " + x.show, x)

  def debugPowerCode( x: Expr[Double], n: Expr[Int])(using Quotes): Expr[Double] =
    println(
      s"powerCode \n" +
      s"  x := ${x.show}\n" +
      s"  n := ${n.show}")
    val code = powerCode(x, n)
    println(s"  code := ${code.show}")
    code

