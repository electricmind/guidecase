package ru.iig.guidecase

import android.content.Context;
import android.view.ActionProvider;
import android.view.LayoutInflater;
import android.widget.ToggleButton
import android.view.View;
 
class ToggleButtonActionProvider(val context : GuideCaseSlide) extends ActionProvider(context) {

    def onCreateActionView() : View = {
        log("create action")
        var layoutInflater : LayoutInflater= LayoutInflater.from(context);
        var view : View= layoutInflater.inflate(R.layout.play,null);
        //context.togglebutton = view.getViewById(R.layout.slideshow).asInstanceOf[ToggleButton]
        return view;
    }
}
