package ru.iig.guidecase

import android.app.Activity
import android.os.{Bundle, Binder, IBinder}
import scala.annotation.tailrec
import scala.util.Random
import android.view.View
import java.net._
import android.view.{Menu, MenuItem, MenuInflater, LayoutInflater, ViewGroup}
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.AbsListView
import android.widget.BaseAdapter

import android.graphics.Bitmap.CompressFormat

import android.content.{SharedPreferences, Context, Intent, ServiceConnection, ComponentName}

import android.graphics.{Bitmap, BitmapFactory}
import ru.iig.guidecase.Definition._

// ticket : include url into refresh request to avoid fake request

class Data(context : GuideCaseSlides) {
    lazy val intent = context.getIntent()
    lazy val slides : Slides = context.service.get.slides(new URI(context.reference))
    lazy val adapter = new BaseAdapter {
        def getCount = slides.size

        def getItem(position : Int) : Object = null

        def getItemId(position :  Int) : Long = 0

        def getView(position : Int, convertView : View , parent: ViewGroup ) : View = {
             val inflater : LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]

             val viewGroup : ViewGroup = if (convertView == null) {
                inflater.inflate(R.layout.guidecaseslidesitem, null).asInstanceOf[ViewGroup] 
             } else {
                convertView.asInstanceOf[ViewGroup]
             }

             val imageView : ImageView = viewGroup.findViewById(R.id.image).asInstanceOf[ImageView]

             imageView.setImageBitmap(slides(position) match {
                 case (title, description, image) => {
                     context.service.get.imageicon(image) 
                 }
             })

             viewGroup
        }
    }
}

class GuideCaseSlides extends Activity with GuideCaseMenu with AdapterView.OnItemClickListener
{
    lazy val reference = getIntent().getStringExtra("data")
    var index = 0
    /* ----- invariant */
    var progressid : Int = 0
    var service : Option[GuideCaseService] = None
    val serviceconnection : ServiceConnection = new ServiceConnection() {

        override
        def onServiceConnected(className : ComponentName, binder : IBinder ) : Unit = {
            service = Some(binder.asInstanceOf[GuideCaseBinder].getService())
            service match {

                case Some(x) =>  {
                    GuideCaseSlides.this.load
                    progressid = x.regProgress(progress)
                }
                case None => {}
            }

        }

        override
        def onServiceDisconnected(arg0 : ComponentName) : Unit = {
            service = service match {
               case Some(x) => {
                   None
               }
               case None => None
            } 
        }
    }

    override
    def onStart() : Unit =  {
        super.onStart()
        bindService(new Intent(this, classOf[GuideCaseService]), serviceconnection, Context.BIND_AUTO_CREATE)
    }

    override
    def onStop() = {
        super.onStop();
        unbindService(serviceconnection)
                   service.get.unregProgress(progressid)
        /*service = service match {
            case Some(x) => unbindService(serviceconnection); None
            case None => None
        }*/
    }  

    def kickService(task : DownloadTask) = startService(new Intent(this, classOf[GuideCaseService]) {
        putExtra("task", task)
    })                

    lazy val progress = new Progress(this)
    /* ---------------- */

    lazy val gridview = findViewById(R.id.grid).asInstanceOf[GridView];
    var data = new Data(this)

    override
    def reload() = {
      kickService(DownloadSlidesTask(new URI(reference)))
      load
    }

    override
    def onCreate(savedInstanceState : Bundle) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.guidecaseslides)
        index = getIntent().getIntExtra("index",0)
    }

    def load = {
        data = new Data(this)
        gridview.setAdapter(data.adapter);
        gridview.setOnItemClickListener(this)
    }

    override
    def onItemClick(l : AdapterView[_], v : View, position : Int, id : Long) : Unit = {
        val intent : Intent = new Intent(this, classOf[GuideCaseSlide])
        intent.putExtra("data", reference)
        intent.putExtra("index",position)
        
        if (getSharedPreferences("GuideCase", 0).getBoolean("godeep", false)) {
            setResult(Activity.RESULT_OK, intent);
            finish()
        } else {
            startActivity(intent)
        }
    }

    override
    def onBackPressed() = {
        if (getSharedPreferences("GuideCase", 0).getBoolean("godeep", false)) {
           val intent : Intent = new Intent(this, classOf[GuideCaseSlide])
           intent.putExtra("data", reference)
           intent.putExtra("index",index)

           setResult(Activity.RESULT_OK, intent)
           finish()
        } else {
          super.onBackPressed()
        }
    }
}
