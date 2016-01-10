sbtVersion := "0.13.9"

name := "guidecase"

buildToolsVersion := Some("19.1")

import android.Keys._

javacOptions ++= Seq("-source", "1.6", "-target", "1.6")
scalaVersion := "2.11.5"
//scalaVersion := "2.10.2"

scalacOptions in Compile += "-feature"

updateCheck in Android := {} // disable update check

useProguard in Android := true

proguardScala in Android := true

proguardCache in Android ++= Seq("scala")

proguardOptions in Android ++= Seq(
  "-dontobfuscate",
  "-verbose",
  "-printconfiguration",
  "-dontoptimize", 
  "-keepattributes Signature", 
  "-printseeds target/seeds.txt", 
  "-printusage target/usage.txt",
  "-dontwarn scala.collection.**" // required from Scala 2.11.4
)

libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.2"

run <<= run in Android

install <<= install in Android

buildToolsVersion := Some("23.0.2")

//dexMulti in Android := true
//
//dexMinimizeMain in Android := true
//
//dexMainClasses in Android := Seq(
//  "com/example/app/MultidexApplication.class",
//  "android/support/multidex/BuildConfig.class",
//  "android/support/multidex/MultiDex$V14.class",
//  "android/support/multidex/MultiDex$V19.class",
//  "android/support/multidex/MultiDex$V4.class",
//  "android/support/multidex/MultiDex.class",
//  "android/support/multidex/MultiDexApplication.class",
//  "android/support/multidex/MultiDexExtractor$1.class",
//  "android/support/multidex/MultiDexExtractor.class",
//  "android/support/multidex/ZipUtil$CentralDirectory.class",
//  "android/support/multidex/ZipUtil.class"
//)

// Tests //////////////////////////////

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-library" % "2.11.5",
  "com.android.support" % "support-v4" % "19.0.0"
)

// without this, @Config throws an exception,
unmanagedClasspath in Test ++= (bootClasspath in Android).value
