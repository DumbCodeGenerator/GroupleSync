package ru.krogon500.grouplesync.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.objectbox.relation.ToMany
import kotlinx.android.synthetic.main.chapter_item.view.*
import kotlinx.android.synthetic.main.manga_chapters.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import ru.krogon500.grouplesync.R
import ru.krogon500.grouplesync.Utils
import ru.krogon500.grouplesync.Utils.getHQThumbnail
import ru.krogon500.grouplesync.adapter.HentaiBrowserChaptersAdapter
import ru.krogon500.grouplesync.adapter.HentaiChaptersAdapter
import ru.krogon500.grouplesync.entity.HentaiManga
import ru.krogon500.grouplesync.event.UpdateEvent
import ru.krogon500.grouplesync.fragment.HentaiFragment
import ru.krogon500.grouplesync.interfaces.OnItemClickListener
import java.io.File
import java.net.URL
import java.util.regex.Pattern

class HentaiChapters : AppCompatActivity() {
    private var manga_id: Long = 0
    var hChapters: ToMany<HentaiManga>? = null
    internal var originalHentai: HentaiManga? = null
    private var visPos: Int = 0
    lateinit var layoutManager: LinearLayoutManager


    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onUpdateEvent(event: UpdateEvent) {
        if (event.type != Utils.HENTAI || event.original_id != manga_id)
            return
        val view = chaptersList.findViewHolderForAdapterPosition(event.position)?.itemView ?: return
        val adapter = chaptersList.adapter as HentaiChaptersAdapter
        val loading = view.chapterLoading
        val saved = view.saved
        if (saved != null)
            if (event.done && event.success) {
                adapter.setSaved(event.position)
            } else if (event.done) {
                adapter.setDownload(event.position)
            } else if (!event.success) {
                adapter.setLoading(event.position)
                loading.max = event.max
                loading.progress = event.current.toFloat()
            }
    }

    public override fun onStop() {
        if (EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        visPos = layoutManager.findFirstCompletelyVisibleItemPosition()
        outState.putInt("listPos", visPos)
        outState.putBoolean("fromBrowser", fromBrowser)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        visPos = savedInstanceState.getInt("listPos")
        fromBrowser = savedInstanceState.getBoolean("fromBrowser")
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            // Respond to the action bar's Up/Home button
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.manga_chapters)
        fab.hide()
        selectUnread!!.visibility = View.GONE

        if (supportActionBar != null)
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        layoutManager = LinearLayoutManager(this)
        chaptersList.layoutManager = layoutManager
        chaptersList.addItemDecoration(Utils.dividerItemDecor(this, Color.WHITE))

        val args = intent.extras!!
        link = args.getString("link")!!
        manga_id = args.getLong("id")
        fromBrowser = args.containsKey("fromBrowser")

        if(!fromBrowser) {
            originalHentai = HentaiFragment.hentaiBox[manga_id]
            hChapters = originalHentai?.relateds ?: return
        }

        if (savedInstanceState != null) {
            visPos = savedInstanceState.getInt("listPos")
            fromBrowser = savedInstanceState.getBoolean("fromBrowser")
        }

        chaptersRefresh!!.setColorSchemeColors(ContextCompat.getColor(applicationContext, R.color.colorAccent))
        chaptersRefresh!!.isRefreshing = true
        chaptersRefresh!!.setOnRefreshListener { GetHentaiInfo(link, HentaiFragment.mUser, HentaiFragment.mPass, true, fromBrowser).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null as Void?) }

