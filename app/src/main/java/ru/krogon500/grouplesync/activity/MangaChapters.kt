package ru.krogon500.grouplesync.activity

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.*
import android.widget.AbsListView.OnScrollListener.SCROLL_STATE_IDLE
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import com.github.lzyzsd.circleprogress.DonutProgress
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import io.objectbox.relation.ToMany
import kotlinx.android.synthetic.main.chapter_item.view.*
import kotlinx.android.synthetic.main.manga_chapters.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import ru.krogon500.grouplesync.App
import ru.krogon500.grouplesync.R
import ru.krogon500.grouplesync.Utils
import ru.krogon500.grouplesync.Utils.getViewByPosition
import ru.krogon500.grouplesync.Utils.getVolAndChapter
import ru.krogon500.grouplesync.adapter.MangaChaptersAdapter
import ru.krogon500.grouplesync.entity.GroupleBookmark
import ru.krogon500.grouplesync.entity.GroupleChapter
import ru.krogon500.grouplesync.event.UpdateEvent
import ru.krogon500.grouplesync.fragment.GroupleFragment
import java.io.File
import java.net.MalformedURLException
import java.net.URL
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.ArrayList

class MangaChapters : AppCompatActivity() {
    internal lateinit var gBookmark: GroupleBookmark
    internal lateinit var gChaptersBox: Box<GroupleChapter>
    internal lateinit var gChapters: ToMany<GroupleChapter>
    private var bookmark_id: Long = 0

    private var visPos: Int = 0

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onUpdateEvent(event: UpdateEvent) {
        if (event.type != Utils.GROUPLE)
            return

        val view = chaptersList.getViewByPosition(event.position)
        val adapter = chaptersList.adapter as? MangaChaptersAdapter ?: return

        val loading = view.findViewById<DonutProgress>(R.id.chapterLoading)
        val saved = view.findViewById<ImageView>(R.id.saved)
        if (saved != null)
            if (event.done && event.success) {
                adapter.setSaved(event.position)
                //groupleChapters.updateRow(tableName, values, DbHelper.LINK, event.link!!)
            } else if (event.done) {
                adapter.setDownload(event.position)
            } else if (!event.success) {
                adapter.setLoading(event.position)
                loading.max = event.max
                loading.progress = event.current.toFloat()
            }
    }

    override fun onStop() {
        if (EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        visPos = chaptersList.firstVisiblePosition
        outState.putInt("listPos", visPos)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        visPos = savedInstanceState?.getInt("listPos") ?: 0
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            // Respond to the action bar's Up/Home button
            android.R.id.home -> {
                onBackPressed()
                return true
            }
            R.id.sync -> {
                val adapter = chaptersList.adapter as? MangaChaptersAdapter ?: return true
                val readed = adapter.getLastReaded()
                val chapterItem = adapter.getItem(readed) as GroupleChapter
                Log.d("lol", "sync item: ${chapterItem.link}")
                AddBookmarkTask(chapterItem.link, chapterItem.page).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null as Void?)
                return true
            }

            R.id.a_z -> {
                val adapter = chaptersList.adapter as? MangaChaptersAdapter ?: return true
                adapter.reverse()
                chaptersList.setSelection(0)
                return true
            }

            R.id.clear_table -> {
                gChapters.clear()
                gChapters.applyChangesToDb()
                val adapter = chaptersList.adapter as? MangaChaptersAdapter
                adapter?.notifyDataSetChanged()
                Toast.makeText(applicationContext, "База очищена", Toast.LENGTH_SHORT).show()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null)
            visPos = savedInstanceState.getInt("listPos")

        setContentView(R.layout.manga_chapters)

        if (supportActionBar != null)
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        /*fab.addOnHideAnimationListener(object: AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator?) {

            }
        })*/
        //fab.hideView()
        fab.setOnClickListener {
            val adapter = chaptersList.adapter as? MangaChaptersAdapter ?: return@setOnClickListener
            chaptersList.smoothScrollBy(0,0)
            chaptersList.setSelection(adapter.getLastReaded())
        }

        val args = intent.extras!!
        bookmark_id = args.getLong("id")

        gBookmark = GroupleFragment.groupleBookmarksBox[bookmark_id]
        gChaptersBox = (application as App).boxStore.boxFor()
        gChapters = gBookmark.chapters

        if(gChapters.isNotEmpty())
            gChapters.sortByDescending { it.date }

        chaptersRefresh!!.setColorSchemeColors(ContextCompat.getColor(this, R.color.colorAccent))
        chaptersRefresh!!.isRefreshing = true
        chaptersRefresh!!.setOnRefreshListener { GetMangaInfo(gBookmark.link, gBookmark.readedLink, true, gBookmark.page).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null as Void?) }

