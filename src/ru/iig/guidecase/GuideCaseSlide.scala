package ru.iig.guidecase

import java.net.URI

import android.app.Activity
import android.content.{ BroadcastReceiver, ComponentName, Context, Intent, IntentFilter, ServiceConnection }
import android.graphics.Bitmap
import android.os.{ Bundle, Handler, IBinder }
import android.support.v4.content.LocalBroadcastManager
import android.support.v4.view.GestureDetectorCompat
import android.view.{ Menu, MenuItem, MotionEvent, ScaleGestureDetector, View }
import android.view.GestureDetector.{ OnDoubleTapListener, OnGestureListener }
import android.view.animation.{ AnimationUtils, TranslateAnimation }
import android.widget.{ ImageView, ProgressBar, TextView, ToggleButton, ViewAnimator }

// ticket : accomplish reader

class SlidesCache(activity: GuideCaseSlide) {
    lazy val slides: Definition.Slides = activity.service.get.slides(activity.uri)
}

class GuideCaseSlide extends Activity with GuideCaseMenu with OnGestureListener with OnDoubleTapListener with ScaleGestureDetector.OnScaleGestureListener //with GestureOverlayView.OnGestureListener //with OnDoubleTapListener
{

    override def menuid = R.menu.menuslide
    var togglebutton: ToggleButton = null
    lazy val height = findViewById(R.id.viewanimator).getHeight()

    override def onCreateOptionsMenu(menu: Menu): Boolean = {
        super.onCreateOptionsMenu(menu)
        //this.menu = menu
        menu.findItem(R.id.slideshow).asInstanceOf[MenuItem].setActionProvider(
            new ToggleButtonActionProvider(this))
        return true;
    }

    /* ----- invariant */
    var service: Option[GuideCaseService] = None
    var progressid: Int = 0

    val handler: Handler = new Handler()
    val serviceconnection: ServiceConnection = new ServiceConnection() {

        override def onServiceConnected(className: ComponentName, binder: IBinder): Unit = {
            service = Some(binder.asInstanceOf[GuideCaseBinder].getService())
            service match {
                case Some(x) => {
                    GuideCaseSlide.this.publish()
                    progressid = x.regProgress(progress)
                    //index = service.get.state(uri).p
                }
                case None => {}
            }
        }

        override def onServiceDisconnected(arg0: ComponentName): Unit = {
            service = service match {
                case Some(x) => {
                    x.unregProgress(progressid)
                    None
                }
                case None => None
            }
        }
    }

    override def onStart(): Unit = {
        log("Start Slide")
        super.onStart()
        bindService(new Intent(this, classOf[GuideCaseService]), serviceconnection, Context.BIND_AUTO_CREATE)
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastreceiver, new IntentFilter(GuideCaseMessage.GuideCaseBroadcast));
        //height = findViewById(R.id.content).getHeight()

