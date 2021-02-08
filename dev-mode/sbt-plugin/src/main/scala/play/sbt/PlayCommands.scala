/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.sbt

import java.nio.file.Path

import play.sbt.PlayInternalKeys._
import sbt.Keys._
import sbt._

object PlayCommands {
  val playReloadTask = Def.task {
    playCompileEverything.value.reduceLeft(_ ++ _)
  }

  // ----- Play prompt

  val playPrompt = { state: State =>
    val extracted = Project.extract(state)
    import extracted._

    (name in currentRef)
      .get(structure.data)
      .map { name =>
        "[" + Colors.cyan(name) + "] $ "
      }
      .getOrElse("> ")
  }

  // ----- Play commands

  private[this] var commonClassLoader: ClassLoader = _

  val playCommonClassloaderTask = Def.task {
    val classpath = (dependencyClasspath in Compile).value
    val log       = streams.value.log
    //we need to handle scala-library.jar from ivy cache, or scala-library-2.x.x from coursier, but not for example scala-library-next.jar
    lazy val commonJars: PartialFunction[java.io.File, java.net.URL] = {
      case jar if jar.getName.startsWith("h2-") || jar.getName == "h2.jar"                        => jar.toURI.toURL
      case jar if jar.getName.startsWith("scala-library-2") || jar.getName == "scala-library.jar" => jar.toURI.toURL
    }

    if (commonClassLoader == null) {
      // The parent of the system classloader *should* be the extension classloader:
      // http://www.onjava.com/pub/a/onjava/2005/01/26/classloading.html
      // We use this because this is where things like Nashorn are located. We don't use the system classloader
      // because it will be polluted with the sbt launcher and dependencies of the sbt launcher.
      // See https://github.com/playframework/playframework/issues/3420 for discussion.
      val parent = ClassLoader.getSystemClassLoader.getParent
      log.debug("Using parent loader for play common classloader: " + parent)

      commonClassLoader = new java.net.URLClassLoader(classpath.map(_.data).collect(commonJars).toArray, parent) {
        override def toString = "Common ClassLoader: " + getURLs.map(_.toString).mkString(",")
      }
    }

    commonClassLoader
  }

  val playCompileEverythingTask = Def.taskDyn {
    // Run playAssetsWithCompilation, or, if it doesn't exist (because it's not a Play project), just the compile task
    val compileTask = Def.taskDyn(playAssetsWithCompilation ?? (compile in Compile).value)

    compileTask.all(
      ScopeFilter(
        inDependencies(thisProjectRef.value)
      )
    )
  }

  val h2Command = Command.command("h2-browser") { state: State =>
    try {
      val commonLoader  = Project.runTask(playCommonClassloader, state).get._2.toEither.right.get
      val h2ServerClass = commonLoader.loadClass("org.h2.tools.Server")
      h2ServerClass.getMethod("main", classOf[Array[String]]).invoke(null, Array.empty[String])
    } catch {
      case _: ClassNotFoundException =>
        state.log.error(
          s"""|H2 Dependency not loaded, please add H2 to your Classpath!
              |Take a look at https://www.playframework.com/documentation/${play.core.PlayVersion.current}/Developing-with-the-H2-Database#H2-database on how to do it.""".stripMargin
        )
      case e: Exception => e.printStackTrace()
    }
    state
  }
}
