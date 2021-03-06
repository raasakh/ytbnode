import com.typesafe.sbt.packager.archetypes.TemplateWriter
import sbt.Keys.{sourceGenerators, _}
import sbt._
import sbt.internal.inc.ReflectUtilities
import sbtassembly.MergeStrategy
import sbtcrossproject.CrossPlugin.autoImport.crossProject

enablePlugins(JavaServerAppPackaging, JDebPackaging, SystemdPlugin, GitVersioning)
scalafmtOnCompile in ThisBuild := true
Global / cancelable := true
Global / coverageExcludedPackages := ".*"

val versionSource = Def.task {
  // WARNING!!!
  // Please, update the fallback version every major and minor releases.
  // This version is used then building from sources without Git repository
  // In case of not updating the version nodes build from headless sources will fail to connect to newer versions
  val FallbackVersion = (1, 0, 1)

  val versionFile      = (sourceManaged in Compile).value / "one" / "ytb" / "Version.scala"
  val versionExtractor = """(\d+)\.(\d+)\.(\d+).*""".r
  val (major, minor, patch) = version.value match {
    case versionExtractor(ma, mi, pa) => (ma.toInt, mi.toInt, pa.toInt)
    case _                            => FallbackVersion
  }
  IO.write(
    versionFile,
    s"""package one.ytb
       |
       |object Version {
       |  val VersionString = "${version.value}"
       |  val VersionTuple = ($major, $minor, $patch)
       |}
       |""".stripMargin
  )
  Seq(versionFile)
}
val network = SettingKey[Network]("network")
network := { Network(sys.props.get("network")) }
name := "ytb"
normalizedName := s"${name.value}${network.value.packageSuffix}"

git.useGitDescribe := true
git.uncommittedSignifier := Some("DIRTY")
logBuffered := false

inThisBuild(
  Seq(
    scalaVersion := "2.12.7",
    organization := "one.ytb",
    crossPaths := false,
    scalacOptions ++= Seq("-feature", "-deprecation", "-language:higherKinds", "-language:implicitConversions", "-Ywarn-unused:-implicits", "-Xlint")
  ))

resolvers ++= Seq(
  Resolver.bintrayRepo("ethereum", "maven"),
  Resolver.bintrayRepo("dnvriend", "maven"),
  Resolver.sbtPluginRepo("releases")
)

fork in run := true
javaOptions in run ++= Seq(
  "-XX:+IgnoreUnrecognizedVMOptions",
  "--add-modules=java.xml.bind"
)

Test / fork := true
Test / javaOptions ++= Seq(
  "-XX:+IgnoreUnrecognizedVMOptions",
  "--add-modules=java.xml.bind",
  "--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED"
)

val aopMerge: MergeStrategy = new MergeStrategy {
  val name = "aopMerge"
  import scala.xml._
  import scala.xml.dtd._

  def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] = {
    val dt                         = DocType("aspectj", PublicID("-//AspectJ//DTD//EN", "http://www.eclipse.org/aspectj/dtd/aspectj.dtd"), Nil)
    val file                       = MergeStrategy.createMergeTarget(tempDir, path)
    val xmls: Seq[Elem]            = files.map(XML.loadFile)
    val aspectsChildren: Seq[Node] = xmls.flatMap(_ \\ "aspectj" \ "aspects" \ "_")
    val weaverChildren: Seq[Node]  = xmls.flatMap(_ \\ "aspectj" \ "weaver" \ "_")
    val options: String            = xmls.map(x => (x \\ "aspectj" \ "weaver" \ "@options").text).mkString(" ").trim
    val weaverAttr                 = if (options.isEmpty) Null else new UnprefixedAttribute("options", options, Null)
    val aspects                    = new Elem(null, "aspects", Null, TopScope, false, aspectsChildren: _*)
    val weaver                     = new Elem(null, "weaver", weaverAttr, TopScope, false, weaverChildren: _*)
    val aspectj                    = new Elem(null, "aspectj", Null, TopScope, false, aspects, weaver)
    XML.save(file.toString, aspectj, "UTF-8", xmlDecl = false, dt)
    IO.append(file, IO.Newline.getBytes(IO.defaultCharset))
    Right(Seq(file -> path))
  }
}

inTask(assembly)(
  Seq(
    test := {},
    assemblyJarName := s"ytb-node-${version.value}.jar",
    assemblyMergeStrategy := {
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.concat
      case PathList("META-INF", "aop.xml")                      => aopMerge
      case other                                                => (assemblyMergeStrategy in assembly).value(other)
    }
  ))

