package ru.krogon500.grouplesync.activity

import android.annotation.SuppressLint
import android.content.Intent.ACTION_VIEW
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.AsyncTask
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager.widget.ViewPager
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import kotlinx.android.synthetic.main.image_activity.*
import org.jsoup.Jsoup
import ru.krogon500.grouplesync.App
import ru.krogon500.grouplesync.R
import ru.krogon500.grouplesync.Utils
import ru.krogon500.grouplesync.Utils.getDPI
import ru.krogon500.grouplesync.Utils.getVolAndChapter
import ru.krogon500.grouplesync.adapter.ImageAdapter
import ru.krogon500.grouplesync.entity.HentaiManga
import ru.krogon500.grouplesync.fragment.HentaiFragment
import ru.krogon500.grouplesync.system_helper.SystemUiHelper
import java.io.File
import java.net.URL
import java.util.*
import java.util.regex.Pattern

class ImageActivity : AppCompatActivity() {
    private lateinit var mSettings: SharedPreferences
    internal var type: Byte = 0
    private lateinit var root: String
    internal var link: String? = null
    internal var id: Long? = null
    private var ids: ArrayList<Long>? = null
    internal var chapters: ArrayList<String>? = null
    private var paths = LinkedHashMap<String, String>()
    internal lateinit var progressBar: ProgressBar
    internal lateinit var uiHelper: SystemUiHelper
    private lateinit var mUser: String
    private lateinit var mPass: String

    private var prevvol: Int = 0
    private var prevchapter: Int = 0
    private var page: Int = 0
    private var onFirst = true
    private var online: Boolean = false
    private lateinit var mContext: ImageActivity

