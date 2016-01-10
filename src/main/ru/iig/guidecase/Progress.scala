package ru.iig.guidecase

import android.app.Activity
import android.os.Handler
import android.widget.ProgressBar
import android.view.View

class Progress(val activity : Activity) {
    lazy val progressbar = activity.findViewById(R.id.progressbar).asInstanceOf[ProgressBar]
    var count = 0
    var range = 1f
    val handler : Handler = new Handler()

    def post(callable : => Unit) = handler.post(new Runnable {
        def run() = callable
    })

    def show(x : Int) = post({
        if (count == 0) {
           progressbar.setVisibility(View.VISIBLE)
           progressbar.setMax(100)
           progressbar.setProgress(0)
        }
        count = count + x
    })
  
    def hide() = post ({
            count = count - 1
            log("Count :" + count)
            if (count == 0) { 
               progressbar.setVisibility(View.GONE)
               log("Turn off progress bar")
            }
    })

    def value(x : Int = 1) = post(progressbar.setProgress((x / range * 100).toInt))

    def rerange(x : Int = 100) = post({
        log("Scale x:" + x)
        log("Scale: " + progressbar.getProgress + " " + (progressbar.getProgress * range / x).toInt)
        progressbar.setProgress((progressbar.getProgress * range / x).toInt)
        range = x
        //progressbar.incrementProgressBy(x)
    })

    def init(c : Int, x : Int, mx : Int) = post({
        progressbar.setVisibility(View.GONE)
        count = c
        if (c > 0) progressbar.setVisibility(View.VISIBLE)
        progressbar.setMax(100)
        range = mx.toFloat
        progressbar.setProgress((x / range).toInt)
    })
}

 