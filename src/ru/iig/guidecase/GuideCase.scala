package ru.iig.guidecase

import android.app.Activity
import android.os.Bundle
import scala.annotation.tailrec
import scala.util.Random
import android.widget.TextView
import android.view.View
import java.net._
import android.content.SharedPreferences
import android.content.Context
import android.view.{Menu, MenuItem, MenuInflater}
import android.content.Intent

class GuideCase extends Activity with GuideCaseMenu
{
    /** Called when the activity is first created. */

    override def menuid = R.menu.menugreetings


    override
    
    
    
    
    def onCreate(savedInstanceState : Bundle)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.guidecase)
        val s : Definition.Tag = "10.0"
        log(Runtime.getRuntime().maxMemory().toString)
    }

    def doEnter(view : View) = {
      log("do enter")
      startActivity(new Intent(this, classOf[GuideCaseSearch]))
    }
}

