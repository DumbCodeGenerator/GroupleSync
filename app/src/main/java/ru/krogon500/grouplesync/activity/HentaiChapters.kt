package ru.krogon500.grouplesync.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.CheckBox
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import io.objectbox.relation.ToMany
import kotlinx.android.synthetic.main.chapter_item.view.*
import kotlinx.android.synthetic.main.manga_chapters.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import ru.krogon500.grouplesync.R
import ru.krogon500.grouplesync.Utils
import ru.krogon500.grouplesync.Utils.getHQThumbnail
import ru.krogon500.grouplesync.Utils.getViewByPosition
import ru.krogon500.grouplesync.adapter.HentaiBrowserChaptersAdapter
import ru.krogon500.grouplesync.adapter.HentaiChaptersAdapter
import ru.krogon500.grouplesync.entity.HentaiManga
import ru.krogon500.grouplesync.event.UpdateEvent
import ru.krogon500.grouplesync.fragment.HentaiFragment
import java.io.File
import java.net.URL
import java.util.regex.Pattern

//import android.widget.Button;

//import xiaofei.library.hermeseventbus.HermesEventBus;

class HentaiChapters : AppCompatActivity() {
    //internal lateinit var hentaiBookmarks: DbHelper
    //SwipeRefreshLayout chaptersRefresh;
    private var manga_id: Long = 0
    var hChapters: ToMany<HentaiManga>? = null
    internal var originalHentai: HentaiManga? = null
    private var visPos: Int = 0


    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onUpdateEvent(event: UpdateEvent) {
        if (event.type != Utils.HENTAI || event.original_id != manga_id)
            return
        //Log.d("lol", "event2");
        /*if(chaptersList == null)
            chaptersList = (ListView)this.findViewById(R.id.chaptersList);*/
        //Log.d("lol", event.position +"");
        //Log.d("lol", chaptersList.getItemAtPosition(event.position).toString());
        val view = chaptersList!!.getViewByPosition(event.position)
        val adapter = chaptersList!!.adapter as HentaiChaptersAdapter
        //ImageButton button = view.findViewById(R.id.download);
        val loading = view.chapterLoading
        val saved = view.saved
        if (saved != null)
        //Log.d("lol", saved.getVisibility()+"");
            if (event.done && event.success) {
                adapter.setSaved(event.position)
                /*val values = ContentValues()
                values.put(DbHelper.PAGE_ALL, event.max)
                hentaiBookmarks.updateRow(DbHelper.HENTAIB_TABLE_NAME, values, DbHelper.ID, manga_id)*/
                //adapter.notifyDataSetChanged();
            } else if (event.done) {
                adapter.setDownload(event.position)
                //adapter.notifyDataSetChanged();
            } else if (!event.success) {
                adapter.setLoading(event.position)
                //adapter.notifyDataSetChanged();
                loading.max = event.max
                loading.progress = event.current.toFloat()
                //button.setVisibility();
            }
    }

    override fun onPause() {
        visPos = chaptersList!!.firstVisiblePosition
        super.onPause()
    }