        GetMangaInfo(gBookmark.link, gBookmark.readedLink, false, gBookmark.page).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null as Void?)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menu.removeItem(R.id.hBrowser)
        menu.removeItem(R.id.logout)
        menu.findItem(R.id.a_z).isVisible = true
        menu.findItem(R.id.clear_table).isVisible = true
        menu.findItem(R.id.sync).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onBackPressed() {
        val adapter = chaptersList.adapter as? MangaChaptersAdapter ?: return super.onBackPressed()
        if (!adapter.isAllUnchecked)
            adapter.uncheckAll()
        else
            super.onBackPressed()
    }

    override fun onRestart() {
        super.onRestart()
        //Log.d("savedd", "restart $visPos")
        if (!EventBus.getDefault().isRegistered(this)) {
            //MPEventBus.getDefault().register(this);
            EventBus.getDefault().register(this)
        }
        gChapters = GroupleFragment.groupleBookmarksBox[bookmark_id].chapters
        val adapter = chaptersList.adapter as? MangaChaptersAdapter ?: return
        adapter.update(gChapters)
        chaptersList.setSelection(visPos)
    }

    fun onSelectAllClicked() {
        val adapter = chaptersList.adapter as? MangaChaptersAdapter ?: return
        adapter.checkAll()
    }

    fun onSelectUnreadClicked() {
        val adapter = chaptersList.adapter as? MangaChaptersAdapter ?: return
        adapter.checkUnreaded()
    }

    fun onMakeReadedClicked(){
        val adapter = chaptersList.adapter as? MangaChaptersAdapter ?: return
        val checkedItems = adapter.checkedItems

        checkedItems.forEach {
            if (it.value) {
                val v = chaptersList.getViewByPosition(it.key)
                val c = v.selected
                val item = adapter.getItem(it.key) as GroupleChapter
                Log.d("lol", "item title: ${item.title}")
                item.readed = true
                gChaptersBox.put(item)
                adapter.notifyDataSetChanged()
                c.isChecked = false
            }
        }
    }

    fun onDownloadSelectedClicked() {
        val adapter = chaptersList.adapter as? MangaChaptersAdapter ?: return
        val checkedItems = adapter.checkedItems

        checkedItems.forEach {
            if (it.value) {
                val v = chaptersList.getViewByPosition(it.key)
                val c = v.selected
                val down = v.download
                if (down.visibility == View.VISIBLE)
                    down.performClick()
                c.isChecked = false
            }
        }
    }

    fun onDeleteSelectedClicked() {
        val adapter = chaptersList.adapter as? MangaChaptersAdapter ?: return
        val checkedItems = adapter.checkedItems
        checkedItems.forEach {
            if (it.value) {
                val chapterItem = adapter.getItem(it.key) as GroupleChapter
                val v = chaptersList.getViewByPosition(it.key)
                val c = v.findViewById<CheckBox>(R.id.selected)
                c.isChecked = false
                //ImageView saved = v.findViewById(R.id.saved);
                if (!chapterItem.saved)
                    return@forEach
                //String link = (String) c.getTag();
                //int[] volAndChap = Utils.getVolAndChapterFromLink(link);
                val path = "${Utils.grouplePath}/b$bookmark_id/vol${chapterItem.vol}/${chapterItem.chap}"
                val mangaDir = File(path)
                if(mangaDir.exists())
                    mangaDir.deleteRecursively()
                val infoFile = File("${Utils.cachePath}/info/grouple/b$bookmark_id/${chapterItem.vol}.${chapterItem.chap}.dat")
                if(infoFile.exists())
                    infoFile.delete()
                adapter.setDownload(it.key)
            }
        }
        adapter.notifyDataSetChanged()
    }

    internal fun onItemLongClick(view: View): Boolean {
        val selected = view.findViewById<CheckBox>(R.id.selected)
        selected.isChecked = !selected.isChecked
        return true
    }

    internal fun onItemClick(parent: AdapterView<*>, view: View, position: Int) {
        val adapter = chaptersList.adapter as? MangaChaptersAdapter ?: return
        val selected = view.findViewById<CheckBox>(R.id.selected)
        val chapterLinks = ArrayList<String>()
        adapter.gChapters.forEach {
            chapterLinks.add(it.link)
        }

        if(!adapter.reversed)
            chapterLinks.reverse()

        val chapterItem = parent.getItemAtPosition(position) as GroupleChapter
        val link = chapterItem.link
        if (adapter.isAllUnchecked) {
            val intent = Intent(this, ImageActivity::class.java)
            intent.putExtra("id", bookmark_id)
            intent.putExtra("link", link)
            intent.putExtra("chapters", chapterLinks)
            intent.putExtra("vol", chapterItem.vol)
            intent.putExtra("chapter", chapterItem.chap)
            intent.putExtra("type", Utils.GROUPLE)
            intent.putExtra("online", !chapterItem.saved)
            intent.putExtra("page", chapterItem.page)
            startActivity(intent)
            //return;
        } else {
            selected.isChecked = !selected.isChecked
        }
    }


    @SuppressLint("StaticFieldLeak")
    private inner class AddBookmarkTask internal constructor(private val link: String, private val page: Int) : AsyncTask<Void, Void, Boolean>() {
        private lateinit var host: String
        private var vol: Int = 0
        private var chapter: Int = 0

        init {
            try {
                val url = URL(link)
                host = String.format("%s://%s", url.protocol, url.host)
                val volandchap = link.getVolAndChapter()
                vol = volandchap[0]
                chapter = volandchap[1]
            } catch (e: MalformedURLException) {
                Toast.makeText(applicationContext, "Неверная ссылка", Toast.LENGTH_SHORT).show()
            }
        }

        override fun doInBackground(vararg voids: Void): Boolean? {
            val mangaId: Int
            try {
                val chapterPage = Jsoup.connect(link).data("mtr", "1").get()
                mangaId = chapterPage.selectFirst("span.bookmark-menu").attr("data-id").toInt()
            } catch (e: Exception) {
                Log.e("lol", e.localizedMessage)
                return false
            }

            val user = GroupleFragment.mUser
            val pass = GroupleFragment.mPass
            val data = HashMap<String, String>()
            if (mangaId <= 0)
                return false
            data["id"] = mangaId.toString()
            data["type"] = ""
            data["status"] = "WATCHING"
            data["vol"] = vol.toString()
            data["num"] = chapter.toString()
            data["page"] = page.toString()
            return try {
                gBookmark.readedLink = link
                gBookmark.page = page
                GroupleFragment.groupleBookmarksBox.put(gBookmark)
                Utils.makeRequest(Utils.GROUPLE, user, pass, host + Utils.groupleAdd, data, Connection.Method.POST)
            } catch (e: Exception) {
                Log.e("lol", e.localizedMessage)
                e.printStackTrace(Utils.getPrintWriter())
                false
            }

        }

        override fun onPostExecute(success: Boolean) {
            if (success) {
                Toast.makeText(applicationContext, "Закладка обновлена", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(applicationContext, "Не удалось обновить закладку", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    private inner class GetMangaInfo
    //private final ArrayList<String> chapterTitles = new ArrayList<>();
    //private final ArrayList<String> links = new ArrayList<>();

    internal constructor(private val baseLink: String, private var readedLink: String,
                         private val refresh: Boolean, private var page: Int) : AsyncTask<Void, Void, Boolean>() {

        private var done: Boolean = false
        private var skip: Boolean = false

        override fun onPreExecute() {
            if (gChapters.isNotEmpty() && !refresh)
                skip = true
        }

        override fun doInBackground(vararg voids: Void): Boolean? {
            if (skip) {
                Log.d("lol", "skip")
                return true
            }
            try {
                if (refresh && Utils.login(Utils.GROUPLE, GroupleFragment.mUser, GroupleFragment.mPass)) {
                    val doc = Utils.getPage(Utils.GROUPLE, GroupleFragment.mUser, GroupleFragment.mPass, Utils.groupleBase + "/private/bookmarks?status=WATCHING")
                    val table = doc.selectFirst("table.table-hover > tbody")
                    val elem = table.selectFirst("td:has(a[href=${gBookmark.link}])")

                    val goToLink = elem.selectFirst("a.go-to-chapter").attr("href")
                    val split1 = goToLink.split("=")
                    readedLink = goToLink.split("#")[0]

                    page = if (split1.size == 2) Integer.parseInt(split1[1]) else 0
                }

                val mangaPage = Jsoup.connect(baseLink).get()
                val url = URL(baseLink)
                val table = mangaPage.selectFirst("table.table-hover > tbody")
                val chapters = table.children()
                chapters.reverse()

                //saved = new boolean[table.children().size()];
                //Log.d("lol", "readed1 link: $readedLink")
                chapters.forEach {
                    if (isCancelled)
                        return false
                    //val values = ContentValues()
                    val hrefna = it.selectFirst("td[colspan] > a[title]")
                    val chapterLink = String.format("%s://%s%s", url.protocol, url.host, hrefna.attr("href"))

                    var chapterName = StringBuilder()
                    var sup = ""
                    for (child in hrefna.childNodes()) {
                        if (child is TextNode)
                            chapterName.append(child.toString())
                        else if (child is Element)
                            sup = "<sup><small><font color=green>${child.text()}</font></small></sup>"
                    }
                    val pattern = Pattern.compile("\\d.+")
                    val m = pattern.matcher(chapterName.toString())
                    if (m.find())
                        chapterName = StringBuilder(m.group())
                    val title = "Глава $chapterName$sup"
                    val volAndChap = chapterLink.getVolAndChapter()

                    //Log.d("lol", gChapters.find { chapter -> chapter.link == chapterLink }?.page_all?.toString() ?: "null")

                    val chapterItem = gChapters.find { chapter -> chapter.link == chapterLink } ?:
                                                    GroupleChapter(id = 0, title = title, link = chapterLink, vol = volAndChap[0], chap = volAndChap[1], date = System.nanoTime())

                    //Log.d("lol", "link and date: ${chapterItem.link} || ${chapterItem.date}")

                    if(refresh || gChapters.size < chapters.size) {
                        if (chapterLink.trim() != readedLink.trim() && !done && !readedLink.trim().contains("vol0/0", true)) {
                            //Log.d("lol", "readed link not done: $chapterLink")
                            chapterItem.readed = true
                        } else if (chapterLink.trim() == readedLink.trim() && !done) {
                            //Log.d("lol", "readed link done: $chapterLink")
                            //chapterItem.readed = false
                            val doc = Jsoup.connect(chapterLink).data("mtr", "1").get()
                            val script = doc.selectFirst("script:containsData(rm_h.init)")
                            val content = script.html()
                            val rows = content.split("\\r?\\n".toRegex())
                            var needed = rows[rows.size - 1]
                            needed = needed.substring(needed.indexOf('[') + 1, needed.lastIndexOf(']'))
                            val parts = needed.split("],")

                            //Log.d("lol", "page count: ${doc.selectFirst("span.pages-count").text()}")
                            chapterItem.page_all = parts.size
                            chapterItem.page = page
                            chapterItem.readed = chapterItem.page + 1 == chapterItem.page_all
                            done = true
                        }
                    }
                    //chapterItem.bookmark.target = gBookmark
                    if(gChapters.contains(chapterItem))
                        gChaptersBox.put(chapterItem)
                    else
                        gChapters.add(chapterItem)
                    //gChaptersBox.put(chapterItem)
                }
                gChapters.applyChangesToDb()
                return true
            } catch (e: Exception) {
                e.printStackTrace(Utils.getPrintWriter())
                return false
            }

        }

        override fun onPostExecute(success: Boolean?) {
            chaptersRefresh!!.isRefreshing = false
            chaptersList.visibility = View.VISIBLE
            if (success!!) {
                val adapter: MangaChaptersAdapter
                if(chaptersList.adapter == null) {
                    adapter = MangaChaptersAdapter(this@MangaChapters, gChapters, gChaptersBox)
                    chaptersList.adapter = adapter
                }else{
                    adapter = chaptersList.adapter as? MangaChaptersAdapter ?: return
                    adapter.update(gChapters)
                }




                if(!refresh)
                    chaptersList.setSelection(visPos)

                chaptersList.setOnItemClickListener { parent, view, position, _ ->  this@MangaChapters.onItemClick(parent, view, position)}
                chaptersList.setOnItemLongClickListener { _, view, _, _ ->  this@MangaChapters.onItemLongClick(view)}
                selectAll.setOnClickListener { onSelectAllClicked() }
                downloadSelected.setOnClickListener { onDownloadSelectedClicked() }
                deleteSelected.setOnClickListener { onDeleteSelectedClicked() }
                selectUnread.setOnClickListener { onSelectUnreadClicked() }
                makeReaded.setOnClickListener { onMakeReadedClicked() }
                //Log.d("lol", "first: ${chaptersList.firstVisiblePosition}/readed: ${adapter.getLastReaded()}/last: ${chaptersList.lastVisiblePosition}")
                /*chaptersList.post {
                    if(adapter.getLastReaded() in chaptersList.firstVisiblePosition..chaptersList.lastVisiblePosition) {
                        val layoutParams = fab.layoutParams as CoordinatorLayout.LayoutParams
                        val fab_bottomMargin = layoutParams.bottomMargin
                        fab.animate().translationY((fab.height + fab_bottomMargin).toFloat()).setInterpolator(LinearInterpolator()).start()
                    }
                }*/

                chaptersList.setOnScrollListener(object : AbsListView.OnScrollListener {
                    var isAnimated = false
                    val duration = 150L
                    val listener = object: AnimatorListenerAdapter(){
                        override fun onAnimationStart(animation: Animator?) {
                            isAnimated = true
                        }
                        override fun onAnimationEnd(animation: Animator?) {
                            isAnimated = false
                        }
                    }

                    val countDownTimer = object: CountDownTimer(1300, 1000){
                        override fun onFinish() {
                            if(fab.translationY == 0f && !isAnimated){
                                val layoutParams = fab.layoutParams as CoordinatorLayout.LayoutParams
                                val fab_bottomMargin = layoutParams.bottomMargin
                                fab.animate().translationY((fab.height + fab_bottomMargin).toFloat()).setInterpolator(LinearInterpolator()).setListener(listener).setDuration(duration).start()
                            }
                        }

                        override fun onTick(millisUntilFinished: Long) {
                        }

                    }

                    init{
                        countDownTimer.start()
                    }

                    override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {
                        if(scrollState == SCROLL_STATE_IDLE){
                            countDownTimer.start()
                        }else{
                            countDownTimer.cancel()
                        }
                    }

                    override fun onScroll(view: AbsListView, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
                        val scrollAdapter = view.adapter as? MangaChaptersAdapter ?: return
                        val last = visibleItemCount + firstVisibleItem
                        val readed = scrollAdapter.getLastReaded()

                        //Log.d("lol", "first: $firstVisibleItem / transY: ${fab.translationY} / isAnimated: $isAnimated")
                        if(readed in firstVisibleItem until last && fab.translationY == 0f && !isAnimated){
                            //Log.d("lol", "pryachem: $readed/$firstVisibleItem")
                            val layoutParams = fab.layoutParams as CoordinatorLayout.LayoutParams
                            val fab_bottomMargin = layoutParams.bottomMargin
                            fab.animate().translationY((fab.height + fab_bottomMargin).toFloat()).setInterpolator(LinearInterpolator()).setListener(listener).setDuration(duration).start()
                        }else if(readed !in firstVisibleItem until last && fab.translationY > 0 && !isAnimated){
                            //Log.d("lol", "kazhem: $readed/$firstVisibleItem")
                            if(readed > firstVisibleItem)
                                fab.setImageDrawable(ContextCompat.getDrawable(applicationContext, R.drawable.arrow_down))
                            else
                                fab.setImageDrawable(ContextCompat.getDrawable(applicationContext, R.drawable.arrow_up))
                            fab.animate().translationY(0f).setInterpolator(LinearInterpolator()).setListener(listener).setDuration(duration).start()
                        }
                    }
                })
                //adapter.notifyDataSetChanged();
                if (!EventBus.getDefault().isRegistered(this@MangaChapters))
                    EventBus.getDefault().register(this@MangaChapters)
            }
        }

    }
}
