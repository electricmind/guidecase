package ru.iig.guidecase
/*
*   A server that downloads data and supports a cache of this data
*
*/
import scala.annotation.tailrec
import scala.util.Random
import java.io._
import java.net._
import android.content.{ Intent, Context }
import android.media.{ MediaPlayer, AudioManager }
import android.app.{ IntentService, Service }
import android.widget.Toast
import android.os.{ Binder, IBinder, AsyncTask, Handler, HandlerThread, Process, Message }
import android.graphics.Bitmap.CompressFormat
import android.graphics.{ Bitmap, BitmapFactory }
import org.json._
import android.util._
import android.support.v4.content.LocalBroadcastManager

import ru.iig.guidecase.Definition._

class GuideCaseBinder(val service: GuideCaseService) extends Binder {
    def getService(): GuideCaseService = {
        return service
    }
}

// ticket : evaluate progress bar
// ticket : save scaled image
// ticket : save size of slideset in status
// ticket : pause task if connection is gone

abstract class DownloadTask(val uri: URI) extends Serializable
case class DownloadSlideTask(override val uri: URI) extends DownloadTask(uri)
case class DownloadSlidesTask(override val uri: URI) extends DownloadTask(uri)
case class RemoveSlidesTask(override val uri: URI) extends DownloadTask(uri)
case class DownloadIndexTask(override val uri: URI) extends DownloadTask(uri)

case class State(val l: Boolean = false, val d: Boolean = false,
                 val e: Boolean = false,
                 val p: Int = 0) extends Serializable {
    def local(x: Boolean) = new State(x, d, e, p)
    def downloading(x: Boolean) = new State(l, x, e, p)
    def error(x: Boolean) = new State(l, d, x, p)
    def position(x: Int) = new State(l, d, e, x)
}

object uriToCache {
    def apply(uri: URI, extension: String = "usual") = extension + "-" + uri.toString.replace("[/\\:]", "-")
}

trait Index {
    lazy val tag2announces: Tag2Announces = Map()
    lazy val tag2tags: Tag2Tags = Map()
    lazy val announces: Announces = List()

    def elicitTag2Tags(t2as: Tag2Announces) = {
        lazy val url2tag: Map[URI, Set[Tag]] = t2as.foldLeft(Map[URI, Set[Tag]]())((map, x) => x match {
            case (tag, announces) => announces.map(_._4).foldLeft(map)((map, x) => map + (x -> (map.getOrElse(x, Set[Tag]()) + tag)))
        })
        t2as.map({ case (x, announces) => x -> announces.map(x => url2tag(x._4)).flatten })
    }
}

class LocalIndex(val service: GuideCaseService, val remote: RemoteIndex) extends Index {
    def exists(announce: Announce): Boolean = announce match { case (_, _, _, uri) => { log("--> " + uri + " " + service.state(uri).l); service.state(uri).l } }

    override lazy val tag2announces: Tag2Announces = (for {
        (tag, announces) <- remote.tag2announces
    } yield (tag -> announces.filter(exists))) //.filterNot({case (_,announces) => announces.isEmpty})

    override lazy val tag2tags: Tag2Tags = elicitTag2Tags(tag2announces)

    override lazy val announces: Announces = remote.announces.filter(x => exists(x._1))
}

class RemoteIndex(val service: GuideCaseService) extends Index {
    override lazy val announces: Announces = (new JSONTokener(try {
        io.Source.fromInputStream(service.openFileInput("usual-guidecaseindex.dat")).getLines.mkString("\n")
    } catch {
        case x => {
            log(x)
            service.msg("Index corrupted, reloading is needed")
            "[]"
        }
    }).nextValue().asInstanceOf[JSONArray] match {
        case x => (0 until x.length).map(x.getJSONArray).map(x => {
            val jtags = x.getJSONArray(1)
            val tags = (0 until jtags.length).map(jtags.getString).toList
            val jannounce = x.getJSONArray(0)
            ((jannounce.getString(0), jannounce.getString(1), new URI(jannounce.getString(2)), new URI(jannounce.getString(3))), tags)
        })
    }).toList

    lazy val uri2slidetitle = announces.map({ case ((title, _, _, uri), _) => uri.toString -> title }).toMap

    override lazy val tag2announces: Tag2Announces = announces.foldLeft(Map[Tag, Set[Announce]]())((map, x) => x match {
        case ((title, description, image, uri), tags) => tags.foldLeft(map)(
            (map, tag) => map + (tag -> (map.getOrElse(tag, Set[Announce]()) + ((title, description, image, uri)))))
    })

    override lazy val tag2tags: Tag2Tags = elicitTag2Tags(tag2announces)

    var local = new LocalIndex(service, this)
    def resetlocal = {
        local = new LocalIndex(service, this)
    }
}