    public override fun onStop() {
        if (EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        //Log.d("savedd", "save");
        super.onSaveInstanceState(outState)
        outState.putInt("listPos", chaptersList!!.firstVisiblePosition)
        outState.putBoolean("fromBrowser", fromBrowser)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        //Log.d("savedd", "restore");
        super.onRestoreInstanceState(savedInstanceState)
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
        //hentaiBookmarks = (application as App).hentaiBookmarks

        if (supportActionBar != null)
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)

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
            //MPEventBus.getDefault().register(this);
            EventBus.getDefault().register(this)
        }
        /*if (groupleChapters == null)
            groupleChapters = new DbHelper(MangaChapters.this, DbHelper.CHAPTERS_DATABASE_NAME);*/
        //new GetHentaiInfo(link, user, pass, false, fromBrowser).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void) null);
    }

    override fun onRestart() {
        super.onRestart()
        if(fromBrowser)
            return
        val adapter = chaptersList.adapter as? HentaiChaptersAdapter ?: return
        adapter.update(HentaiFragment.hentaiBox[manga_id].relateds)
        //val cursor = hentaiBookmarks.selectRelated(manga_id)
        /*if(cursor.moveToFirst()){
            while (!cursor.isAfterLast){
                val chapterItem = adapter.getItem(cursor.position) as ChapterItem
                val readed = cursor.getInt(cursor.getColumnIndex(DbHelper.READED)) == 1
                val page = cursor.getInt(cursor.getColumnIndex(DbHelper.PAGE))
                val page_all = cursor.getInt(cursor.getColumnIndex(DbHelper.PAGE_ALL))
                chapterItem.readed = readed
                chapterItem.page = page
                chapterItem.page_all = page_all
                cursor.moveToNext()
            }
            adapter.notifyDataSetChanged()
        }*/
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
                val v = chaptersList!!.getViewByPosition(it.key)
                val c = v.findViewById<CheckBox>(R.id.selected)
                val down = v.findViewById<ImageButton>(R.id.download)
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
                val chapterItem = adapter.getItem(it.key) as HentaiManga
                val v = chaptersList!!.getViewByPosition(it.key)
                val c = v.findViewById<CheckBox>(R.id.selected)
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

    internal fun onItemLongClick(view: View): Boolean {
        val selected = view.findViewById<CheckBox>(R.id.selected)
        selected.isChecked = !selected.isChecked
        return true
    }

    internal fun onItemClick(parent: AdapterView<*>, view: View, position: Int) {
        val links = ArrayList<String>()
        val ids = ArrayList<Long>()
        if(!fromBrowser) {
            val adapter = chaptersList.adapter as? HentaiChaptersAdapter ?: return
            adapter.hChapters.forEach {
                links.add(it.link)
                ids.add(it.id)
            }
        }else{
            val adapter = chaptersList.adapter as? HentaiBrowserChaptersAdapter ?: return
            adapter.hChapters.forEach {
                links.add(it.link)
                ids.add(it.id)
            }
        }

        val chapterItem = parent.getItemAtPosition(position) as HentaiManga
        val selected = view.findViewById<CheckBox>(R.id.selected)
        //TextView title = view.findViewById(R.id.chapterTitle);
        if ((!fromBrowser && (chaptersList.adapter as? HentaiChaptersAdapter ?: return).isAllUnchecked) || fromBrowser) {
            val intent = Intent(this, ImageActivity::class.java)
            Log.d("lol", "ids and chapters size: ${ids.size}/${links.size}")
            intent.putExtra("id", chapterItem.id)
            intent.putExtra("ids", ids)
            intent.putExtra("title", chapterItem.title)
            intent.putExtra("type", Utils.HENTAI)
            intent.putExtra("link", chapterItem.link)
            intent.putExtra("chapters", links)
            intent.putExtra("page", chapterItem.page)
            //Log.d("lol", chapterItem.saved.toString())
            intent.putExtra("online", !chapterItem.saved)

            if(fromBrowser)
                intent.putExtra("fromBrowser", fromBrowser)

            startActivity(intent)
            //return;
        }  else {
            selected.isChecked = !selected.isChecked
        }
    }


    @SuppressLint("StaticFieldLeak")
    private inner class GetHentaiInfo
    //private final ArrayList<String> chapterTitles = new ArrayList<>(), manga_ids = new ArrayList<>();

    internal constructor(private val baseLink: String, private val mUser: String, private val mPass: String, //private boolean[] saved, readed;
                         private val refresh: Boolean, private val fromBrowser: Boolean) : AsyncTask<Void, Void, Boolean>() {
        //private var cursor: Cursor? = null
        private val chapterItems = ArrayList<HentaiManga>()
        private var skip: Boolean = false

        override fun onPreExecute() {
            //cursor = hentaiBookmarks.selectRelated(manga_id)
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

        override fun onPostExecute(success: Boolean?) {
            chaptersRefresh!!.isRefreshing = false
            chaptersList.visibility = View.VISIBLE
            if (success!!) {
                val adapter = if(!fromBrowser) HentaiChaptersAdapter(this@HentaiChapters, originalHentai!!)
                                            else HentaiBrowserChaptersAdapter(this@HentaiChapters, chapterItems)
                chaptersList.adapter = adapter
                chaptersList.setSelection(visPos)
                chaptersList.setOnItemClickListener { parent, view, position, _ ->  this@HentaiChapters.onItemClick(parent, view, position)}
                chaptersList.setOnItemLongClickListener { _, view, _, _ ->  this@HentaiChapters.onItemLongClick(view)}
                selectAll.setOnClickListener { onSelectAllClicked() }
                downloadSelected.setOnClickListener { onDownloadSelectedClicked() }
                deleteSelected.setOnClickListener { onDeleteSelectedClicked() }
                //adapter.notifyDataSetChanged();
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