        GetHentaiInfo(link, HentaiFragment.mUser, HentaiFragment.mPass, false, fromBrowser).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null as Void?)
    }

    override fun onBackPressed() {
        val adapter = chaptersList!!.adapter as? HentaiChaptersAdapter ?: return super.onBackPressed()
        if (!adapter.isAllUnchecked)
            adapter.uncheckAll()
        else
            super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
    }

    override fun onRestart() {
        super.onRestart()
        if(fromBrowser)
            return
        val adapter = chaptersList.adapter as? HentaiChaptersAdapter ?: return
        adapter.update(HentaiFragment.hentaiBox[manga_id].relateds)
    }

    fun onSelectAllClicked() {
        val adapter = chaptersList!!.adapter as HentaiChaptersAdapter
        adapter.checkAll()
    }

    fun onDownloadSelectedClicked() {
        val adapter = chaptersList!!.adapter as HentaiChaptersAdapter
        val checkedItems = adapter.checkedItems
        checkedItems.forEach {
            if (it.value) {
                val v = chaptersList.findViewHolderForAdapterPosition(it.key)?.itemView ?: return
                val c = v.selected
                val down = v.download
                if (down.visibility == View.VISIBLE)
                    down.performClick()
                c.isChecked = false
            }
        }
    }

    fun onDeleteSelectedClicked() {
        val adapter = chaptersList!!.adapter as HentaiChaptersAdapter
        val checkedItems = adapter.checkedItems
        checkedItems.forEach {
            if (it.value) {
                val chapterItem = adapter.getItem(it.key)
                val v = chaptersList.findViewHolderForAdapterPosition(it.key)?.itemView ?: return
                val c = v.selected
                c.isChecked = false
                //ImageView saved = v.findViewById(R.id.saved);
                if (!chapterItem.saved)
                    return@forEach
                val hentai_id_local = chapterItem.id
                val mangaDir = File(Utils.hentaiPath + File.separator + hentai_id_local)
                if(mangaDir.exists())
                    mangaDir.deleteRecursively()
                val infoFile = File(Utils.getHentaiInfoFile(hentai_id_local))
                if(infoFile.exists())
                    infoFile.delete()
                adapter.setDownload(it.key)
            }
        }
        adapter.notifyDataSetChanged()
    }

    private val onBrowserClickListener = object : OnItemClickListener{
        override fun onItemClick(view: View, position: Int) {
            val adapter = chaptersList.adapter as? HentaiBrowserChaptersAdapter ?: return

            val links = ArrayList<String>()
            val ids = ArrayList<Long>()
            val chapterItem: HentaiManga = adapter.getItem(position)
            adapter.hChapters.forEach {
                links.add(it.link)
                ids.add(it.id)
            }

            val intent = Intent(this@HentaiChapters, ImageActivity::class.java)
            Log.d("lol", "ids and chapters size: ${ids.size}/${links.size}")
            intent.putExtra("id", chapterItem.id)
            intent.putExtra("ids", ids)
            intent.putExtra("title", chapterItem.title)
            intent.putExtra("type", Utils.HENTAI)
            intent.putExtra("link", chapterItem.link)
            intent.putExtra("chapters", links)
            intent.putExtra("page", chapterItem.page)
            intent.putExtra("online", !chapterItem.saved)
            intent.putExtra("fromBrowser", fromBrowser)

            startActivity(intent)
        }

    }

    private val onChapterClickListener = object : OnItemClickListener{
        override fun onItemClick(view: View, position: Int) {
            val adapter = chaptersList.adapter as? HentaiChaptersAdapter ?: return

            val links = ArrayList<String>()
            val ids = ArrayList<Long>()
            val chapterItem: HentaiManga = adapter.getItem(position)
            adapter.hChapters.forEach {
                links.add(it.link)
                ids.add(it.id)
            }

            val intent = Intent(this@HentaiChapters, ImageActivity::class.java)
            Log.d("lol", "ids and chapters size: ${ids.size}/${links.size}")
            intent.putExtra("id", chapterItem.id)
            intent.putExtra("ids", ids)
            intent.putExtra("title", chapterItem.title)
            intent.putExtra("type", Utils.HENTAI)
            intent.putExtra("link", chapterItem.link)
            intent.putExtra("chapters", links)
            intent.putExtra("page", chapterItem.page)
            intent.putExtra("online", !chapterItem.saved)

            startActivity(intent)
        }
    }

    @SuppressLint("StaticFieldLeak")
    private inner class GetHentaiInfo

    internal constructor(private val baseLink: String, private val mUser: String, private val mPass: String,
                         private val refresh: Boolean, private val fromBrowser: Boolean) : AsyncTask<Void, Void, Boolean>() {
        private val chapterItems = ArrayList<HentaiManga>()
        private var skip: Boolean = false

        override fun onPreExecute() {
            if (hChapters?.isNotEmpty() == true && !refresh && !fromBrowser)
                skip = true
        }

        override fun doInBackground(vararg voids: Void): Boolean? {
            if (skip)
                return true

            try {
                val mangaPage = Utils.getPage(Utils.HENTAI, mUser, mPass, baseLink)
                val url = URL(baseLink)
                val relateds = mangaPage.select("div.related")
                val pages = mangaPage.selectFirst("div#pagination_related").select("a")
                pages.filter {it.text().toIntOrNull() != null}.forEach {
                    val pageD = Utils.getPage(Utils.HENTAI, mUser, mPass, baseLink + it.attr("href"))
                    relateds.addAll(pageD.select("div.related"))
                }

                relateds.forEach {
                    if (isCancelled)
                        return false

                    val hrefna = it.selectFirst("div.related_info").selectFirst("h2").child(0)
                    val title = hrefna.text()

                    val imageLink = Utils.hentaiBase + it.selectFirst("div.related_cover").selectFirst("a").selectFirst("img").attr("src")
                    val imageLinkHQ = imageLink.getHQThumbnail()

                    val series = it.selectFirst("div.related_row > div.item2 > h3 > a").text()

                    val chapterLink = String.format("%s://%s%s", url.protocol, url.host, hrefna.attr("href")).replace("/manga/", "/online/")

                    var id: Long? = null
                    val pattern = Pattern.compile("/\\d+")
                    val matcher = pattern.matcher(chapterLink)
                    if (matcher.find())
                        id = matcher.group(0).substring(1).toLong()

                    val chapterItem: HentaiManga
                    when {
                        fromBrowser -> chapterItem = HentaiManga(id = id!!, title = title, series = series, coverLink = imageLinkHQ, link = chapterLink, date = System.nanoTime())
                        id == originalHentai!!.id -> {
                            if(originalHentai!!.date == 0L)
                                originalHentai!!.date = System.nanoTime()

                            chapterItem = originalHentai!!
                        }
                        else -> chapterItem = hChapters!!.find { chapter -> chapter.id == id } ?: HentaiManga(id = id!!, title = title, link = chapterLink, date = System.nanoTime())
                    }

                    if(!fromBrowser) {
                        HentaiFragment.hentaiBox.put(chapterItem)
                        if (!hChapters!!.hasA { chapter -> chapter.id == id })
                            hChapters!!.add(chapterItem)
                    }else
                        chapterItems.add(chapterItem)
                }
                if(!fromBrowser)
                    hChapters!!.applyChangesToDb()
                return true
            } catch (e: Exception) {
                e.printStackTrace(Utils.getPrintWriter())
                return false
            }

        }

        override fun onPostExecute(success: Boolean) {
            chaptersRefresh!!.isRefreshing = false
            chaptersList.visibility = View.VISIBLE
            if (success) {
                val adapter: RecyclerView.Adapter<*>
                if(!fromBrowser){
                    adapter = HentaiChaptersAdapter(this@HentaiChapters, originalHentai!!, onChapterClickListener)
                } else {
                    adapter = HentaiBrowserChaptersAdapter(chapterItems, onBrowserClickListener)
                }
                chaptersList.adapter = adapter
                chaptersList.scrollToPosition(visPos)

                selectAll.setOnClickListener { onSelectAllClicked() }
                downloadSelected.setOnClickListener { onDownloadSelectedClicked() }
                deleteSelected.setOnClickListener { onDeleteSelectedClicked() }

                if (!EventBus.getDefault().isRegistered(this@HentaiChapters))
                    EventBus.getDefault().register(this@HentaiChapters)
            }
        }

    }

    companion object {
        private lateinit var link: String
        private var fromBrowser: Boolean = false
    }
}
