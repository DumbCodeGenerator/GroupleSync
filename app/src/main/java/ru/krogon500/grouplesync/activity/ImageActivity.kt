package ru.krogon500.grouplesync.activity

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.AsyncTask
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.*
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.viewpager.widget.ViewPager
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import io.objectbox.relation.ToMany
import kotlinx.android.synthetic.main.image_activity.*
import org.jsoup.Jsoup
import ru.krogon500.grouplesync.App
import ru.krogon500.grouplesync.R
import ru.krogon500.grouplesync.Utils
import ru.krogon500.grouplesync.Utils.getDPI
import ru.krogon500.grouplesync.adapter.ImageAdapter
import ru.krogon500.grouplesync.entity.GroupleBookmark
import ru.krogon500.grouplesync.entity.GroupleChapter
import ru.krogon500.grouplesync.entity.HentaiManga
import ru.krogon500.grouplesync.fragment.HentaiFragment
import ru.krogon500.grouplesync.system_helper.SystemUiHelper
import java.util.regex.Pattern

class ImageActivity : AppCompatActivity() {
    private lateinit var mSettings: SharedPreferences
    private var type: Byte = 0
    private var link: String? = null
    private lateinit var gBookmarksBox: Box<GroupleBookmark>
    private lateinit var gChaptersBox: Box<GroupleChapter>
    private lateinit var gBookmark: GroupleBookmark
    private lateinit var gChapters: ToMany<GroupleChapter>

    private lateinit var hentaiBox: Box<HentaiManga>
    private var hChapters: ToMany<HentaiManga>? = null
    private var chaptersLinks: ArrayList<String>? = null

    private var id: Long = 0
    private var prevId: Long = 0
    private var nextId: Long = 0

    internal lateinit var progressBar: ProgressBar
    internal lateinit var uiHelper: SystemUiHelper

    private var page: Int = 0
    private var onFirst = true

    private var groupleTask: GroupleOnlineImagesTask? = null
    private var hentaiTask: HentaiOnlineImagesTask? = null

    private val pageListener = object : ViewPager.OnPageChangeListener{
        override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
            if(onFirst){
                onPageSelectedAction(position)
                onFirst = false
            }
        }

        override fun onPageSelected(position: Int) {
            onPageSelectedAction(position)
        }

