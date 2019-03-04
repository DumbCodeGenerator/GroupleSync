package ru.krogon500.grouplesync.activity

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import io.objectbox.relation.ToMany
import kotlinx.android.synthetic.main.chapter_item.view.*
import kotlinx.android.synthetic.main.manga_chapters.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import ru.krogon500.grouplesync.App
import ru.krogon500.grouplesync.R
import ru.krogon500.grouplesync.Utils
import ru.krogon500.grouplesync.Utils.getVolAndChapter
import ru.krogon500.grouplesync.adapter.MangaChaptersAdapter
import ru.krogon500.grouplesync.entity.GroupleBookmark
import ru.krogon500.grouplesync.entity.GroupleChapter
import ru.krogon500.grouplesync.event.UpdateEvent
import ru.krogon500.grouplesync.fragment.GroupleFragment
import ru.krogon500.grouplesync.interfaces.OnItemClickListener
import ru.krogon500.grouplesync.interfaces.RequestListener
import java.io.File
import java.net.URL
import java.util.regex.Pattern

class MangaChapters : AppCompatActivity() {
    internal lateinit var gBookmark: GroupleBookmark
    internal lateinit var gChaptersBox: Box<GroupleChapter>
    internal lateinit var gChapters: ToMany<GroupleChapter>
    private var bookmark_id: Long = 0
    lateinit var layoutManager: LinearLayoutManager
    private var visPos: Int = 0
    private var setVisPos = false

