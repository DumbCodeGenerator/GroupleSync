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

    private var nextChapter: Any? = null
    private var prevChapter: Any? = null

    private var id: Long = 0

    internal lateinit var progressBar: ProgressBar
    internal lateinit var uiHelper: SystemUiHelper

    private var page: Int = 0
    private var onFirst = true

    private var groupleTask: GroupleOnlineImagesTask? = null
    private var hentaiTask: HentaiOnlineImagesTask? = null

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

        view_pager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener{
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

        })

        //when {
        //    online -> {
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
                    gBookmark = gBookmarksBox.get(id) ?: return
                    gChapters = gBookmark.chapters.also { it.sortBy { chapter -> chapter.date } }

                    currentGChapter = gChapters.getById(args.getLong("chapter_id")) ?: return

                    val prevChapIndex = gChapters.indexOfId(currentGChapter.id) - 1
                    val nextChapIndex = gChapters.indexOfId(currentGChapter.id) + 1
                    prevChapter = if (prevChapIndex >= 0) gChapters[prevChapIndex] else null
                    nextChapter = if(nextChapIndex < gChapters.size) gChapters[nextChapIndex] else null

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
                    }else
                        groupleTask = GroupleOnlineImagesTask(currentGChapter.link, savedInstanceState).also { it.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR) }

                } else {
                    hentaiBox = (application as App).boxStore.boxFor()
                    currentHManga = hentaiBox.get(id) ?: return
                    page = currentHManga.page
                    hChapters = currentHManga.origin.target?.relateds.also { it?.sortBy { chapter -> chapter.date } }

                    if(hChapters != null && hChapters!!.isNotEmpty()) {
                        val prevChapIndex = hChapters!!.indexOfId(currentHManga.id) - 1
                        val nextChapIndex = hChapters!!.indexOfId(currentHManga.id) + 1
                        prevChapter = if (prevChapIndex >= 0) hChapters!![prevChapIndex] else null
                        nextChapter = if (nextChapIndex < hChapters!!.size) hChapters!![nextChapIndex] else null
                    }

                    supportActionBar?.title = "Страница: ${page + 1}"

                    Log.d("lol", "saved and files: ${currentHManga.saved}/${currentHManga.files?.size}")
                    if(currentHManga.saved && currentHManga.files != null){
                        val adapter = ImageAdapter(this, uiHelper, currentHManga.files!!, hentaiBox, type)
                        view_pager.adapter = adapter
                        if (savedInstanceState != null && savedInstanceState.containsKey("pos")) {
                            view_pager.currentItem = savedInstanceState.getInt("pos")
                        } else {
                            view_pager.currentItem = adapter.count - page - 1
                        }
                        progressBar.visibility = View.GONE
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
            supportActionBar?.title = "Страница: ${page + 1}/${ImageAdapter.offset}"
            if (page < 0) {
                page = adapter1.count - position - 1
                supportActionBar?.title = "Страница: ${page + 1}/${adapter1.count - ImageAdapter.offset}"
            }

            if (position == 0) adapter1.onZeroPos()
        } else {
            supportActionBar?.title = "Глава ${currentGChapter.vol} – ${currentGChapter.chap}. Страница ${page + 1}/${ImageAdapter.offset}"
            if (page < 0) {
                val prevVol = (prevChapter as GroupleChapter).vol
                val prevChap = (prevChapter as GroupleChapter).chap

                page = adapter1.count - position - 1
                supportActionBar?.title = "Глава $prevVol – $prevChap. Страница ${page + 1}/${adapter1.count - ImageAdapter.offset}"
            }

            if (position == 0 && nextChapter != null) adapter1.onZeroPos()
        }
    }

    private fun ImageAdapter.onZeroPos() {
        if (type == Utils.GROUPLE) {
            currentGChapter.page = page
            currentGChapter.readed = true
            gChaptersBox.put(currentGChapter)

            prevChapter = currentGChapter
            currentGChapter = nextChapter as GroupleChapter

            val nextChapIndex = gChapters.indexOfId(currentGChapter.id) + 1
            nextChapter = if(nextChapIndex < gChapters.size) gChapters[nextChapIndex] else null

            if(currentGChapter.saved && currentGChapter.files != null){
                this.nextChapter(currentGChapter.files!!)
            }else{
                this.nextChapterOnline(currentGChapter.link)
            }
        } else if(type == Utils.HENTAI && hChapters != null && !fromBrowser){
            currentHManga.readed = true
            currentHManga.page = page
            hentaiBox.put(currentHManga)

            prevChapter = currentHManga
            currentHManga = nextChapter as HentaiManga

            val nextChapIndex = hChapters!!.indexOfId(currentHManga.id) + 1
            nextChapter = if(nextChapIndex < hChapters!!.size) hChapters!![nextChapIndex] else null

            if(currentHManga.saved && currentHManga.files != null){
                this.nextChapter(currentHManga.files!!)
            }else{
                this.nextChapterOnline(currentHManga.link)
            }
        } else if(type == Utils.HENTAI && fromBrowser && chaptersLinks != null){
            val nextIndex = chaptersLinks!!.indexOf(link) + 1
            if(nextIndex < chaptersLinks!!.size) {
                link = chaptersLinks!![nextIndex]
                this.nextChapterOnline(link!!)
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

        if (type == Utils.HENTAI && hChapters != null && hChapters?.isNotEmpty() == true && view_pager.currentItem < ImageAdapter.offset) {
            val hentaiManga = hentaiBox[currentHManga.id]
            hentaiManga.page = page
            hentaiBox.put(hentaiManga)
        } else if (type == Utils.GROUPLE && view_pager.currentItem < ImageAdapter.offset) {
            val gChapter = gChaptersBox[currentGChapter.id]
            gChapter.page = page
            gChaptersBox.put(gChapter)
        }
    }

    companion object {
        private var mUser: String = HentaiFragment.mUser
        private var mPass: String = HentaiFragment.mPass

        internal var fromBrowser = false
        lateinit var currentGChapter: GroupleChapter
        lateinit var currentHManga: HentaiManga
    }
}