        /*
        service match {
	    case Some(x) => index = x.state(uri).p
            case None => {}
        } */

    }

    override def onStop() = {
        log("Stop Slise")
        super.onStop();
        unbindService(serviceconnection)
        service.get.unregProgress(progressid)

        val settings = getSharedPreferences("GuideCase", 0)
        val editor = settings.edit
        editor.putString("slideshowperiod", slideshowperiod.toString)
        editor.commit()

        handler.removeCallbacks(slideshowlast)

        /*service = service match {
            case Some(x) => unbindService(serviceconnection); None
            case None => None
        }*/
    }

    def kickService(task: DownloadTask) = startService(new Intent(this, classOf[GuideCaseService]) {
        putExtra("task", task)
    })

    lazy val progress = new Progress(this)

    lazy val broadcastreceiver = new BroadcastReceiver() {
        override def onReceive(context: Context, intent: Intent) = {
            intent.getIntExtra("message", 0) match {
                case GuideCaseMessage.update => {
                    // a special case
                    slides = new SlidesCache(GuideCaseSlide.this)
                    //index = if (index > slides.slides.size - 1) 0 else index
                    publish()
                }
                case _ => {}
            }
        }
    }

    override def onDestroy(): Unit = {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastreceiver);
        super.onDestroy();
    }

    /* ---------------- */
    lazy val uri = new URI(getIntent().getStringExtra("data"))

    var slides = new SlidesCache(this)

    var index = 0
    var godeep: Boolean = false
    var slot = 0
    var isdown = false

    override def onTouchEvent(event: MotionEvent) = {
        log(event.toString)
        event.getActionMasked() match {
            case MotionEvent.ACTION_DOWN => isdown = true
            case MotionEvent.ACTION_UP   => isdown = false
            case _                       => {}
        }
        mDetector.onTouchEvent(event)
        mScaleDetector.onTouchEvent(event)
        super.onTouchEvent(event)
    }

    override def reload() = {
        slides.slides(index) match {
            case (_, _, uri) => {
                kickService(new DownloadSlideTask(uri))
            }
        }
    }

    override def onActivityResult(requestCode: Int, resultCode: Int, intent: Intent) = if (intent != null) {
        index = intent.getIntExtra("index", index)
        findViewById(R.id.content).setVisibility(View.VISIBLE)
        findViewById(R.id.busy).setVisibility(View.GONE)
        
        log("%d %d %d".format(requestCode, resultCode, index));
    }

    var mDetector: GestureDetectorCompat = null

    override def onDown(e: MotionEvent) = true

    override def onFling(e1: MotionEvent, e2: MotionEvent, velocityx: Float, velocityy: Float) = {
        log("fling %f %f %f".format(velocityx, velocityy, velocityx / velocityy))
        (velocityx, velocityy, Math.abs(velocityx / velocityy)) match {
            case (side, _, ratio) if (side < -750 && ratio > 2) => {
                log("next " + side)
                //(if (slot % 2 == 1) findViewById(R.id.slot1) else findViewById(R.id.slot)).animate.setDuration(-(1800/side*750).toLong).x(-1800).start()
                index = if (index >= slides.slides.size - 1) 0 else index + 1
                if (slideshow) {
                    slideshowperiod = ((slideshowperiod + System.currentTimeMillis() - slideshowtime) / 2.0).toLong
                    handler.removeCallbacks(slideshowlast)
                    doSlideshow()
                    log("period: " + slideshowperiod)
                }
                publishNext(side)
            }
            case (side, _, ratio) if (side > 750 && ratio > 2) => {
                log("prev " + side)
                index = if (index > 0) index - 1 else slides.slides.size - 1
                if (slideshow) {
                    slideshowperiod = slideshowperiod * 2
                    handler.removeCallbacks(slideshowlast)
                    doSlideshow()
                    log("period: " + slideshowperiod)
                }
                publishPrev(side)
            }
            case (_, side, ratio) if (side < -750 && ratio < 0.5) => {
                log("grid " + side)
                if (getSharedPreferences("GuideCase", 0).getBoolean("useverticalflick", false)) {
                    onSingleTapUp(e1)
                }
            }
            case (_, side, ratio) if (side > 750 && ratio < 0.5) => {
                log("finish " + side)
                if (getSharedPreferences("GuideCase", 0).getBoolean("useverticalflick", false)) {
                    finish()
                }
            }
            case _ => {
                log("nothing")
                (if (slot % 2 == 1) findViewById(R.id.image1) else findViewById(R.id.image)).setTranslationX(0f)
            }
        }
        false;
    }

    var savex = 0f
    var signx = 0f
    override def onScroll(e1: MotionEvent, e2: MotionEvent, distancex: Float, distancey: Float) = {
        log("scroll %f %f".format(distancex, distancey));
        val viewx = (if (slot % 2 == 1) findViewById(R.id.slot1) else findViewById(R.id.slot))
        savex = savex - distancex
        signx = distancex

        //
        viewx.setTranslationX(-distancex + viewx.getTranslationX())
        //viewx.animate.cancel()
        //viewx.animate.setDuration(5).x(savex).start()

        val view = scaleview
        val scale = view.getScaleY()
        val Y = view.getTranslationY()

        log("Height " + height + " " + (scale * height - height) / 2.0 + " " + (Y - distancey) * scale + " " + (Y - distancey) + " " + distancey + " " + scale)

        if ((scale * height - height) / 2d > (Math.abs(Y - distancey))) {
            view.setTranslationY(-distancey * scale + Y)
        } else {
            view.setTranslationY((Math.signum(-distancey + Y) * (scale * height - height) / 2f))
        }
        false
    }

    override def onLongPress(e: MotionEvent) = {}

    override def onShowPress(e: MotionEvent) = {
        log("onShowPress")
    }

    override def onSingleTapUp(e: MotionEvent) = if (godeep) {
        log("onSingleTap");
        findViewById(R.id.content).setVisibility(View.GONE)
        findViewById(R.id.busy).setVisibility(View.VISIBLE)
        startActivityForResult(new Intent(this, classOf[GuideCaseSlides]) {
            putExtra("data", uri.toString)
            putExtra("index", index)
        }, 0)
        false
    } else false

    override def onDoubleTap(e: MotionEvent) = {
        log("onDoubleTap " + e.toString)
        true
    }

    override def onDoubleTapEvent(e: MotionEvent) = {
        log("onDoubleTapEvent " + e.toString)
        true
    }

    override def onSingleTapConfirmed(e: MotionEvent) = {
        log("nSingleTapConfirmed" + e.toString)
        true
    }

    var slideshow: Boolean = false
    var slideshowperiod = 1000l
    var slideshowtime = 0l
    var slideshowlast: Runnable = null
    var slideshowprogress: Runnable = null

    def doSlideshow(postpone: Boolean = false): Unit = {
        if (postpone) {
            slideshowperiod = System.currentTimeMillis() - slideshowtime
        } else {
            slideshowtime = System.currentTimeMillis()
        }
        slideshowlast = new Runnable {
            def run() {
                if (isdown) {
                    doSlideshow(true)
                } else {
                    doNext(null)
                    if (slideshow) {
                        doSlideshow()
                    }
                }
            }
        }
        handler.postDelayed(slideshowlast, if (postpone) 100 else slideshowperiod)
    }

    def doSlideshowProgress(): Unit = {
        slideshowprogress = new Runnable() {
            def run() {
                val progressbar = findViewById(R.id.slideshowprogress).asInstanceOf[ProgressBar]
                progressbar.setProgress(((System.currentTimeMillis() - slideshowtime).toDouble * 100 / slideshowperiod).toInt)
                progressbar.setMax(100)

                //log(((System.currentTimeMillis()-slideshowtime).toDouble * 100 / slideshowperiod ).toInt + " " + progressbar.getMax())    
                if (slideshow) {
                    doSlideshowProgress()
                }
            }
        }
        handler.postDelayed(slideshowprogress, 30)
    }

    def slideshowstate(state: Boolean) = if (state) {
        slideshow = true
        findViewById(R.id.content).setKeepScreenOn(true)
        doSlideshow()
        doSlideshowProgress()
        findViewById(R.id.slideshowprogress).setVisibility(View.VISIBLE)
        slideshow
    } else {
        slideshow = false
        findViewById(R.id.content).setKeepScreenOn(false)
        handler.removeCallbacks(slideshowlast)
        handler.removeCallbacks(slideshowprogress)
        findViewById(R.id.slideshowprogress).setVisibility(View.GONE)
        slideshow
    }

    def onToggleSlideshow(view: View): Unit = slideshowstate(view.asInstanceOf[ToggleButton].isChecked())

    override def onResume() {
        /*onToggleSlideshow(menu.findItem(R.id.slideshow))*/
        log("Resume Slide")
        super.onResume()
    }

    override def onSaveInstanceState(bundle: Bundle) = {
        bundle.putBoolean("slideshow", slideshow)
        bundle.putInt("index", index)
        super.onSaveInstanceState(bundle)
    }

    override def onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.guidecaseslide)

        val intent: Intent = getIntent();

        val settings = getSharedPreferences("GuideCase", 0)
        godeep = settings.getBoolean("godeep", false)

        if (settings.getBoolean("hidebutton", false)) {
            findViewById(R.id.prevbutton).asInstanceOf[View].
                setVisibility(View.GONE)
            findViewById(R.id.nextbutton).asInstanceOf[View].
                setVisibility(View.GONE)
        } else {
            findViewById(R.id.prevbutton).asInstanceOf[View].
                setVisibility(View.VISIBLE)
            findViewById(R.id.nextbutton).asInstanceOf[View].
                setVisibility(View.VISIBLE)
        }

        mDetector = new GestureDetectorCompat(this, this)
        mDetector.setOnDoubleTapListener(this)

        mScaleDetector = new ScaleGestureDetector(this, this)

        if (savedInstanceState != null) {
            index = savedInstanceState.getInt("index", 0)
            slideshow = savedInstanceState.getBoolean("slideshow", false)
        } else {
            getIntent().getIntExtra("index", 0)
        }

        slideshowperiod = getSharedPreferences("GuideCase", 0).
            getString("slideshowperiod", "1000").toLong
        slideshowstate(slideshow)
        //height = findViewById(R.id.content).getHeight()

    }

    var mScaleDetector: ScaleGestureDetector = null

    def scaleview = if (getSharedPreferences("GuideCase", 0).getBoolean("scalewholeframe", false)) {
        findViewById(R.id.viewanimator)
    } else {
        if (slot % 2 == 1) findViewById(R.id.slot1) else findViewById(R.id.slot)
    }

    override def onScale(detector: ScaleGestureDetector) = {
        log("onscale " + detector + " " + detector.getScaleFactor())

        val (scalex, scaley) = getSharedPreferences("GuideCase", 0).getBoolean("saveaspectratio", true) match {
            case true  => (detector.getScaleFactor(), detector.getScaleFactor())
            case false => (detector.getCurrentSpanX() / detector.getPreviousSpanX(), detector.getCurrentSpanY() / detector.getPreviousSpanY())
        }

        scaleview.getScaleY * scaley match {
            case x if x > 1 && x < 4 => scaleview.setScaleY(x)
            case _                   => {}
        }

        scaleview.getScaleX * scalex match {
            case x if x > 1 && x < 4 => scaleview.setScaleX(x)
            case _                   => {}
        }
        true
    }
    
    override def onScaleBegin(detector: ScaleGestureDetector) = {
        log("onscale " + detector)
        true
    }
    override def onScaleEnd(detector: ScaleGestureDetector) = {
        log("onscale " + detector)
    }

    def doPrev(view: View) = {
        index = if (index > 0) index - 1 else slides.slides.size - 1
        publishPrev(1)
    }

    def doNext(view: View) = {
        index = if (index >= slides.slides.size - 1) 0 else index + 1
        publishNext(1)
    }

    def publish(callback: => Unit = {}) = {
        savex = 0
        slides.slides(index) match {
            case (title, description, uri) => {
                if (slot % 2 == 0) {
                    findViewById(R.id.title).asInstanceOf[TextView].setText(title)
                    findViewById(R.id.description).asInstanceOf[TextView].setText(description)
                    findViewById(R.id.slot).setTranslationX(0)
                    findViewById(R.id.slot).setTranslationY(0)
                    findViewById(R.id.slot).setScaleX(1)
                    findViewById(R.id.slot).setScaleY(1)
                    findViewById(R.id.image).asInstanceOf[ImageView].setImageBitmap(null)
                } else {
                    findViewById(R.id.title1).asInstanceOf[TextView].setText(title)
                    findViewById(R.id.description1).asInstanceOf[TextView].setText(description)
                    findViewById(R.id.slot1).setTranslationX(0)
                    findViewById(R.id.slot1).setTranslationY(0)
                    findViewById(R.id.slot1).setScaleX(1)
                    findViewById(R.id.slot1).setScaleY(1)
                    findViewById(R.id.image1).asInstanceOf[ImageView].setImageBitmap(null)
                }

                var bitmap: Bitmap = null
                async {
                    bitmap = service.get.image(uri)
                    service.get.state(uri, service.get.state(uri).position(index))

                    handler.post(new Runnable {
                        def run() = {
                            if (slot % 2 == 0) {
                                findViewById(R.id.image).asInstanceOf[ImageView].setImageBitmap(bitmap)
                            } else {
                                findViewById(R.id.image1).asInstanceOf[ImageView].setImageBitmap(bitmap)
                            }
                            callback
                        }
                    })
                }
            }
        }
    }

    def publishPrev(speed: Float) = {
        val flipper = findViewById(R.id.viewanimator).asInstanceOf[ViewAnimator]
        flipper.setInAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_left));
        if (getSharedPreferences("GuideCase", 0).getBoolean("smoothanimation", true)) {
            val view = (if (slot % 2 == 1) findViewById(R.id.slot1) else findViewById(R.id.slot))
            log("width:" + view.getWidth() + " " + view.getTranslationX())
            val distance = view.getWidth() - view.getTranslationX()
            val animation = new TranslateAnimation(0, distance, 0, 0)
            animation.setDuration(List((distance / speed * 1000).toLong, 30).max)
            flipper.setOutAnimation(animation)
        } else {
            flipper.setOutAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_out_right))
        }
        flipper.showNext()

        slot = slot + 1
        publish()
        log(uri.toString)
    }

    def publishNext(speed: Float) = {
        val flipper = findViewById(R.id.viewanimator).asInstanceOf[ViewAnimator]
        flipper.setInAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_right))

        if (getSharedPreferences("GuideCase", 0).getBoolean("smoothanimation", true)) {
            val view = (if (slot % 2 == 1) findViewById(R.id.slot1) else findViewById(R.id.slot))
            log("width:" + view.getWidth() + " " + view.getTranslationX())
            val distance = view.getWidth() - view.getTranslationX()
            val animation = new TranslateAnimation(0, -distance, 0, 0)
            animation.setDuration(List(-(distance / speed * 1000).toLong, 30).max)
            flipper.setOutAnimation(animation)
        } else {
            flipper.setOutAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_out_left))
        }
        flipper.showNext()
        slot = slot + 1

        publish()
        log(uri.toString)
    }

    override def onPause() = {
        log("Pause: " + index + " " + uri)
        service.get.state(uri, service.get.state(uri).position(index))
        super.onPause();
    }
}