class GuideCaseService extends IntentService("GuideCaseService") {
    var lock: AnyRef = new Object
    var callbacks = Map[Int, (Int, Int) => Unit]()
    var callbacksindex = 0
    var index = new RemoteIndex(this)
    var indexstate: Map[String, State] = Map()
    var id2progress: Map[Int, Progress] = Map()
    var progressindex = 0
    var ntasks, fulfilled = 0
    var range = 1

    val binder: IBinder = new GuideCaseBinder(this)
    val handler: Handler = new Handler();

    override def onBind(intent: Intent): IBinder = binder

    def msg(msg: AnyRef) = {
        handler.post(new Runnable {
            def run(): Unit = Toast.makeText(GuideCaseService.this, msg.toString, Toast.LENGTH_SHORT).show();
        })
    }

    def state(uri: URI): State = indexstate.synchronized {
        indexstate.getOrElse(uri.toString, new State())
    }

    def state(uri: URI, state: State): State = {
        indexstate.synchronized {
            indexstate = indexstate + (uri.toString -> state)
        }
        new java.io.ObjectOutputStream(openFileOutput("indexstate.dat", Context.MODE_PRIVATE)) {
            writeObject(indexstate)
            close
        }
        state
    }

    def uriResolve(uri: URI) = new URI(getSharedPreferences("GuideCase", 0).getString("server", "http://guidecase.dreambot.ru/Export/")).resolve(uri)

    /* note : jarp18 is Yarno and he has come from Finland */

    def remoteIndexes: (Tag2Announces, Tag2Tags) = { log("remoteIndexes"); (index.tag2announces, index.tag2tags) }

    def localIndexes: (Tag2Announces, Tag2Tags) = { log("localIndexes"); (index.local.tag2announces, index.local.tag2tags) }

    def seen(callback: (Int, Int) => Unit): Int = {
        callbacksindex = callbacksindex + 1
        callbacks = callbacks + (callbacksindex -> callback)
        callbacksindex
    }

    def slides(uri: URI): Slides = try {
        val st = io.Source.fromInputStream(openFileInput(uriToCache(uri)))
        //log("slides: " + st.getLines.mkString("\n"))
        val jslides = new JSONTokener(st.getLines.mkString("\n")).nextValue().asInstanceOf[JSONArray]
        st.close()
        (0 until jslides.length).map(jslides.getJSONArray).map(x =>
            (x.getString(0), x.getString(1), new URI(x.getString(2)))).toList
    } catch {
        case x => msg("Slides are invalid and should be reladed"); List(("Invalid slide set", "This slideset is invalid or downloading has been failed, try to reload to be amazed. The reason was: " + x, new URI("")))
    }

    val imagecache: LruCache[String, Bitmap] = new LruCache[String, Bitmap](2 * 1024 + (List(Runtime.getRuntime().maxMemory() - 50331648, 0).max / 4000).toInt) {
        override def sizeOf(uri: String, bitmap: Bitmap): Int = bitmap.getByteCount() / 1024

        override def create(uri: String): Bitmap = {
            val options1: BitmapFactory.Options = new BitmapFactory.Options() {
                inJustDecodeBounds = true
            }
            val st1 = openFileInput(uriToCache(new URI(uri)))
            BitmapFactory.decodeStream(st1, null, options1)
            st1.close()
            def factor(option: BitmapFactory.Options) = {
                val x = Math.ceil(Math.sqrt(options1.outHeight * options1.outWidth / (1.5 * 1024 * 768).toFloat)).toInt
                if (x >= 1) x else 1
            }

            log("size & factor: %d %d %d".format(options1.outHeight, options1.outWidth, factor(options1)))

            val options2: BitmapFactory.Options = new BitmapFactory.Options() {
                inSampleSize = factor(options1)
            }

            val st2 = openFileInput(uriToCache(new URI(uri)))
            log("memory: " + java.lang.Runtime.getRuntime().freeMemory() + " " + Runtime.getRuntime().maxMemory() + " " + Runtime.getRuntime().totalMemory())
            log("size  : " + size())

            List(Runtime.getRuntime().maxMemory() - 50331648, 0).max / 2
            val bitmap = BitmapFactory.decodeStream(st2, null, options2)
            st2.close()
            log("picture: " + options2.outHeight + " " + options2.outWidth)
            java.lang.Runtime.getRuntime().gc()
            bitmap
        }

        /*override
      def entryRemoved (evicted : Boolean, uri : URI, oldbitmap : Bitmap, newbitmap : Bitmap) = {
	  if (!evicted) oldbitmap.recycle()
      }*/
    }
    val iconcache: LruCache[String, Bitmap] = new LruCache[String, Bitmap](4 * 1024 + (List(Runtime.getRuntime().maxMemory() - 50331648, 0).max / 4096).toInt) {
        override def sizeOf(key: String, bitmap: Bitmap): Int = bitmap.getByteCount() / 1024

        override def create(uri: String): Bitmap = {
            //*imagecache.synchronized { imagecache.evictAll() }
            val st = openFileInput(uriToCache(new URI(uri), "icon"))
            val bitmap = BitmapFactory.decodeStream(st)
            st.close()
            log("memory: " + java.lang.Runtime.getRuntime().freeMemory() + " " + Runtime.getRuntime().maxMemory() + " " + Runtime.getRuntime().totalMemory())
            java.lang.Runtime.getRuntime().gc()
            bitmap
        }
    }