    private var groupleTask: GroupleOnlineImagesTask? = null
    private var hentaiTask: HentaiOnlineImagesTask? = null

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("pos", view_pager.currentItem)
    }

    @SuppressLint("SetTextI18n", "NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.image_activity)
        mSettings = PreferenceManager.getDefaultSharedPreferences(this)

        val flags = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        uiHelper = SystemUiHelper(this, SystemUiHelper.LEVEL_IMMERSIVE, flags)
        mContext = this


        view_pager.offscreenPageLimit = 1
        rl.setOnApplyWindowInsetsListener { v, insets ->
            (v.layoutParams as ViewGroup.MarginLayoutParams).rightMargin = insets.systemWindowInsetRight
            insets.consumeSystemWindowInsets()
        }

        val adapter: ImageAdapter
        val action = intent.action
        val data = intent.dataString
        val args : Bundle
        if(ACTION_VIEW == action && data != null){
            fromBrowser = true
            type = if (data.contains("henchan")) Utils.HENTAI else Utils.GROUPLE
            online = true
            link = if (type == Utils.GROUPLE) data.split("#")[0] else data
            if(type == Utils.HENTAI) {
                mUser = mSettings.getString("user_h", "nothing")!!
                mPass = mSettings.getString("pass_h", "nothing")!!
                val pattern = Pattern.compile("/\\d+")
                val matcher = pattern.matcher(data)
                if (matcher.find())
                    id = matcher.group(0).substring(1).toLong()
            }else{
                val split = data.split("=")
                if (split.size == 2)
                    page = split[1].toInt()
            }

        }else {
            mUser = HentaiFragment.mUser
            mPass = HentaiFragment.mPass
            args = intent.extras!!
            fromBrowser = args.containsKey("fromBrowser")
            type = args.getByte("type")
            online = args.getBoolean("online")
            link = args.getString("link")
            id = args.getLong("id")

            if (args.containsKey("ids")) {
                @Suppress("UNCHECKED_CAST")
                ids = args.get("ids") as ArrayList<Long>
            }

            if (args.containsKey("chapters")) {
                chapters = args.getStringArrayList("chapters")
                currentChapter = if (chapters != null) chapters!!.indexOf(link!!) else 0
            }
            if (args.containsKey("page")) {
                page = args.getInt("page")
            }
        }

        if (type == Utils.GROUPLE) {
            val volAndChap = link!!.getVolAndChapter()
            vol = volAndChap[0]
            chapter = volAndChap[1]
            textView.text = "Глава " + vol + " – " + chapter + ". Страница " + (page + 1)
        } else
            textView.text = "Страница: ${page + 1}"

        rotateScreen.setOnClickListener {
            requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        view_pager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener{
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                onPageScrolled(position)
            }

            override fun onPageSelected(position: Int) {
                onPageSelectedAction(position)
            }

            override fun onPageScrollStateChanged(state: Int) {

            }

        })

        when {
            online -> {
                val layout = findViewById<RelativeLayout>(R.id.rootView)
                progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleLarge)
                val params = RelativeLayout.LayoutParams(100.getDPI(applicationContext), 100.getDPI(applicationContext))
                params.addRule(RelativeLayout.CENTER_IN_PARENT)
                layout.addView(progressBar, params)
                progressBar.visibility = View.VISIBLE

                if (type == Utils.GROUPLE) {
                    root = (Utils.cachePath + File.separator + "info/grouple"
                            + File.separator + id)

                    val rootDir = File(root)

                    if(rootDir.exists())
                        rootDir.listFiles().forEach {
                            paths[it.name.replace(".dat", "")] = it.absolutePath
                        }

                    groupleTask = GroupleOnlineImagesTask(link!!, savedInstanceState)
                    groupleTask!!.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
                } else {
                    hentaiTask = HentaiOnlineImagesTask(link!!, mUser, mPass, savedInstanceState)
                    hentaiTask!!.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
                }
                return

            }
            type == Utils.GROUPLE -> {
                root = (Utils.cachePath + File.separator + "info/grouple"
                        + File.separator + id)

                val rootDir = File(root)
                if(rootDir.exists())
                    rootDir.listFiles().forEach {
                        paths[it.name.replace(".dat", "")] = it.absolutePath
                    }
                adapter = ImageAdapter(this, paths["$vol.$chapter"]!!, uiHelper, id!!)
            }
            else -> {
                root = Utils.getHentaiInfoFile(id!!)
                val linkImages = Utils.getSavedListFile(root)!!
                adapter = ImageAdapter(this, uiHelper, linkImages, chapters, currentChapter, HentaiFragment.mUser, HentaiFragment.mPass)
            }
        }

        view_pager!!.adapter = adapter

        if (savedInstanceState != null && savedInstanceState.containsKey("pos")) {
            view_pager!!.currentItem = savedInstanceState.getInt("pos")
        } else {
            view_pager!!.currentItem = adapter.count - page - 1
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        val position = view_pager.currentItem
        super.onConfigurationChanged(newConfig)
        val adapter = view_pager.adapter as? ImageAdapter ?: return
        adapter.clearViewPos()
        view_pager.postDelayed({ view_pager.currentItem = position }, 100)
    }

    @SuppressLint("StaticFieldLeak")
    internal inner class GroupleOnlineImagesTask(var link: String, private var savedInstanceState: Bundle?) : AsyncTask<Void, Void, Boolean>() {
        private var nextChapter: String? = null
        private var prevChapter: String? = null
        private var images = ArrayList<String>()

        override fun doInBackground(vararg voids: Void): Boolean? {

            try {
                val url = URL(link)
                val protocol = url.protocol
                val host = url.host

                val mainPage = Jsoup.connect(link).data("mtr", "1").get()
                val prevChapEl = mainPage.selectFirst("a.prevChapLink")
                prevChapter = if (prevChapEl.text() == "Предыдущая глава") prevChapEl.attr("href") else null

                val script = mainPage.selectFirst("script:containsData(rm_h.init)")
                val content = script.html()
                val pattern = Pattern.compile("var nextChapterLink = \"(.*)\";")
                val matcher = pattern.matcher(content)
                if (matcher.find()) {
                    nextChapter = String.format("%s://%s%s", protocol, host, matcher.group(1)).replace("?mtr=1", "")
                    if (nextChapter!!.contains("/list/like"))
                        nextChapter = null
                }
                val rows = content.split("\\r?\\n".toRegex())
                var needed = rows[rows.size - 1]
                needed = needed.substring(needed.indexOf('[') + 1, needed.lastIndexOf(']'))
                val parts = needed.split("],")
                for (part in parts) {
                    val link = part.replace("[\\['\"\\]]".toRegex(), "").split(",")
                    val ext = link[2].split("\\?".toRegex())[0]
                    val img = link[1] + link[0] + ext
                    images.add(img)
                }
                return !isCancelled
            } catch (e: Exception) {
                e.printStackTrace(Utils.getPrintWriter())
                return false
            }
        }

        override fun onPostExecute(aBoolean: Boolean?) {
            if (aBoolean!!) {
                images.reverse()
                val adapter = ImageAdapter(mContext, uiHelper, images,
                        chapters, currentChapter, id!!)
                view_pager!!.adapter = adapter
                uiHelper.delayHide(300)

                if (savedInstanceState != null && savedInstanceState!!.containsKey("pos")) {
                    view_pager!!.currentItem = savedInstanceState!!.getInt("pos")
                } else {
                    view_pager!!.currentItem = adapter.count - page - 1
                }
            } else
                uiHelper.show()

            progressBar.visibility = View.GONE
            groupleTask = null
        }

        override fun onCancelled() {
            super.onCancelled()
            groupleTask = null
        }
    }

    @SuppressLint("StaticFieldLeak")
    internal inner class HentaiOnlineImagesTask(var link: String, var mUser: String, private var mPass: String, private var savedInstanceState: Bundle?) : AsyncTask<Void, Void, Boolean>() {
        private var images = ArrayList<String>()

        override fun doInBackground(vararg voids: Void): Boolean? {
            if (!Utils.login(Utils.HENTAI, mUser, mPass))
                return false
            try {
                val mainPage = Utils.getPage(Utils.HENTAI, mUser, mPass, link)
                Log.d("lol", "link: $link")
                val script = mainPage.selectFirst("script:containsData(fullimg)")
                val pattern = Pattern.compile("\"fullimg\":.+")
                val matcher = pattern.matcher(script.html())
                if (matcher.find()) {
                    val needed = matcher.group(0).replace("\"fullimg\":", "")
                    val pattern2 = Pattern.compile("([\"|'])(\\\\?.)*?\\1")
                    val matcher2 = pattern2.matcher(needed)
                    while (matcher2.find()) {
                        images.add(matcher2.group(0).replace("\"", "").replace("'", ""))
                    }
                }
                return !isCancelled
            } catch (e: Exception) {
                Log.e("GroupleSync", e.localizedMessage)
                e.printStackTrace(Utils.getPrintWriter())
                return false
            }
        }

        override fun onPostExecute(aBoolean: Boolean?) {
            if (aBoolean!!) {
                images.reverse()
                val adapter = ImageAdapter(mContext, uiHelper, images,
                        chapters, currentChapter, mUser, mPass)
                view_pager.adapter = adapter
                uiHelper.delayHide(300)

                if (savedInstanceState != null && savedInstanceState!!.containsKey("pos")) {
                    view_pager.currentItem = savedInstanceState!!.getInt("pos")
                } else {
                    view_pager.currentItem = adapter.count - page - 1
                }
            } else
                uiHelper.show()

            progressBar.visibility = View.GONE
            hentaiTask = null
        }

        override fun onCancelled() {
            super.onCancelled()
            hentaiTask = null
        }
    }

    override fun onStop() {
        if (hentaiTask != null) {
            hentaiTask!!.cancel(true)
            hentaiTask = null
        }
        if (groupleTask != null) {
            groupleTask!!.cancel(true)
            groupleTask = null
        }
        super.onStop()
    }

    @SuppressLint("SetTextI18n")
    internal fun onPageSelectedAction(position: Int) {
        val adapter1 = view_pager!!.adapter as? ImageAdapter ?: return
        page = ImageAdapter.offset - position - 1
        if (type == Utils.HENTAI) {
            textView.text = "Страница: ${page + 1}/${ImageAdapter.offset}"
            if (page < 0) {
                page = adapter1.count - position - 1
                textView.text = "Страница: ${page + 1}/${adapter1.count - ImageAdapter.offset}"
            }
            if (position == 0)
                adapter1.onZeroPos()
        } else {
            textView.text = "Глава $vol – $chapter. Страница ${page + 1}/${ImageAdapter.offset}"
            if (page < 0) {
                link = link?.replace("\\d+/\\d+$".toRegex(), "$prevvol/$prevchapter")
                currentChapter = chapters!!.indexOf(link!!)
                page = adapter1.count - position - 1
                textView.text = "Глава $prevvol – $prevchapter. Страница ${page + 1}/${adapter1.count - ImageAdapter.offset}"
            }
            if (adapter1.count > ImageAdapter.offset && position < ImageAdapter.offset) {
                link = link?.replace("\\d+/\\d+$".toRegex(), "$vol/$chapter")
                currentChapter = chapters!!.indexOf(link!!)
            }
            if (position == 0) {
                adapter1.onZeroPos()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    internal fun onPageScrolled(position: Int) {
        val adapter1 = view_pager!!.adapter as? ImageAdapter ?: return
        if (type == Utils.HENTAI && onFirst){
            textView.text = "Страница: ${page + 1}/${ImageAdapter.offset}"
            if (page < 0) {
                page = adapter1.count - position - 1
                textView.text = "Страница: ${page + 1}/${adapter1.count - ImageAdapter.offset}"
            }
            if (position == 0)
                adapter1.onZeroPos()
        }else if (type == Utils.GROUPLE && onFirst) {
            page = ImageAdapter.offset - position - 1
            textView.text = "Глава $vol – $chapter. Страница ${page + 1}/${ImageAdapter.offset}"
            if (page < 0) {
                link = link?.replace("\\d+/\\d+$".toRegex(), "$prevvol/$prevchapter")
                currentChapter = chapters!!.indexOf(link!!)
                page = adapter1.count - position - 1
                textView.text = "Глава $prevvol – $prevchapter. Страница ${page + 1}/${adapter1.count - ImageAdapter.offset}"
            }
            if (adapter1.count > ImageAdapter.offset && position < ImageAdapter.offset) {
                link = link?.replace("\\d+/\\d+$".toRegex(), "$vol/$chapter")
                currentChapter = chapters!!.indexOf(link!!)
            }
            if (position == 0)
                adapter1.onZeroPos()
        }
        onFirst = false
    }

    private fun ImageAdapter.onZeroPos() {
        if (type == Utils.GROUPLE) {

            val gChapter = gChapters.find { it.link == link }
            if(gChapter != null) {
                gChapter.page = page
                gChapter.readed = true
                gChaptersBox.put(gChapter)
            }

            prevvol = vol
            prevchapter = chapter

            if (paths["$vol." + (chapter + 1)] != null) {
                chapter++
                link = link?.replace("\\d+$".toRegex(), chapter.toString())
                this.nextChapter(paths["$vol.$chapter"]!!)
                currentChapter = chapters!!.indexOf(link!!)
            } else if (paths["${vol + 1}.$chapter"] != null || paths["${vol + 1}.${chapter + 1}"] != null) {
                vol++
                if (paths["$vol.$chapter"] != null) {
                    link = link?.replace("\\d+/\\d+$".toRegex(), "$vol/$chapter")
                    this.nextChapter(paths["$vol.$chapter"]!!)
                } else if (paths["$vol.${chapter + 1}"] != null) {
                    chapter++
                    link = link?.replace("\\d+/\\d+$".toRegex(), "$vol/$chapter")
                    this.nextChapter(paths["$vol.$chapter"]!!)
                }
                currentChapter = chapters!!.indexOf(link!!)
            }else {
                if (chapters!!.size > currentChapter + 1) {
                    ImageAdapter.nextChapter = chapters!![currentChapter + 1]
                    link = ImageAdapter.nextChapter
                    val volAndChap = ImageAdapter.nextChapter!!.getVolAndChapter()
                    vol = volAndChap[0]
                    chapter = volAndChap[1]
                    this.nextChapterOnline()
                }
                else
                    ImageAdapter.nextChapter = null
            }

        } else if(type == Utils.HENTAI && ids != null){
            if(!fromBrowser) {
                val hentaiBox: Box<HentaiManga> = (application as App).boxStore.boxFor()
                val hentaiManga = hentaiBox[id ?: return] ?: return
                hentaiManga.readed = true
                hentaiManga.page = page
                hentaiBox.put(hentaiManga)
            }

            val nextCh = ids!!.indexOf(id!!) + 1
            if(ids!!.size > nextCh) {
                id = ids!![nextCh]
                val infoFile = File(Utils.getHentaiInfoFile(id!!))
                if (infoFile.exists()) {
                    this.nextChapter(infoFile.absolutePath)
                }else{
                    ImageAdapter.nextChapter = chapters!![nextCh]
                    this.nextChapterOnline()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        uiHelper.hide()
        if (ImageAdapter.opened) {
            rl.animate().alpha(0.0f).duration = 100
            ImageAdapter.opened = false
        }
    }

    override fun onPause() {
        super.onPause()
        if (type == Utils.HENTAI && chapters != null && !fromBrowser && view_pager.currentItem < ImageAdapter.offset) {
            val hentaiBox : Box<HentaiManga> = (application as App).boxStore.boxFor()
            val hentaiManga = hentaiBox[ids?.get(ImageAdapter.currentChapter) ?: return] ?: return
            hentaiManga.page = page
            hentaiBox.put(hentaiManga)
        } else if (type == Utils.GROUPLE && !fromBrowser && view_pager.currentItem < ImageAdapter.offset) {
            val adapter = view_pager.adapter as? ImageAdapter ?: return

            val curChap = adapter.gChapters.find { it.link == link } ?: return
            curChap.page = page
            adapter.gChaptersBox.put(curChap)
        }
    }

    companion object {
        internal var fromBrowser = false
        internal var vol: Int = 0
        internal var chapter: Int = 0
        internal var currentChapter: Int = 0
    }
}