        override fun onPageScrollStateChanged(state: Int) {

        }

    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("pos", view_pager.currentItem)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.image_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when(item?.itemId ?: return false){
            android.R.id.home -> {
                onBackPressed()
                return true
            }
            R.id.rotateScreen -> {
                requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
        return super.onOptionsItemSelected(item)
    }

    @SuppressLint("SetTextI18n", "NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.image_activity)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        mSettings = PreferenceManager.getDefaultSharedPreferences(this)

        val flags = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        uiHelper = SystemUiHelper(this, SystemUiHelper.LEVEL_IMMERSIVE, flags)

        appBar.setOnApplyWindowInsetsListener { v, insets ->
            (v.layoutParams as ViewGroup.MarginLayoutParams).rightMargin = insets.systemWindowInsetRight
            insets.consumeSystemWindowInsets()
        }
        val args = intent.extras ?: return

        fromBrowser = args.containsKey("fromBrowser")
        type = args.getByte("type")

        if(!fromBrowser) {
            id = args.getLong("id")
        } else {
            link = args.getString("link")
            if(args.containsKey("chapters"))
                chaptersLinks = args.getStringArrayList("chapters")
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleLarge)
        val params = CoordinatorLayout.LayoutParams(100.getDPI(applicationContext), 100.getDPI(applicationContext))
        params.gravity = Gravity.CENTER
        rootView.addView(progressBar, params)
        progressBar.visibility = View.VISIBLE

        if(fromBrowser){
            hentaiTask = HentaiOnlineImagesTask(link, mUser, mPass, savedInstanceState).also { it.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR) }
        }else if (type == Utils.GROUPLE) {
            gBookmarksBox = (application as App).boxStore.boxFor()
            gChaptersBox = (application as App).boxStore.boxFor()
            gBookmark = gBookmarksBox.get(args.getLong("bookmark_id")) ?: return
            gChapters = gBookmark.chapters.also { it.sortBy { chapter -> chapter.order } }

            val currentGChapter = gChapters.getById(id) ?: return

            val prevChapIndex = gChapters.indexOfId(id) - 1
            val nextChapIndex = gChapters.indexOfId(id) + 1
            prevId = if (prevChapIndex >= 0) gChapters[prevChapIndex].id else 0
            nextId = if(nextChapIndex < gChapters.size) gChapters[nextChapIndex].id else 0

            page =  currentGChapter.page
            supportActionBar?.title = "Глава ${currentGChapter.vol} – ${currentGChapter.chap}. Страница ${page + 1}"

            if(currentGChapter.saved && currentGChapter.files != null && currentGChapter.files?.size ?: 0 > 0){
                val adapter = ImageAdapter(this, uiHelper, currentGChapter.files!!, gChaptersBox, type)
                view_pager.adapter = adapter

                if (savedInstanceState != null && savedInstanceState.containsKey("pos")) {
                    view_pager.currentItem = savedInstanceState.getInt("pos")
                } else {
                    view_pager.currentItem = adapter.count - page - 1
                }
                progressBar.visibility = View.GONE
                view_pager.addOnPageChangeListener(pageListener)
            }else
                groupleTask = GroupleOnlineImagesTask(currentGChapter.link, savedInstanceState).also { it.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR) }

        } else {
            hentaiBox = (application as App).boxStore.boxFor()
            val currentHManga = hentaiBox.get(id) ?: return
            page = currentHManga.page
            hChapters = currentHManga.origin.target?.relateds.also { it?.sortBy { chapter -> chapter.order } }

            if(hChapters != null && hChapters!!.isNotEmpty()) {
                val prevChapIndex = hChapters!!.indexOfId(id) - 1
                val nextChapIndex = hChapters!!.indexOfId(id) + 1
                prevId = if (prevChapIndex >= 0) hChapters!![prevChapIndex].id else 0
                nextId = if (nextChapIndex < hChapters!!.size) hChapters!![nextChapIndex].id else 0
            }

            supportActionBar?.title = "Страница: ${page + 1}"

            //Log.d("lol", "saved and files: ${currentHManga.saved}/${currentHManga.files?.size}")
            if(currentHManga.saved && currentHManga.files != null){
                val adapter = ImageAdapter(this, uiHelper, currentHManga.files!!, hentaiBox, type)
                view_pager.adapter = adapter
                if (savedInstanceState != null && savedInstanceState.containsKey("pos")) {
                    view_pager.currentItem = savedInstanceState.getInt("pos")
                } else {
                    view_pager.currentItem = adapter.count - page - 1
                }
                progressBar.visibility = View.GONE
                view_pager.addOnPageChangeListener(pageListener)
            }else
                hentaiTask = HentaiOnlineImagesTask(currentHManga.link, mUser, mPass, savedInstanceState).also { it.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR) }

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
    internal inner class GroupleOnlineImagesTask(var link: String?, private var savedInstanceState: Bundle?) : AsyncTask<Void, Void, Boolean>() {
        private var images = ArrayList<String>()

        override fun doInBackground(vararg voids: Void): Boolean? {

            try {
                val mainPage = Jsoup.connect(link).data("mtr", "1").get()
                val script = mainPage.selectFirst("script:containsData(rm_h.init)")
                val content = script.html()
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

        override fun onPostExecute(aBoolean: Boolean) {
            if (aBoolean) {
                images.reverse()
                val currentGChapter = gChaptersBox[id] ?: return
                currentGChapter.page_all = images.size
                gChaptersBox.put(currentGChapter)

                val adapter = ImageAdapter(this@ImageActivity, uiHelper, images,
                        gChaptersBox, type)
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
            groupleTask = null
            view_pager.addOnPageChangeListener(pageListener)
        }

        override fun onCancelled() {
            super.onCancelled()
            groupleTask = null
        }
    }

    @SuppressLint("StaticFieldLeak")
    internal inner class HentaiOnlineImagesTask(var link: String?, var mUser: String, private var mPass: String, private var savedInstanceState: Bundle?) : AsyncTask<Void, Void, Boolean>() {
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

        override fun onPostExecute(aBoolean: Boolean) {
            if (aBoolean) {
                images.reverse()

                val adapter: ImageAdapter
                if(!fromBrowser) {
                    val currentHManga = hentaiBox[id] ?: return
                    currentHManga.page_all = images.size
                    hentaiBox.put(currentHManga)

                    adapter = ImageAdapter(this@ImageActivity, uiHelper, images,
                            hentaiBox, type)
                }else
                    adapter = ImageAdapter(this@ImageActivity, uiHelper, images)

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
            view_pager.addOnPageChangeListener(pageListener)
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
        val adapter = view_pager.adapter as? ImageAdapter ?: return
        page = ImageAdapter.currentRange - position - 1
        if (type == Utils.HENTAI) {
            if (page < 0) {
                page = adapter.count - position - 1
                supportActionBar?.title = "Страница: ${page + 1}/${adapter.count - ImageAdapter.currentRange}"
            }else{
                supportActionBar?.title = "Страница: ${page + 1}/${ImageAdapter.currentRange}"
            }

            if (position == 0) adapter.onExtremePos(false) else if(position == adapter.count - 1) adapter.onExtremePos(true)
        } else {
            val currentGChapter = gChaptersBox[id] ?: return
            if (page < 0) {
                val prevChapter = gChaptersBox[prevId] ?: return
                val prevVol = prevChapter.vol
                val prevChap = prevChapter.chap

                page = adapter.count - position - 1
                supportActionBar?.title = "Глава $prevVol – $prevChap. Страница ${page + 1}/${adapter.count - ImageAdapter.currentRange}"
            }else{
                supportActionBar?.title = "Глава ${currentGChapter.vol} – ${currentGChapter.chap}. Страница ${page + 1}/${ImageAdapter.currentRange}"
            }

            if (position == 0) adapter.onExtremePos(false) else if(position == adapter.count - 1 && prevId > 0) adapter.onExtremePos(true)
        }
    }
    
    private fun setNextId(): Boolean{
        if(nextId == 0L) return false

        prevId = id
        id = nextId

        val nextChapIndex = if(type == Utils.GROUPLE) gChapters.indexOfId(id) + 1 else hChapters!!.indexOfId(id) + 1
        nextId = if(type == Utils.GROUPLE) {
            if (nextChapIndex < gChapters.size) gChapters[nextChapIndex].id else 0
        } else{
            if(nextChapIndex < hChapters!!.size) hChapters!![nextChapIndex].id else 0
        }
        return true
    }
    
    private fun setPrevId(){
        nextId = id
        id = prevId

        val prevChapterIndex = if (type == Utils.GROUPLE) gChapters.indexOfId(id) - 1 else hChapters!!.indexOfId(id) - 1
        prevId = if (prevChapterIndex >= 0) {
            if (type == Utils.GROUPLE)
                gChapters[prevChapterIndex].id
            else
                hChapters!![prevChapterIndex].id
        } else 0
    }

    private fun ImageAdapter.onExtremePos(prevChapter: Boolean) {
        if (type == Utils.GROUPLE) {
            val currentGChapter = if(prevChapter) {
                if(this.count > ImageAdapter.currentRange) setPrevId()
                if(prevId == 0L) return
                gChaptersBox[prevId] ?: return
            }else {
                val readedGChapter = gChaptersBox[id] ?: return
                readedGChapter.page = page
                readedGChapter.readed = true
                gChaptersBox.put(readedGChapter)

                if(!setNextId()) return
                gChaptersBox[id] ?: return
            }

            if(currentGChapter.saved && currentGChapter.files != null){
                if(prevChapter) this.prevChapter(currentGChapter.files!!) else this.nextChapter(currentGChapter.files!!)
            }else{
                if(prevChapter) this.prevChapterOnline(currentGChapter.link, currentGChapter.id) else this.nextChapterOnline(currentGChapter.link, currentGChapter.id)
            }
        } else if(type == Utils.HENTAI && hChapters != null && !fromBrowser){
            val currentHManga = if(prevChapter) {
                if(this.count > ImageAdapter.currentRange) setPrevId()
                if(prevId == 0L) return
                hentaiBox[prevId] ?: return
            } else {
                val readedHManga = hentaiBox[id] ?: return
                readedHManga.readed = true
                readedHManga.page = page
                hentaiBox.put(readedHManga)

                if(!setNextId()) return
                hentaiBox[id] ?: return
            }

            if(currentHManga.saved && currentHManga.files != null){
                if(prevChapter) this.prevChapter(currentHManga.files!!) else this.nextChapter(currentHManga.files!!)
            }else{
                if(prevChapter) this.prevChapterOnline(currentHManga.link, currentHManga.id) else this.nextChapterOnline(currentHManga.link, currentHManga.id)
            }
        } else if(type == Utils.HENTAI && fromBrowser && chaptersLinks != null && !prevChapter){
            val nextIndex = chaptersLinks!!.indexOf(link) + 1
            if(nextIndex < chaptersLinks!!.size) {
                link = chaptersLinks!![nextIndex]
                this.nextChapterOnline(link!!, 0)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        uiHelper.hide()
        if (ImageAdapter.opened) {
            appBar.animate()
                    .apply {
                        alpha(0f)
                        duration = 100
                        startDelay = 100}

            ImageAdapter.opened = false
        }
    }

    override fun onPause() {
        super.onPause()
        if(fromBrowser) return

        if (type == Utils.HENTAI && hChapters != null && hChapters?.isNotEmpty() == true) {
            val hentaiManga = (if(view_pager.currentItem < ImageAdapter.currentRange && id > 0) hentaiBox[id] else if(prevId > 0) hentaiBox[prevId] else null) ?: return
            hentaiManga.page = page
            hentaiBox.put(hentaiManga)
        } else if (type == Utils.GROUPLE) {
            val gChapter = (if(view_pager.currentItem < ImageAdapter.currentRange && id > 0) gChaptersBox[id] else if(prevId > 0) gChaptersBox[prevId] else null) ?: return
            gChapter.page = page
            gChaptersBox.put(gChapter)
        }
    }

    companion object {
        private var mUser: String = HentaiFragment.mUser
        private var mPass: String = HentaiFragment.mPass

        internal var fromBrowser = false
    }
}