    def image(uri: URI): Bitmap = try {
        imagecache.synchronized {
            imagecache.get(uri.toString) match {
                case null => BitmapFactory.decodeResource(getResources(), R.drawable.brokenimage)
                case x    => x
            }
        }
    } catch {
        case x => {
            println("broken image" + x);
            BitmapFactory.decodeResource(getResources(), R.drawable.brokenimage)
        }
    }

    def imageicon(uri: URI): Bitmap = try {
        iconcache.synchronized { iconcache.get(uri.toString) }
    } catch {
        case x => {
            println("broken icon" + x);
            BitmapFactory.decodeResource(getResources(), R.drawable.brokenimage)
        }
    }

    var buf = new Array[Byte](1024 * 1024)
    var pos = 0

    // ticket : enchance report and progress to pay attention on previous downloadings to estimate new volume
    def regProgress(progress: Progress): Int = {
        progressindex = progressindex + 1
        id2progress = id2progress + (progressindex -> progress)
        log("reg range:" + range)
        log("progressamount:" + id2progress.size + " " + progressindex)
        progress.init(ntasks, fulfilled, range)
        progressindex
    }

    def unregProgress(id: Int) = {
        log("unreg1:" + id2progress.size + " " + id)
        id2progress = id2progress - id
        log("unreg2:" + id2progress.size + " " + id)
    }

    def progressValue(x: Int) = {
        fulfilled = fulfilled + x
        id2progress.values.map(_.value(fulfilled))
    }

    def progressRange(x: Int) = {
        range = range + x
        log("Range " + range + " " + x)
        id2progress.values.map(_.rerange(range))
    }

    def progressStart() = {
        range = 0
        fulfilled = 0
        ntasks = ntasks + 1
        id2progress.values.map(_.show(1))
    }

    def progressFinish() = {
        ntasks = ntasks - 1
        id2progress.values.map(_.hide)
    }

    def makeicon(uri: URI) = try {
        val st = openFileInput(uriToCache(uri))
        val options: BitmapFactory.Options = new BitmapFactory.Options() {
            inSampleSize = if (st.available > 100 * 1024) 4 else 2
        }
        val bitmap1 = BitmapFactory.decodeStream(st, null, options)

        st.close()
        val (w, h) = (bitmap1.getWidth, bitmap1.getHeight) match {
            case (w, h) if h > w => (90, (h * (90. / w)).toInt)
            case (w, h)          => ((w * (90. / h)).toInt, 90)
        }

        val bitmap2 = Bitmap.createScaledBitmap(bitmap1, w, h, true)
        bitmap1.recycle()
        val fo = openFileOutput(uriToCache(uri, "icon"), Context.MODE_PRIVATE)
        bitmap2.compress(CompressFormat.JPEG, 80, fo)
        fo.close
        bitmap2.recycle()
    } catch {
        case x => msg("Making icon failed"); log("Conversion image into icon is failure: " + x)
    }

    def download(uri: URI): Option[URI] = {
        val ruri = uriResolve(uri)
        var count = 0
        try {
            import org.apache.http.HttpEntity
            import org.apache.http.HttpResponse
            import org.apache.http.client.methods.HttpGet
            import org.apache.http.client.HttpClient
            import org.apache.http.impl.client.DefaultHttpClient
            val httpclient = new DefaultHttpClient()
            val httpget = new HttpGet(ruri)
            val httpresponse = httpclient.execute(httpget)
            val httpentity = httpresponse.getEntity()
            val st = httpentity.getContent()
            imagecache.synchronized { imagecache.evictAll() }
            iconcache.synchronized { iconcache.evictAll() }
            try {
                buf.synchronized {
                    pos = 0
                    Stream.continually({
                        val size = st.read(buf, pos, buf.size - pos)
                        pos = pos + size
                        if (pos >= buf.size) {
                            buf = buf ++ new Array[Byte](1024 * 1024)
                        }
                        log("size: " + pos)
                        size
                    }).takeWhile(-1 != _).toList

                    log("cachefile: " + uriToCache(uri))
                    val output = openFileOutput(uriToCache(uri), Context.MODE_PRIVATE)
                    output.write(buf, 0, pos + 1)
                    output.close()
                }
                Some(uri)
            } catch {
                case x => log(x); None
            }
        } catch {
            case x => msg("Site is unavailable"); None
        }
    }