inConfig(Compile)(
  Seq(
    mainClass := Some("one.ytb.Application"),
    publishArtifact in packageDoc := false,
    publishArtifact in packageSrc := false,
    sourceGenerators += versionSource
  ))

inConfig(Test)(
  Seq(
    logBuffered := false,
    parallelExecution := false,
    testOptions += Tests.Argument("-oIDOF", "-u", "target/test-reports"),
    testOptions += Tests.Setup(_ => sys.props("sbt-testing") = "true")
  ))

inConfig(Linux)(
  Seq(
    maintainer := "ytb.one",
    packageSummary := "Ytb node",
    packageDescription := "Ytb node"
  ))

bashScriptExtraDefines += s"""addJava "-Dytb.directory=/var/lib/${normalizedName.value}""""

val linuxScriptPattern = "bin/(.+)".r
val batScriptPattern   = "bin/([^.]+)\\.bat".r

inConfig(Universal)(
  Seq(
    mappings += (baseDirectory.value / s"ytb-${network.value}.conf" -> "doc/ytb.conf.sample"),
    mappings := {
      val scriptSuffix = network.value.packageSuffix
      mappings.value.map {
        case m @ (file, batScriptPattern(script)) =>
          if (script.endsWith(scriptSuffix)) m else (file, s"bin/$script$scriptSuffix.bat")
        case m @ (file, linuxScriptPattern(script)) =>
          if (script.endsWith(scriptSuffix)) m else (file, s"bin/$script$scriptSuffix")
        case other => other
      }
    },
    javaOptions ++= Seq(
      // -J prefix is required by the bash script
      "-J-server",
      // JVM memory tuning for 2g ram
      "-J-Xms128m",
      "-J-Xmx1g",
      "-J-XX:+ExitOnOutOfMemoryError",
      // Java 9 support
      "-J-XX:+IgnoreUnrecognizedVMOptions",
      "-J--add-modules=java.xml.bind",
      // from https://groups.google.com/d/msg/akka-user/9s4Yl7aEz3E/zfxmdc0cGQAJ
      "-J-XX:+UseG1GC",
      "-J-XX:+UseNUMA",
      "-J-XX:+AlwaysPreTouch",
      // probably can't use these with jstack and others tools
      "-J-XX:+PerfDisableSharedMem",
      "-J-XX:+ParallelRefProcEnabled",
      "-J-XX:+UseStringDeduplication"
    )
  ))

val packageSource = Def.setting {
  sourceDirectory.value / "package"
}

val upstartScript = Def.task {
  val src    = packageSource.value / "upstart.conf"
  val dest   = (target in Debian).value / "upstart" / s"${packageName.value}.conf"
  val result = TemplateWriter.generateScript(src.toURI.toURL, linuxScriptReplacements.value)
  IO.write(dest, result)
  dest
}

linuxPackageMappings ++= Seq(
  (upstartScript.value, s"/etc/init/${packageName.value}.conf")
).map(packageMapping(_).withConfig().withPerms("644"))

linuxScriptReplacements += "detect-loader" ->
  """is_systemd() {
    |    which systemctl >/dev/null 2>&1 && \
    |    systemctl | grep -- -\.mount >/dev/null 2>&1
    |}
    |is_upstart() {
    |    /sbin/init --version | grep upstart >/dev/null 2>&1
    |}
    |""".stripMargin

inConfig(Debian)(
  Seq(
    linuxStartScriptTemplate := (packageSource.value / "systemd.service").toURI.toURL,
    debianPackageDependencies += "java8-runtime-headless",
    serviceAutostart := false,
    maintainerScripts := maintainerScriptsFromDirectory(packageSource.value / "debian", Seq("preinst", "postinst", "postrm", "prerm"))
  ))

commands += Command.command("packageAll") { state =>
  "clean" :: "assembly" :: "debian:packageBin" :: state
}

// https://stackoverflow.com/a/48592704/4050580
def allProjects: List[ProjectReference] = ReflectUtilities.allVals[Project](this).values.toList map { p =>
  p: ProjectReference
}

