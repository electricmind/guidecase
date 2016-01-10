/*
* Search Activity
*
*/

package ru.iig.guidecase

import java.net.URI
import java.security.MessageDigest

import android.app.Activity
import android.content.{
    BroadcastReceiver,
    ComponentName,
    Context,
    Intent,
    IntentFilter,
    ServiceConnection,
    SharedPreferences
}
import android.os.{ Bundle, Handler, IBinder }
import android.support.v4.content.LocalBroadcastManager
import android.support.v4.view.GestureDetectorCompat
import android.view.{ LayoutInflater, MenuInflater, MenuItem, MotionEvent, View }
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.GestureDetector.OnGestureListener
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.widget.{
    AbsListView,
    AdapterView,
    ArrayAdapter,
    BaseAdapter,
    Button,
    EditText,
    GridView,
    ImageView,
    LinearLayout,
    ListView,
    ProgressBar,
    TextView,
    Toast,
    ToggleButton
}
import android.widget.AdapterView.AdapterContextMenuInfo
import ru.iig.guidecase.Definition.{ Announce, Tag, Tag2Announces, Tag2Tags }
//TODO: can't disable auto enter
//TODO: change default for all options
//TODO: something wrong happened when a message update come - we are entering into a presentation that is not exist

class GuideCaseSearch extends Activity with GuideCaseMenu with OnGestureListener with OnTouchListener {