    override def onHandleIntent(intent: Intent): Unit = {
        val task = intent.getSerializableExtra("task").asInstanceOf[DownloadTask]

        lock.synchronized {
            progressStart()
            task match {
                case DownloadSlideTask(uri) => {
                    download(uri) match {
                        case Some(uri) => {
                            makeicon(uri)
                            msg("Image refresh completed: %s".format(uri))
                            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(GuideCaseMessage.GuideCaseBroadcast) {
                                putExtra("message", GuideCaseMessage.update)
                            })
                        }

                        case None => {
                            msg("Slide refresh failed")
                        }
                    }
                }

                case DownloadSlidesTask(uri) => {
                    download(uri) match {
                        case Some(uri) => {
                            state(uri, state(uri).downloading(true))
                            progressRange(slides(uri).size)

                            for {
                                (title, description, image) <- slides(uri)
                            } {
                                log("image: " + image.toString)
                                download(image) match {
                                    case Some(uri) => makeicon(uri)
                                    case None      => None
                                }

                                progressValue(1)
                            }

                            state(uri, state(uri).local(true).downloading(false).error(false))
                            index.resetlocal
                            log("uri2title: " + index.uri2slidetitle(uri.toString))
                            msg("Slides refresh completed: %s".format(index.uri2slidetitle(uri.toString)))

                        }
                        case None => {
                            state(uri, state(uri).downloading(false).error(true))
                            msg("Slides refresh failed: %s".format(index.uri2slidetitle(uri.toString)))
                        }
                    }
                    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(GuideCaseMessage.GuideCaseBroadcast) {
                        putExtra("message", GuideCaseMessage.update)
                    })
                }

                case DownloadIndexTask(uri) => {
                    download(uri)
                    index = new RemoteIndex(this)
                    progressRange(index.announces.size)
                    (for {
                        ((title, description, image, _), _) <- index.announces
                    } yield {
                        progressValue(1)
                        download(image) match {
                            case Some(uri) =>
                                makeicon(uri); msg("Icon refresh completed: %s".format(title)); Some(uri)
                            case None      => msg("Icon refresh failed: %s".format(title)); None
                        }
                    }).find(_ == None).flatten.size match {
                        case 0 =>
                            msg("Index refreshe failed: %s".format(uri)); false
                        case _ => {
                            true
                        }
                    }
log("download index finished")
                    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(GuideCaseMessage.GuideCaseBroadcast) {
                        putExtra("message", GuideCaseMessage.newindex)
                    })
                }

                case RemoveSlidesTask(uri) => {
                    log(uri.toString)
                    (for {
                        slide <- try { slides(uri) } catch { case _ => List() } //.tail
                    } yield {
                        slide match {
                            case (title, desctription, image) => {
                                log("remove " + image)
                                try {
                                    deleteFile(uriToCache(uri))
                                } catch {
                                    case x => log("Image %s doesn't exist".format(uri))
                                }
                                try {
                                    deleteFile(uriToCache(uri, "icon"))
                                } catch {
                                    case x => log("Icon %s doesn't exist".format(uri))
                                }
                            }
                        }
                    })
                    try {
                        deleteFile(uriToCache(uri))
                    } catch {
                        case x => log("Slideset %s doesn't exist".format(uri))
                    }
                    state(uri, state(uri).local(false))
                    index.resetlocal
                    log("remove finished, encourage callback")

                    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(GuideCaseMessage.GuideCaseBroadcast) {
                        putExtra("message", GuideCaseMessage.update)
                    })

                }
            }
            progressFinish()
        }
    }

    def kickService(task: DownloadTask) = startService(new Intent(this, classOf[GuideCaseService]) {
        putExtra("task", task)
    })

    override def onCreate(): Unit = {
        super.onCreate()
        indexstate = try {
            new java.io.ObjectInputStream(openFileInput("indexstate.dat")).readObject.asInstanceOf[Map[String, State]]
        } catch {
            case x => {
                log(x)
                (for {
                    ((_, _, _, uri), _) <- index.announces
                } yield {
                    log("exist: " + uriToCache(uri) + " " + new File(uriToCache(uri)))
                    (uri.toString -> new State(new File(uriToCache(uri)) exists, false, false, 0))
                }).toMap
            }
        }

        for {
            (uri, State(false, true, _, _)) <- indexstate
        } {
            msg("Resume downloading of: " + index.uri2slidetitle(uri))
            kickService(new DownloadSlidesTask(new URI(uri)))
        }

        log("indexstate: " + indexstate)
    }

    override def onDestroy(): Unit = {
        Toast.makeText(this, "Service has done", Toast.LENGTH_SHORT).show();
    }

    // ticket : move index icons in different place to have a possibility clean index in the case if slides was  removed before update
}