addCommandAlias(
  "checkPR",
  """;
    |set scalacOptions in ThisBuild ++= Seq("-Xfatal-warnings");
    |Global / checkPRRaw;
    |set scalacOptions in ThisBuild -= "-Xfatal-warnings";
  """.stripMargin
)
lazy val checkPRRaw = taskKey[Unit]("Build a project and run unit tests")
checkPRRaw in Global := {
  try {
    clean.all(ScopeFilter(inProjects(allProjects: _*), inConfigurations(Compile))).value
  } finally {
    test.all(ScopeFilter(inProjects(langJVM, node), inConfigurations(Test))).value
    (langJS / Compile / fastOptJS).value
    compile.all(ScopeFilter(inProjects(generator, benchmark), inConfigurations(Test))).value
  }
}

lazy val lang =
  crossProject(JSPlatform, JVMPlatform)
    .withoutSuffixFor(JVMPlatform)
    .settings(
      version := "1.0.0",
      coverageExcludedPackages := ".*",
      // the following line forces scala version across all dependencies
      scalaModuleInfo ~= (_.map(_.withOverrideScalaVersion(true))),
      test in assembly := {},
      addCompilerPlugin(Dependencies.kindProjector),
      libraryDependencies ++=
        Dependencies.cats ++
          Dependencies.fp ++
          Dependencies.scalacheck ++
          Dependencies.scorex ++
          Dependencies.scalatest ++
          Dependencies.scalactic ++
          Dependencies.monix.value ++
          Dependencies.scodec.value ++
          Dependencies.fastparse.value,
      resolvers += Resolver.bintrayIvyRepo("portable-scala", "sbt-plugins"),
      resolvers += Resolver.sbtPluginRepo("releases")
    )
    .jsSettings(
      scalaJSLinkerConfig ~= {
        _.withModuleKind(ModuleKind.CommonJSModule)
      }
    )
    .jvmSettings(
      coverageExcludedPackages := "",
      publishMavenStyle := true,
      credentials += Credentials(Path.userHome / ".sbt" / ".credentials"),
      publishTo := Some("Sonatype Nexus" at "https://oss.sonatype.org/service/local/staging/deploy/maven2"),
      name := "RIDE Compiler",
      normalizedName := "lang",
      description := "The RIDE smart contract language compiler",
      homepage := Some(url("https://docs.ytb.one/en/technical-details/ytb-contracts-language-description/maven-compiler-package.html")),
      licenses := Seq(("MIT", url("https://github.com/raasakh/ytbnode/blob/master/LICENSE"))),
      organization := "one.ytb",
      organizationName := "Ytb One",
      organizationHomepage := Some(url("https://ytb.one")),
      scmInfo := Some(ScmInfo(url("https://github.com/raasakh/ytbnode"), "git@github.com:raasakh/ytbnode.git", None)),
      developers := List(Developer("raasakh", "Alexandr Ryzhikh", "raasakh@gmail.com", url("https://ytb.one"))),
      libraryDependencies ++= Seq(
        "org.scala-js"                      %% "scalajs-stubs" % "1.0.0-RC1" % "provided",
        "com.github.spullara.mustache.java" % "compiler" % "0.9.5"
      ) ++ Dependencies.logging.map(_       % "test") // scrypto logs an error if a signature verification was failed
    )

lazy val langJS  = lang.js
lazy val langJVM = lang.jvm

lazy val node = project
  .in(file("."))
  .settings(
    addCompilerPlugin(Dependencies.kindProjector),
    coverageExcludedPackages := "",
    libraryDependencies ++=
      Dependencies.network ++
        Dependencies.db ++
        Dependencies.http ++
        Dependencies.akka ++
        Dependencies.serialization ++
        Dependencies.testKit.map(_ % "test") ++
        Dependencies.logging ++
        Dependencies.matcher ++
        Dependencies.metrics ++
        Dependencies.fp ++
        Dependencies.meta ++
        Dependencies.ficus ++
        Dependencies.scorex ++
        Dependencies.commons_net ++
        Dependencies.monix.value
  )
  .dependsOn(langJVM)

lazy val discovery = project

lazy val it = project
  .dependsOn(node)

lazy val generator = project
  .dependsOn(it)
  .settings(libraryDependencies += "com.github.scopt" %% "scopt" % "3.6.0")

lazy val benchmark = project
  .enablePlugins(JmhPlugin)
  .dependsOn(node % "compile->compile;test->test", langJVM % "compile->compile;test->test")

lazy val dexgenerator = project
  .dependsOn(it)
  .settings(libraryDependencies += "com.github.scopt" %% "scopt" % "3.6.0")
