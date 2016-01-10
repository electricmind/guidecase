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

trait GuideCaseMenu extends Activity
{

  def menuid = R.menu.menu

  override
  def onCreateOptionsMenu(menu : Menu) : Boolean = {
    val inflater : MenuInflater = getMenuInflater();
    inflater.inflate(menuid, menu)
    return true;
  }

  
  def reload() = {}

  override 
  def onOptionsItemSelected(item: MenuItem): Boolean =
    item.getItemId match {
      case R.id.help => {
        println("Help!!")
        true
      }

      case R.id.reload => {
        println("Settings!!")
        reload
        true
      }

      case R.id.settings => {
        println("Settings!!")
        startActivity(new Intent(this, classOf[GuideCaseSettings]))
        true
      }

      case x => {
        println("Nobody knows: " + x)
        false
      }
    }
}