    var service: Option[GuideCaseService] = None
    var lastrequest: Option[URI] = None
    val serviceconnection: ServiceConnection = new ServiceConnection() {
        var progressid: Int = 0

        override def onServiceConnected(className: ComponentName, binder: IBinder): Unit = {
            service = Some(binder.asInstanceOf[GuideCaseBinder].getService())
            service match {
                case Some(x) => {
                    findViewById(R.id.remote).asInstanceOf[ToggleButton].setChecked(
                        GuideCaseSearch.this.remote(GuideCaseSearch.this.getSharedPreferences("GuideCase", 0).getBoolean("remote", true)))
                    updateTags
                    updateResult
                    progressid = x.regProgress(progress)
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

    lazy val progress = new Progress(this)

    val broadcastreceiver = new BroadcastReceiver() {
        override def onReceive(context: Context, intent: Intent) {
            intent.getIntExtra("message", 0) match {
                case GuideCaseMessage.newindex => {
                    log("newindex")
                    remote.renew
                    updateTags
                    updateResult
                }
                case GuideCaseMessage.update => {
                    remote.renew
                    log("renew");

                    (lastrequest, getSharedPreferences("GuideCase", 0).getBoolean("useautoopen", true)) match {
                        case (Some(uri), true) => lookAt(uri)
                        case (None, _) | (_, false) => if (remote.flag) {
                            findViewById(R.id.gridresult).asInstanceOf[GridView].invalidateViews()
                        } else {
                            updateTags
                            updateResult
                        }
                    }
                }
                case x => { log(x); }
            }
        }
    }

    override def onStart(): Unit = {
        super.onStart()
        bindService(new Intent(this, classOf[GuideCaseService]), serviceconnection, Context.BIND_AUTO_CREATE)
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastreceiver, new IntentFilter(GuideCaseMessage.GuideCaseBroadcast));
    }

    var tag2announces: Tag2Announces = Map()
    var tag2tags: Tag2Tags = Map()
    var result: List[Announce] = List()
    var tags: Array[Tag] = Array()
    var query = List[Definition.Tag]()

    var godeep = false
    object remote {
        var flag = true
        def apply() = flag
        def apply(f: Boolean) = {
            log("flag: " + flag)
            flag = f
            renew
            f
        }
        def renew = {
            (if (flag) service.get.remoteIndexes else service.get.localIndexes) match {
                case (t2as, t2ts) => {
                    tag2announces = t2as
                    tag2tags = t2ts
                }
            }
        }
    }

    var scrolled = false
    var prevscroll = 0f

    var width = 0
    val handler: Handler = new Handler();

    override def reload() = kickService(new DownloadIndexTask(new URI("guidecaseindex.dat")))

    def onRemoteClicked(view: View) = {
        log("onToggleRemote")
        remote(view.asInstanceOf[ToggleButton].isChecked)
        updateResult
        updateTags
    }

    var mDetector: GestureDetectorCompat = null
    override def onTouch(view: View, event: MotionEvent) = {
        mDetector.onTouchEvent(event)
    }

    override def onDown(e: MotionEvent) = true

    override def onFling(e1: MotionEvent, e2: MotionEvent, velocityx: Float, velocityy: Float) = false

    override def onScroll(e1: MotionEvent, e2: MotionEvent, distancex: Float, distancey: Float) = {
        log("scroll %f %f".format(distancex, distancey));
        (distancex, distancey) match {
            case (distancex, distancey) => {
                val view = findViewById(R.id.leftcolumn)
                log("scroll 1 %d".format(view.getLayoutParams().width))
                log("scroll 2 %f".format((view.getLayoutParams().width - distancex)))

                view.setLayoutParams(new LinearLayout.LayoutParams(List(width - 20, List(5, (view.getLayoutParams().width + distancex /**1.3*/ ).toInt).max).min, view.getLayoutParams().height))
                prevscroll = distancex - prevscroll
            }
            case _ => true
        }
        false
    }

    override def onLongPress(e: MotionEvent) = {}

    override def onShowPress(e: MotionEvent) = {}

    override def onSingleTapUp(e: MotionEvent) = {
        println("onSingleTap");
        //doTranslate(null)
        false
    }

    /** Called when the activity is first created. */
    override def onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.guidecasesearch)
        //ticket *  : new GuideCaseIndexDownloadTask(this).execute(new URI("guidecaseindex.dat"))

        width = getWindowManager().getDefaultDisplay().getWidth()

        val settings = getSharedPreferences("GuideCase", 0)
        query = settings.getString("query", "").split("""[,\s]+""").toList.filterNot("" == _)
        godeep = settings.getBoolean("godeep", false)
        log("Go deep: " + godeep)

        findViewById(R.id.remote).asInstanceOf[ToggleButton].setChecked(remote())

        val column = findViewById(R.id.leftcolumn)
        log(settings.getInt("width", column.getLayoutParams().width))
        column.setLayoutParams(new LinearLayout.LayoutParams(List(width - 20, List(5, settings.getInt("width", column.getLayoutParams().width)).max).min, column.getLayoutParams().height))

        mDetector = new GestureDetectorCompat(this, this)
        findViewById(R.id.tags).setOnTouchListener(this)
        val gridview = findViewById(R.id.gridresult).asInstanceOf[GridView]
        gridview.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE)

        val context: GuideCaseSearch = this

        registerForContextMenu(gridview)

        log("------------------------------------------- 0 4")
        gridview.setOnItemClickListener(new AdapterView.OnItemClickListener {
            override def onItemClick(l: AdapterView[_], v: View, position: Int, id: Long): Unit = onClick(position)
        })

        val listview = findViewById(R.id.tags).asInstanceOf[ListView]

        listview.setOnItemClickListener(new AdapterView.OnItemClickListener {
            override def onItemClick(l: AdapterView[_], v: View, position: Int, id: Long): Unit = {
                log(position.toString)
                query = query :+ tags(position)
                context.updateTags
                context.updateResult
            }
        })
    }

    def lookAt(uri: URI, page: Int = 0) = if (godeep) this.startActivity(new Intent(this, classOf[GuideCaseSlide]) {
        putExtra("data", uri.toString)
        putExtra("index", page)
    })
    else this.startActivity(new Intent(this, classOf[GuideCaseSlides]) {
        putExtra("data", uri.toString)
    })

