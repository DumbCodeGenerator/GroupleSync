package ru.krogon500.grouplesync.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import io.objectbox.kotlin.boxFor
import io.objectbox.relation.ToMany
import kotlinx.android.synthetic.main.chapter_item.view.*
import kotlinx.android.synthetic.main.manga_chapters.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import ru.krogon500.grouplesync.App
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
        val adapter = chaptersList.adapter as? HentaiChaptersAdapter ?: return
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menu.removeItem(R.id.hBrowser)
        menu.removeItem(R.id.logout)
        menu.removeItem(R.id.a_z)
        menu.removeItem(R.id.sync)
        menu.findItem(R.id.clear_table).isVisible = true
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            // Respond to the action bar's Up/Home button
            android.R.id.home -> {
                onBackPressed()
                return true
            }
            R.id.clear_table -> {
                val hentaiBox = (application as App).boxStore.boxFor<HentaiManga>()
                hentaiBox.remove(hChapters)
                hChapters?.clear() ?: return false
                val adapter = chaptersList.adapter as? HentaiChaptersAdapter ?: return false
                adapter.notifyDataSetChanged()
                Toast.makeText(applicationContext, "База очищена", Toast.LENGTH_SHORT).show()
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

        if (!EventBus.getDefault().isRegistered(this@HentaiChapters))
            EventBus.getDefault().register(this@HentaiChapters)

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
    }

    fun onDeleteSelectedClicked() {
        val adapter = chaptersList!!.adapter as HentaiChaptersAdapter
        val checkedItems = adapter.checkedItems
        checkedItems.forEachIndexed { index, b ->
            if (b) {
                val chapterItem = adapter.getItem(index)
                val v = chaptersList.findViewHolderForAdapterPosition(index)?.itemView ?: return
                val c = v.selected
                c.isChecked = false
                //ImageView saved = v.findViewById(R.id.saved);
                if (!chapterItem.saved)
                    return@forEachIndexed
                val hentai_id_local = chapterItem.id
                val mangaDir = File(Utils.hentaiPath + File.separator + hentai_id_local)
                if(mangaDir.exists())
                    mangaDir.deleteRecursively()
                val infoFile = File(Utils.getHentaiInfoFile(hentai_id_local))
                if(infoFile.exists())
                    infoFile.delete()
                adapter.setDownload(index)
            }
        }
        adapter.notifyDataSetChanged()
    }

    private val onBrowserClickListener = object : OnItemClickListener{
        override fun onItemClick(view: View, position: Int) {
            val adapter = chaptersList.adapter as? HentaiBrowserChaptersAdapter ?: return

            val links = ArrayList<String>()
            val chapterItem: HentaiManga = adapter.getItem(position)
            adapter.hChapters.forEach {
                links.add(it.link)
            }

            val intent = Intent(this@HentaiChapters, ImageActivity::class.java)
            Log.d("lol", "chapters size: ${links.size}")
            intent.putExtra("link", chapterItem.link)
            intent.putExtra("type", Utils.HENTAI)
            intent.putExtra("fromBrowser", fromBrowser)
            intent.putExtra("chapters", links)

            startActivity(intent)
        }

    }

    private val onChapterClickListener = object : OnItemClickListener{
        override fun onItemClick(view: View, position: Int) {
            val adapter = chaptersList.adapter as? HentaiChaptersAdapter ?: return
            val chapterItem: HentaiManga = adapter.getItem(position)

            val intent = Intent(this@HentaiChapters, ImageActivity::class.java)
            intent.putExtra("id", chapterItem.id)
            intent.putExtra("type", Utils.HENTAI)

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

                relateds.forEachIndexed { index, element ->
                    if (isCancelled)
                        return false

                    val hrefna = element.selectFirst("div.related_info").selectFirst("h2").child(0)
                    val title = hrefna.text()

                    val imageLink = Utils.hentaiBase + element.selectFirst("div.related_cover").selectFirst("a").selectFirst("img").attr("src")
                    val imageLinkHQ = imageLink.getHQThumbnail()

                    val series = element.selectFirst("div.related_row > div.item2 > h3 > a").text()

                    val chapterLink = String.format("%s://%s%s", url.protocol, url.host, hrefna.attr("href")).replace("/manga/", "/online/")

                    var id: Long? = null
                    val pattern = Pattern.compile("/\\d+")
                    val matcher = pattern.matcher(chapterLink)
                    if (matcher.find())
                        id = matcher.group(0).substring(1).toLong()

                    val chapterItem: HentaiManga
                    when {
                        fromBrowser -> chapterItem = HentaiManga(id = id!!, title = title, series = series, coverLink = imageLinkHQ, link = chapterLink, order = index)
                        id == originalHentai!!.id -> {
                            if(originalHentai!!.order < 0)
                                originalHentai!!.order = index

                            chapterItem = originalHentai!!
                        }
                        else -> chapterItem = hChapters!!.find { chapter -> chapter.id == id }?.also { it.order = index } ?: HentaiManga(id = id!!, title = title, link = chapterLink, order = index)
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
                var adapter = chaptersList.adapter
                if(adapter == null) {
                    adapter = if(!fromBrowser) HentaiChaptersAdapter(this@HentaiChapters, originalHentai!!, onChapterClickListener)
                    else HentaiBrowserChaptersAdapter(chapterItems, onBrowserClickListener)

                    chaptersList.adapter = adapter
                    chaptersList.scrollToPosition(visPos)
                }else{
                    if(!fromBrowser) (adapter as HentaiChaptersAdapter).update(hChapters ?: return) else (adapter as HentaiBrowserChaptersAdapter).update(chapterItems)
                }

                selectAll.setOnClickListener { onSelectAllClicked() }
                downloadSelected.setOnClickListener { onDownloadSelectedClicked() }
                deleteSelected.setOnClickListener { onDeleteSelectedClicked() }
            }
        }

    }

    companion object {
        private lateinit var link: String
        private var fromBrowser: Boolean = false
    }
}