    private val requestListener = object : RequestListener{
        override fun onComplete(item: Any?) {
            Toast.makeText(applicationContext, "Закладка обновлена", Toast.LENGTH_SHORT).show()
        }

        override fun onFail(e: Exception) {
            Toast.makeText(applicationContext, "Не удалось обновить закладку. ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    val listener = object : RecyclerView.OnScrollListener() {
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

        val countDownTimer = object: CountDownTimer(2000, 1000){
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

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            val scrollAdapter = recyclerView.adapter as? MangaChaptersAdapter ?: return
            val firstVisibleItem = layoutManager.findFirstCompletelyVisibleItemPosition()
            val last = layoutManager.findLastVisibleItemPosition()
            val readed = scrollAdapter.getLastReaded()

            if(readed in firstVisibleItem..last && fab.translationY == 0f && !isAnimated){
                val layoutParams = fab.layoutParams as CoordinatorLayout.LayoutParams
                val fab_bottomMargin = layoutParams.bottomMargin
                fab.animate().translationY((fab.height + fab_bottomMargin).toFloat()).setInterpolator(LinearInterpolator()).setListener(listener).setDuration(duration).start()
            }else if(readed !in firstVisibleItem..last && fab.translationY > 0 && !isAnimated){
                fab.setImageDrawable(ContextCompat.getDrawable(applicationContext, if(readed > firstVisibleItem) R.drawable.arrow_down else R.drawable.arrow_up))

                fab.animate().translationY(0f).setInterpolator(LinearInterpolator()).setListener(listener).setDuration(duration).start()
            }
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if(newState == RecyclerView.SCROLL_STATE_IDLE){
                countDownTimer.start()
            }else{
                countDownTimer.cancel()
            }
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onUpdateEvent(event: UpdateEvent) {
        if (event.type != Utils.GROUPLE)
            return

        val view = chaptersList.findViewHolderForAdapterPosition(event.position)?.itemView ?: return
        val adapter = chaptersList.adapter as? MangaChaptersAdapter ?: return

        val loading = view.chapterLoading
        val saved = view.saved
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
        val adapter = chaptersList.adapter as? MangaChaptersAdapter ?: return
        visPos = layoutManager.findFirstCompletelyVisibleItemPosition()
        setVisPos = if(adapter.reversed) adapter.getLastReaded() > visPos else adapter.getLastReaded() < visPos
        outState.putInt("listPos", visPos)
        outState.putBoolean("setVP", setVisPos)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        visPos = savedInstanceState?.getInt("listPos") ?: 0
        setVisPos = savedInstanceState?.getBoolean("setVP") ?: false
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
                val chapterItem = adapter.getItem(readed)
                Log.d("lol", "sync item: ${chapterItem.link}")
                Utils.UpdateBookmarkTask(chapterItem, requestListener).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
                return true
            }

            R.id.a_z -> {
                val adapter = chaptersList.adapter as? MangaChaptersAdapter ?: return true
                adapter.reverse()
                chaptersList.scrollToPosition(0)
                return true
            }

            R.id.clear_table -> {
                gChaptersBox.remove(gChapters)
                gChapters.clear()
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

        layoutManager = LinearLayoutManager(this)
        chaptersList.layoutManager = layoutManager
        (chaptersList.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        chaptersList.addItemDecoration(Utils.dividerItemDecor(this, Color.WHITE))
        chaptersList.addOnScrollListener(listener)

        fab.setOnClickListener {
            val adapter = chaptersList.adapter as? MangaChaptersAdapter ?: return@setOnClickListener
            chaptersList.stopScroll()
            layoutManager.scrollToPositionWithOffset(adapter.getLastReaded(), chaptersList.height/2)
        }

        val args = intent.extras!!
        bookmark_id = args.getLong("id")

        gBookmark = GroupleFragment.groupleBookmarksBox[bookmark_id]
        gChaptersBox = (application as App).boxStore.boxFor()
        gChapters = gBookmark.chapters

        chaptersRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.colorAccent))
        chaptersRefresh.isRefreshing = true
        chaptersRefresh.setOnRefreshListener { GetMangaInfo(gBookmark.link, gBookmark.readedLink, true, gBookmark.page).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null as Void?) }

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
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
        gChapters = GroupleFragment.groupleBookmarksBox[bookmark_id].chapters
        val adapter = chaptersList.adapter as? MangaChaptersAdapter ?: return
        adapter.update(gChapters)
        if(setVisPos){
            layoutManager.scrollToPosition(visPos)
        }else {
            layoutManager.scrollToPositionWithOffset(adapter.getLastReaded(), chaptersList.height / 2)
        }
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

        checkedItems.forEachIndexed { index, b ->
            if (b) {
                val v = chaptersList.findViewHolderForAdapterPosition(index)?.itemView ?: return
                val c = v.selected
                val item = adapter.getItem(index)
                item.readed = true
                gChaptersBox.put(item)
                c.isChecked = false
            }
        }
        adapter.notifyDataSetChanged()
    }

    fun onDownloadSelectedClicked() {
        val adapter = chaptersList.adapter as? MangaChaptersAdapter ?: return
        val checkedItems = adapter.checkedItems

        checkedItems.forEachIndexed { index, b ->
            if (b) {
                val v = chaptersList.findViewHolderForAdapterPosition(index)?.itemView ?: return
                val c = v.selected
                val down = v.download
                if (down.visibility == View.VISIBLE)
                    down.performClick()
                c.isChecked = false
            }
        }
        adapter.notifyDataSetChanged()
    }

    fun onDeleteSelectedClicked() {
        val adapter = chaptersList.adapter as? MangaChaptersAdapter ?: return
        val checkedItems = adapter.checkedItems
        Log.d("lol", "deleted count: ${checkedItems.count { it }}")
        checkedItems.forEachIndexed { index, b ->
            if (b) {
                val chapterItem = adapter.getItem(index)
                val v = chaptersList.findViewHolderForAdapterPosition(index)?.itemView ?: return
                val c = v.selected
                c.isChecked = false
                if (!chapterItem.saved)
                    return@forEachIndexed

                val path = "${Utils.grouplePath}/b$bookmark_id/vol${chapterItem.vol}/${chapterItem.chap}"
                val mangaDir = File(path)
                if(mangaDir.exists())
                    mangaDir.deleteRecursively()
                adapter.setDownload(index)
            }
        }
    }

    private val onItemClickListener = object : OnItemClickListener{
        override fun onItemClick(view: View, position: Int) {
            val adapter = chaptersList.adapter as? MangaChaptersAdapter ?: return
            val selected = view.selected

            if (adapter.isAllUnchecked) {
                val intent = Intent(this@MangaChapters, ImageActivity::class.java)
                intent.putExtra("bookmark_id", bookmark_id)
                intent.putExtra("id", adapter.getItem(position).id)
                intent.putExtra("type", Utils.GROUPLE)
                startActivity(intent)
            } else {
                selected.isChecked = !selected.isChecked
            }
        }

    }

    @SuppressLint("StaticFieldLeak")
    private inner class GetMangaInfo

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

                chapters.forEachIndexed { index, element ->
                    if (isCancelled)
                        return false

                    val hrefna = element.selectFirst("td[colspan] > a[title]")
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

                    val chapterItem = gChapters.find { chapter -> chapter.link == chapterLink.trim() } ?:
                                                    GroupleChapter(id = 0, title = title, link = chapterLink, vol = volAndChap[0], chap = volAndChap[1], order = index)

                    if(refresh || gChapters.size < chapters.size) {
                        if (chapterLink.trim() != readedLink.trim() && !done && !readedLink.trim().contains("vol0/0", true)) {
                            chapterItem.readed = true
                        } else if (chapterLink.trim() == readedLink.trim() && !done) {
                            val doc = Jsoup.connect(chapterLink).data("mtr", "1").get()
                            val script = doc.selectFirst("script:containsData(rm_h.init)")
                            val content = script.html()
                            val rows = content.split("\\r?\\n".toRegex())
                            var needed = rows[rows.size - 1]
                            needed = needed.substring(needed.indexOf('[') + 1, needed.lastIndexOf(']'))
                            val parts = needed.split("],")

                            chapterItem.page_all = parts.size
                            chapterItem.page = page
                            chapterItem.readed = chapterItem.page + 1 == chapterItem.page_all
                            done = true
                        }
                    }
                    if(gChapters.contains(chapterItem))
                        gChaptersBox.put(chapterItem)
                    else
                        gChapters.add(chapterItem)
                }
                gChapters.applyChangesToDb()
                return true
            } catch (e: Exception) {
                e.printStackTrace(Utils.getPrintWriter())
                return false
            }

        }

        override fun onPostExecute(success: Boolean) {
            chaptersRefresh.isRefreshing = false
            chaptersList.visibility = View.VISIBLE
            if (success) {
                val adapter: MangaChaptersAdapter
                if(chaptersList.adapter == null) {
                    adapter = MangaChaptersAdapter(this@MangaChapters, gChapters, gChaptersBox, onItemClickListener)
                    chaptersList.adapter = adapter
                }else{
                    adapter = chaptersList.adapter as? MangaChaptersAdapter ?: return
                    adapter.update(gChapters)
                }

                selectAll.setOnClickListener { onSelectAllClicked() }
                downloadSelected.setOnClickListener { onDownloadSelectedClicked() }
                deleteSelected.setOnClickListener { onDeleteSelectedClicked() }
                selectUnread.setOnClickListener { onSelectUnreadClicked() }
                makeReaded.setOnClickListener { onMakeReadedClicked() }

                if (!EventBus.getDefault().isRegistered(this@MangaChapters))
                    EventBus.getDefault().register(this@MangaChapters)
            }
        }

    }
}