    def onClick(position: Int): Unit = result(position) match {
        case (title, description, _, uri) => service.get.state(uri) match {
            case State(true, _, _, page) => lookAt(result(position)._4, page)

            // ticket : click on remote slideset should turn on a view that expose description and offer a button for downloading
            case State(false, false, _, _) => {
                service.get.state(uri, service.get.state(uri).downloading(true))
                Toast.makeText(this, """A slideset "%s" is still remote, attempt loading...""".format(title), Toast.LENGTH_SHORT).show()
                //updateResult
                findViewById(R.id.gridresult).asInstanceOf[GridView].invalidateViews()
                kickService(new DownloadSlidesTask(uri))
                lastrequest = Some(uri)
            }
            case State(false, true, _, _) => {
                Toast.makeText(this, """A slideset "%s" is loading but still remote""".format(title), Toast.LENGTH_SHORT).show()
            }
        }
    }

    def updateTags: Unit = {
        //log("Tags: " + tag2tags)
        val listview = findViewById(R.id.tags).asInstanceOf[ListView]

        log("Remote: " + remote)
        tags = (query match {
            case List() => tag2tags.values.flatten
            case x      => x.map(x => { tag2tags.getOrElse(x, Set()) }).reduce((set, x) => set intersect x).filterNot(query contains _)
        }).toSet.toList.sorted.toArray

        val adapter: ArrayAdapter[String] = new ArrayAdapter[String](this, android.R.layout.simple_list_item_1, tags)

        listview.setAdapter(adapter)

        val querybuttonsview = findViewById(R.id.querybuttons).asInstanceOf[ViewGroup]

        querybuttonsview.removeAllViews()

        query.map(word => querybuttonsview.addView(
            {
                val inflater: LayoutInflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
                val button: Button = inflater.inflate(R.layout.guidecasequerybutton, null).asInstanceOf[Button];
                button.setText(word)

                button.setOnClickListener(new View.OnClickListener {
                    def onClick(view: View) = {
                        val word = view.asInstanceOf[Button].getText()
                        query = query.takeWhile(_ != word) ++ query.dropWhile(_ != word).tail
                        updateTags
                        updateResult
                    }
                })

                button
            }))

    }

    def updateResult = {
        val intent: Intent = getIntent();
        log(result.toString + " " + result.size)

        result = (query match {
            case List() => tag2announces.values.flatten.toSet
            case x => query.map(x => tag2announces.getOrElse(x, Set())).
                reduce(_ intersect _)
        }).toList.sortBy({ case (t, d, im, u) => u })

        val context: Context = this

        val gridview = findViewById(R.id.gridresult).asInstanceOf[GridView]

        val index = gridview.getFirstVisiblePosition();
        log("first visible position:" + index)

        // ticket : make adapter static
        val adapter = new BaseAdapter {
            def getCount = result.size

            def getItem(position: Int): Object = result(position)

            // ticket : imlement kind of view to use convertView

            def getItemId(position: Int): Long = {
                val md = MessageDigest.getInstance("MD5")
                md.update(result(position)._4.toString.getBytes)
                md.digest
            }.toList.take(16).zipWithIndex.foldLeft(1.toLong)({
                case (a, (item, order)) => a + item * Math.pow(8, order - 1).toLong
            })

            def getView(position: Int, convertView: View,
                        parent: ViewGroup): View = {
                val inflater: LayoutInflater = getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE).
                    asInstanceOf[LayoutInflater]
                log("getView!!" + position + " " +
                    service.get.state(result(position)._4).d +
                    " " + service.get.state(result(position)._4).l)
                val viewGroup: ViewGroup = //if (convertView == null) {
                    inflater.inflate(service.get.
                        state(result(position)._4) match {
                            case State(true, _, _, _) =>
                                R.layout.guidecasesearchitemlocal
                            case State(false, true, _, _) =>
                                R.layout.guidecasesearchitemdownloading
                            case State(false, false, false, _) =>
                                R.layout.guidecasesearchitemremote
                            case State(false, false, true, _) =>
                                R.layout.guidecasesearchitemwarning
                        }, null).asInstanceOf[ViewGroup]
                /* } else {
                   convertView.asInstanceOf[ViewGroup]
                }*/

                val imageView: ImageView = viewGroup.findViewById(R.id.image).asInstanceOf[ImageView]

                val textView: TextView = viewGroup.findViewById(R.id.title).asInstanceOf[TextView]
                result(position) match {
                    case (title, description, image, uri) => {
                        textView.setText(title)
                    }
                }

                imageView.setImageBitmap(result(position) match {
                    case (title, description, image, uri) => {
                        service.get.imageicon(image)
                    }
                })

                viewGroup
            }
        }

