package ru.iig.guidecase

import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceActivity

class GuideCaseSettings extends PreferenceActivity
  with SharedPreferences.OnSharedPreferenceChangeListener {

  override protected def onCreate(icicle: Bundle) {
    try {
    super.onCreate(icicle)
    println("----------1")
    getPreferenceManager.setSharedPreferencesName("GuideCase")
    println("----------2")
    addPreferencesFromResource(R.xml.preferences)
    println("----------3")
    getPreferenceManager.getSharedPreferences registerOnSharedPreferenceChangeListener this
    println("----------4")

    //PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
    } catch {
      case x => println(x)
    }
  }

  override protected def onResume() {
    super.onResume()
  }

  override protected def onDestroy() {
    getPreferenceManager.getSharedPreferences unregisterOnSharedPreferenceChangeListener this
    super.onDestroy()
  }

  def onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
  }
}
