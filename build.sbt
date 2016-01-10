sbtVersion := "0.13.9"

name := "guidecase"

import android.Keys._
android.Plugin.androidBuild

buildToolsVersion := Some("19.1")

import android.Keys._

javacOptions ++= Seq("-source", "1.6", "-target", "1.6")
scalaVersion := "2.11.5"
//scalaVersion := "2.10.2"

scalacOptions in Compile += "-feature"

updateCheck in Android := {} // disable update check

useProguard in Android := true

proguardCache in Android ++= Seq("scala")

proguardOptions in Android ++= Seq(
  "sdfsa asf asfs sadf sdfsad sadf sd-dontobfuscate", 
  "-verbose",
  "-printconfiguration",
  "-dontoptimize", 
  "-keepattributes Signature", 
  "-printseeds target/seeds.txt", 
  "-printusage target/usage.txt",
  "-dontwarn scala.collection.**", // required from Scala 2.11.4
  "-dontwarn org.scaloid.**" // this can be omitted if current Android Build target is android-16
)

libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.2"

run <<= run in Android

install <<= install in Android

buildToolsVersion := Some("23.0.2")

dexMulti in Android := true

dexMinimizeMain in Android := true

dexMainClasses in Android := Seq(
  "com/example/app/MultidexApplication.class",
  "android/support/multidex/BuildConfig.class",
  "android/support/multidex/MultiDex$V14.class",
  "android/support/multidex/MultiDex$V19.class",
  "android/support/multidex/MultiDex$V4.class",
  "android/support/multidex/MultiDex.class",
  "android/support/multidex/MultiDexApplication.class",
  "android/support/multidex/MultiDexExtractor$1.class",
  "android/support/multidex/MultiDexExtractor.class",
  "android/support/multidex/ZipUtil$CentralDirectory.class",
  "android/support/multidex/ZipUtil.class"
)

// Tests //////////////////////////////

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-library" % "2.10.2",
  "com.android.support" % "support-v4" % "19.0.0",
  "org.apache.maven" % "maven-ant-tasks" % "2.1.3" % "test",
  "org.robolectric" % "robolectric" % "3.0" % "test",
  "junit" % "junit" % "4.12" % "test",
  "com.novocode" % "junit-interface" % "0.11" % "test"
)

// without this, @Config throws an exception,
unmanagedClasspath in Test ++= (bootClasspath in Android).value
