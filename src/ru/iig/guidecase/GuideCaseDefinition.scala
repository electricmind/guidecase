package ru.iig.guidecase

import scala.util.parsing.json
import java.net._
import java.io._
import android.graphics.{Bitmap, BitmapFactory}
import android.content.Context
import android.os.Bundle
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.HttpClient
import org.apache.http.impl.client.DefaultHttpClient
import java.lang.System
import org.json._


object GuideCaseMessage {
    lazy val is : Stream[Int] = 1 #:: is.map(_ + 1)
    val GuideCaseBroadcast = "GuideServerComplete"

    val i = is.toIterator
    val update = i.next
    val newindex = i.next
}

object log {
  def apply(s : String) : Unit = println(("?GuideSearch - V - " + System.currentTimeMillis() + " - %s").format(s))
  def apply(s : Int) : Unit = this(s.toString)
  def apply(x : Throwable) : Unit = println("?GuideSearch - W - %s".format(x.toString))
}

object stack {
  def apply(code : =>Unit) : Unit = {
    val thread = new Thread(null, new Runnable() {
      def run() = code
    }, "InitDataThread", 8*1048570);
    thread.start()
    thread.join()
  } 
}

/*object stackres {
  def apply[T](code : => Unit[T]) : T = {
    var ret : T = null
    val thread = new Thread(null, new Runnable() {
      def run() = {
          ret = code
      }
    }, "InitDataThread", 8*1048570);
    thread.start()
    thread.join()
    ret
  } 
}*/

object async {
  def apply(code : =>Unit) : Unit = {
    val thread = new Thread(null, new Runnable() {
      def run() = code
    }, "InitDataThread", 8*1048570);
    thread.start()
  } 
}


object Definition {
  type Tag = String
  type Tags = List[Tag]
  type Title = String
  type Description = String
  type URIImage = URI
  type URISlides = URI
  
  type Announce = (Title, Description, URIImage, URI)
  type Announces = List[(Announce, Tags)]
  type Slide = (Title, Description, URIImage)
  type Slides = List[Slide]

  type Tag2Announces = Map[Tag, Set[Announce]]
  type Tag2Tags = Map[Tag, Set[Tag]]

  type Position = Int
  type State = (Position)
}
