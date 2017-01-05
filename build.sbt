import com.typesafe.sbt.SbtGit.git
import sbt.Keys._
import sbt._
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.{Versions, _}

enablePlugins(JavaAppPackaging)

name := "anti-xml"

scalaVersion := "2.11.8"

organization := "com.al333z"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

description := "anti-xml"

licenses := Seq(("BSD", new URL("https://github.com/arktekk/anti-xml/blob/master/LICENSE.rst")))

homepage := Some(url("http://github.com/AL333Z/anti-xml"))

libraryDependencies ++= Seq(
  "org.scalacheck" %% "scalacheck" % "1.11.3" % "test",
  "org.specs2" %% "specs2-core" % "3.8.6",
  "org.specs2" %% "specs2-scalacheck" % "3.8.6",
  "org.specs2" %% "specs2-matcher-extra" % "3.8.6",
  "org.scala-lang.modules" %% "scala-xml" % "1.0.6",
  "com.github.julien-truffaut" %% "monocle-core" % "1.4.0-M2",
  "com.github.julien-truffaut" %% "monocle-macro" % "1.4.0-M2",
  "org.scalaz" %% "scalaz-core" % "7.2.8"
)

initialCommands in console := """import com.codecommit.antixml._"""

scalacOptions in Compile in doc <++= (unmanagedSourceDirectories in Compile) map {
  (usd) =>
    val scalaSrc: File = (usd filter {
      _.toString endsWith "scala"
    }).head
    Seq(
      "-sourcepath", scalaSrc.toString,
      "-doc-source-url", "https://github.com/arktekk/anti-xml/tree/master/src/main/scala€{FILE_PATH}.scala"
    )
}

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ ⇒ false }

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

pomExtra :=
  <scm>
    <url>http://github.com/AL333Z/anti-xml</url>
    <connection>scm:git:git://github.com/AL333Z/anti-xml.git</connection>
    <developerConnection>scm:git:git@github.com:AL333Z/anti-xml.git</developerConnection>
  </scm>
    <developers>
      <developer>
        <id>djspiewak</id>
        <name>Daniel Spiewak</name>
        <url>http://twitter.com/djspiewak</url>
      </developer>
      <developer>
        <name>Erlend Hamnaberg</name>
        <url>http://twitter.com/hamnis</url>
      </developer>
    </developers>
    <contributors>
      <contributor>
        <name>Trygve Laugstøl</name>
        <url>http://twitter.com/trygvis</url>
      </contributor>
      <contributor>
        <name>Daniel Beskin</name>
      </contributor>
      <contributor>
        <name>Joshua Arnold</name>
      </contributor>
      <contributor>
        <name>Martin Kneissl</name>
      </contributor>
      <contributor>
        <name>Erik Engbrecht</name>
      </contributor>
      <contributor>
        <name>Heikki Vesalainen</name>
      </contributor>
    </contributors>

val VersionRegex = "v([0-9]+.[0-9]+.[0-9]+)-?(.*)?".r

def setReleaseVersionCustom(): ReleaseStep = {
  def setVersionOnly(selectVersion: Versions => String): ReleaseStep = { st: State =>
    val vs = st.get(ReleaseKeys.versions).getOrElse(sys.error("No versions are set! Was this release part executed before inquireVersions?"))
    val selected = selectVersion(vs)
    st.log.info("Setting version to '%s'." format selected)
    val useGlobal = Project.extract(st).get(releaseUseGlobalVersion)
    val versionStr = (if (useGlobal) globalVersionString else versionString) format selected

    reapply(Seq(
      if (useGlobal) version in ThisBuild := selected
      else version := selected
    ), st)
  }

  setVersionOnly(_._1)
}

git.useGitDescribe := true
git.baseVersion := "0.0.0"
git.gitTagToVersionNumber := {
  case VersionRegex(v, "") => Some(v)
  case VersionRegex(v, s) => Some(s"$v-$s")
  case _ => None
}

releaseVersion <<= releaseVersionBump(bumper => {
  ver =>
    Version(ver)
      .map(_.withoutQualifier)
      .map(_.bump(bumper).string).getOrElse(versionFormatError)
})

releaseProcess := Seq(
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  setReleaseVersionCustom(),
  tagRelease,
  ReleaseStep(action = Command.process("publishSigned", _), enableCrossBuild = true),
  ReleaseStep(action = Command.process("sonatypeReleaseAll", _), enableCrossBuild = true),
  pushChanges
)