        gridview.setAdapter(adapter);
        //gridview.setSelectionFromTop(index, top)
        gridview.smoothScrollToPosition(index)
    }

    override def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo) = {
        log("menu info " + menuInfo + " " + v + " " + menuInfo.asInstanceOf[AdapterContextMenuInfo].position)
        super.onCreateContextMenu(menu, v, menuInfo)
        val inflater: MenuInflater = getMenuInflater()
        inflater.inflate(service.get.state(result(menuInfo.asInstanceOf[AdapterContextMenuInfo].position)._4) match {
            case State(true, _, _, _)  => R.menu.context_local
            case State(false, _, _, _) => R.menu.context_remote
        }, menu)
    }

    def kickService(task: DownloadTask) = startService(new Intent(this, classOf[GuideCaseService]) {
        putExtra("task", task)
    })

    override def onContextItemSelected(item: MenuItem) = {
        val info: AdapterContextMenuInfo = item.getMenuInfo().asInstanceOf[AdapterContextMenuInfo]
        item.getItemId() match {
            case R.id.load => {
                log("Load info ID: " + info.position)
                //findViewById(R.id.progressbar).asInstanceOf[ProgressBar].setVisibility(View.VISIBLE) 
                //new GuideCaseGridDownloadTask(this,info.position).execute(result(info.position)._4)

                kickService(new DownloadSlidesTask(result(info.position)._4)) //, x => { handler.post(new Runnable { def run() : Unit = { println(" ----------------- Loading has been finished"); remote.renew; updateTags; updateResult; } } )}))
                log("Task started")
                true
            }

            case R.id.delete => {
                log("Delete info ID: " + info.position)
                findViewById(R.id.progressbar).asInstanceOf[ProgressBar].setVisibility(View.VISIBLE)

                kickService(new RemoveSlidesTask(result(info.position)._4)) //, x => {  println(" ------------!!"); handler.post(new Runnable { def run() : Unit = { println(" ------------- Removing has been finished"); remote.renew; updateTags; updateResult; } } )}))
                true
            }

            case R.id.lookat => {
                log("Look at info ID: " + info.position);
                onClick(info.position)
                true
            }

            case _ => super.onContextItemSelected(item)
        }
    }

    def doQuery(view: View) = {
        findViewById(R.id.query).asInstanceOf[EditText].setFocusable(true)
    }

    def doSearch(view: View) = {
        log("do search" + view.toString)
        updateResult
    }

    def doClear(view: View) = {
        log("do clear" + view.toString)
        query = List[Definition.Tag]()
        updateTags
        updateResult
    }

    def doUp(view: View) = {
        log("do up" + view.toString)
        query = query.dropRight(1)
        updateTags
        updateResult
    }

    override def onStop() = {
        super.onStop();
        val settings: SharedPreferences = getSharedPreferences("GuideCase", 0);
        val editor: SharedPreferences.Editor = settings.edit();

        editor.putString("query", query.mkString(", "))
        editor.putBoolean("remote", remote())
        editor.putInt("width", findViewById(R.id.leftcolumn).getLayoutParams().width)

        log("save %d".format(findViewById(R.id.leftcolumn).getLayoutParams().width))
        unbindService(serviceconnection)
        editor.commit();
    }

    override def onBackPressed() = {
        log("do back")
        if (query.size == 0) {
            super.onBackPressed()
        } else {
            query = query.dropRight(1)
            updateTags
            updateResult
        }
    }

    override def onDestroy(): Unit = {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastreceiver);
        super.onDestroy();
    }
}
