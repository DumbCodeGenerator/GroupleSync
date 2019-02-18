package ru.krogon500.grouplesync.activity

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nostra13.universalimageloader.core.DisplayImageOptions
import com.nostra13.universalimageloader.core.ImageLoader
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration
import kotlinx.android.synthetic.main.hbrowser_act.*
import org.jsoup.Connection
import ru.krogon500.grouplesync.R
import ru.krogon500.grouplesync.Utils
import ru.krogon500.grouplesync.Utils.getHQThumbnail
import ru.krogon500.grouplesync.Utils.hideView
import ru.krogon500.grouplesync.adapter.HBrowserAdapter
import ru.krogon500.grouplesync.adapter.SimpleArrayAdapter
import ru.krogon500.grouplesync.fragment.HentaiFragment
import ru.krogon500.grouplesync.holder.ClickableViewHolder
import ru.krogon500.grouplesync.image_loaders.HentaiBrowserImageLoader
import ru.krogon500.grouplesync.items.MangaItem
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap


class HentaiBrowser : AppCompatActivity() {
    private var seriesLinks = LinkedHashMap<String, String>()
    private var filteredSeriesTitles = ArrayList<String>()
    private var visPos: Int = 0
    private var curPage: Int = 0
    private val pages = ArrayList<String>()
    private var hTask: HentaiBrowse? = null
    private var isLoading: Boolean = false
    //private lateinit var progressBar: View
    private lateinit var layoutManager: LinearLayoutManager
    private var mOptionsMenu: Menu? = null
    private var searchView: SearchView? = null
    private var seriesViewPos: Int = 0

    private val simpleListener = View.OnClickListener { v ->
        val viewHolder = v.tag as ClickableViewHolder
        if(viewHolder is SimpleArrayAdapter.ViewHolder) {
            val adapter = browseList.adapter as? SimpleArrayAdapter ?: return@OnClickListener
            val position = viewHolder.adapterPosition
            if (searchView != null)
                searchView!!.clearFocus()
            seriesViewPos = layoutManager.findFirstCompletelyVisibleItemPosition()
            browseRefresh.isRefreshing = true
            val key = adapter.getItem(position)
            if (seriesLinks.containsKey(key)) {
                curPage = 0
                hTask = HentaiBrowse(HentaiBrowser.mUser, HentaiBrowser.mPass, seriesLinks[key]!!, firstPage = true, series = true)
                hTask!!.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
            }
        }else if(viewHolder is HBrowserAdapter.ItemViewHolder){
            val adapter = browseList.adapter as? HBrowserAdapter ?: return@OnClickListener
            val position = viewHolder.adapterPosition
            val mangaItem = adapter.getItem(position)
            val intent = Intent(applicationContext, ImageActivity::class.java)
            intent.putExtra("type", Utils.HENTAI)
            intent.putExtra("title", mangaItem.title)
            intent.putExtra("link", mangaItem.link)
            intent.putExtra("online", true)
            intent.putExtra("user", mUser)
            intent.putExtra("pass", mPass)
            startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.hbrowser_act)
        isLoading = true

        if (!imageLoader!!.isInited) {

            val options = DisplayImageOptions.Builder()
                    .cacheInMemory(true)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .build()
            val config = ImageLoaderConfiguration.Builder(this@HentaiBrowser)
                    .defaultDisplayImageOptions(options)
                    .memoryCacheExtraOptions(500, 300)
                    .denyCacheImageMultipleSizesInMemory()
                    .build()
            imageLoader!!.init(config)
        }

        if (savedInstanceState != null) {
            visPos = savedInstanceState.getInt("viewPos")
            curPage = savedInstanceState.getInt("page")
        }
        setSupportActionBar(toolbar)

        if (supportActionBar != null)
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        layoutManager = LinearLayoutManager(this)
        browseList.layoutManager = layoutManager

        browser_fab.setOnClickListener {
            browseList.stopScroll()
            browseList.scrollToPosition(0)
            it.hideView()
            appBar.setExpanded(true, false)
        }

        browseRefresh.setColorSchemeColors(ContextCompat.getColor(applicationContext, R.color.colorAccent))
        browseRefresh.isRefreshing = true
        browseRefresh.setOnRefreshListener {
            if(browseList.adapter is SimpleArrayAdapter) {
                browseRefresh.isRefreshing = false
                return@setOnRefreshListener
            }
            if(mOptionsMenu != null){
                val search_item = mOptionsMenu!!.findItem(R.id.action_search)
                search_item!!.isVisible = false
            }
            hTask = HentaiBrowse(mUser, mPass, Utils.hentaiBase + "/manga/", true)
            hTask?.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR) ?: return@setOnRefreshListener
        }

        browseList.visibility = View.GONE
        browseList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            var isAnimated = false
            val listener = object: AnimatorListenerAdapter(){
                override fun onAnimationStart(animation: Animator?) {
                    isAnimated = true
                }
                override fun onAnimationEnd(animation: Animator?) {
                    isAnimated = false
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val firstVisibleItem = layoutManager.findFirstCompletelyVisibleItemPosition()
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                val itemCount = layoutManager.itemCount
                if(firstVisibleItem == 0 && browser_fab.translationY == 0f && !isAnimated){
                    browser_fab.hideView(listener)
                }else if(firstVisibleItem > 0 && browser_fab.translationY > 0 && !isAnimated){
                    browser_fab.animate().translationY(0f).setInterpolator(LinearInterpolator()).setDuration(150).setListener(listener).start()
                }

                if (layoutManager.itemCount == 0 || pages.size == 0 || pages.size <= curPage || browseList.adapter !is HBrowserAdapter)
                    return

                if (lastVisibleItem == itemCount - 1 && !isLoading) {
                    // It is time to add new data. We call the listener
                    isLoading = true

                    visPos = firstVisibleItem
                    hTask = HentaiBrowse(mUser, mPass, pages[curPage], false)
                    hTask!!.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
                    curPage++
                }
            }
        })

        hTask = HentaiBrowse(mUser, mPass, Utils.hentaiBase + "/manga/", true)
        hTask?.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR) ?: return
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("sViewPos", seriesViewPos)
        outState.putInt("page", curPage)
        outState.putInt("viewPos", layoutManager.findFirstCompletelyVisibleItemPosition())
    }


    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        curPage = savedInstanceState.getInt("page")
        visPos = savedInstanceState.getInt("viewPos")
        seriesViewPos = savedInstanceState.getInt("sViewPos")
    }

    override fun onBackPressed() {
        if(seriesLinks.size > 0){
            appBar.setExpanded(true, false)
            if(browseList.adapter is SimpleArrayAdapter) {
                browser_fab.hideView()
                seriesLinks.clear()
                if (mOptionsMenu != null) {
                    val search_item = mOptionsMenu!!.findItem(R.id.action_search)
                    search_item!!.isVisible = false
                }
                browseRefresh.isRefreshing = true
                browseList.visibility = View.GONE
                /*if(browseList.footerViewsCount == 0)
                browseList!!.addFooterView(progressBar)*/
                //browseList.adapter = HBrowserAdapter()
                hTask = HentaiBrowse(mUser, mPass, Utils.hentaiBase + "/manga/", true)
                hTask!!.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
            }else{
                browseList.adapter = SimpleArrayAdapter(seriesLinks.keys.toMutableList(), simpleListener)
                browseList.scrollToPosition(seriesViewPos)
                if(seriesViewPos == 0)
                    browser_fab.hideView()
            }
        }else
            super.onBackPressed()
    }

    override fun onPause() {
        visPos = layoutManager.findFirstCompletelyVisibleItemPosition()
        browser_fab.hideView()
        super.onPause()
    }

    override fun onStop() {
        if (hTask != null) {
            hTask!!.cancel(true)
            hTask = null
        }
        super.onStop()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.browser_menu, menu)

        val searchItem = menu!!.findItem(R.id.action_search)
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener{
            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                val adapter = browseList.adapter
                if (seriesLinks.size > 0) {
                    return if(adapter is SimpleArrayAdapter) {
                        filteredSeriesTitles.clear()
                        browseList.adapter = SimpleArrayAdapter(seriesLinks.keys.toMutableList(), simpleListener)
                        true
                    }else{
                        browseList.adapter = SimpleArrayAdapter(filteredSeriesTitles, simpleListener)
                        browseList.scrollToPosition(seriesViewPos)
                        if(seriesViewPos == 0)
                            browser_fab.hideView()
                        false
                    }
                }
                return true
            }

            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                return true
            }
        })

        val searchManager = this@HentaiBrowser.getSystemService(Context.SEARCH_SERVICE) as SearchManager

        if (searchItem != null) {
            searchView = searchItem.actionView as SearchView
        }
        searchView?.setSearchableInfo(searchManager.getSearchableInfo(this@HentaiBrowser.componentName))

        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {

            override fun onQueryTextChange(newText: String): Boolean {
                if (browseList.adapter is SimpleArrayAdapter && seriesLinks.size > 0) {
                    filteredSeriesTitles.clear()
                    filteredSeriesTitles = seriesLinks.keys.filter { it.contains(newText, true) } as ArrayList<String>

                    browseList.adapter = SimpleArrayAdapter(filteredSeriesTitles, simpleListener)
                    browser_fab.hideView()
                }
                return false
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                if (browseList.adapter is SimpleArrayAdapter && seriesLinks.size > 0) {
                    filteredSeriesTitles.clear()
                    filteredSeriesTitles = seriesLinks.keys.filter { it.contains(query, true) } as ArrayList<String>

                    browseList.adapter = SimpleArrayAdapter(filteredSeriesTitles, simpleListener)
                    browser_fab.hideView()
                    if(searchView != null)
                        searchView!!.clearFocus()
                }
                return false
            }

        })

        return true
    }


    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        mOptionsMenu = menu
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            // Respond to the action bar's Up/Home button
            android.R.id.home -> {
                onBackPressed()
                return true
            }
            R.id.series -> {
                if (hTask != null) {
                    hTask!!.cancel(true)
                    hTask = null
                }
                browseRefresh.isRefreshing = true
                isLoading = true
                browseList.visibility = View.GONE
                SeriesTask(mUser, mPass).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
                return true
            }

        }
        return super.onOptionsItemSelected(item)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val adapter = browseList.adapter as? HBrowserAdapter ?: return false
        val position = adapter.selectedItem ?: return false
        val mangaItem = adapter.getItem(position)
        Log.d("lol", "position and id: $position/${mangaItem.id}")
        when (item.itemId) {
            1 -> {
                if (mangaItem.haveChapters) {
                    val intent = Intent(this, HentaiChapters::class.java)
                    intent.putExtra("id", mangaItem.id)
                    intent.putExtra("link", mangaItem.link.replace("/online/", "/related/"))
                    intent.putExtra("fromBrowser", true)
                    startActivity(intent)
                }
                return true
            }
            2 -> RequestTask(mangaItem.id).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        }
        return super.onContextItemSelected(item)
    }

    @SuppressLint("StaticFieldLeak")
    private inner class RequestTask internal constructor(id: Long) : AsyncTask<Void, Void, Boolean>() {
        internal var data = HashMap<String, String>()
        internal var error: String = ""

        init {
            data["do"] = "favorites"
            data["doaction"] = "add"
            data["id"] = id.toString()
        }


        override fun doInBackground(vararg voids: Void): Boolean? {
            return try {
                Utils.makeRequest(Utils.HENTAI, mUser, mPass, Utils.hentaiBase + "/index.php", data, Connection.Method.GET)
                true
            } catch (e: Exception) {
                Log.e("GroupleSync", e.localizedMessage)
                error = e.localizedMessage
                e.printStackTrace(Utils.getPrintWriter())
                false
            }

        }

        override fun onPostExecute(aBoolean: Boolean?) {
            if (aBoolean!!) {
                Toast.makeText(applicationContext, "Добавлено в избранное", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(applicationContext, error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    internal inner class SeriesTask(var mUser: String, private var mPass: String) : AsyncTask<Void, Void, Boolean>() {

        override fun doInBackground(vararg params: Void?): Boolean {
            if (!Utils.login(Utils.HENTAI, mUser, mPass))
                return false
            seriesLinks.clear()
            return try {
                val seriesPage = Utils.getPage(Utils.HENTAI, mUser, mPass, "${Utils.hentaiBase}/series/")
                val series = seriesPage.select("div.series_wrap")
                //Log.d("lol", "series1 size: ${series.size}")
                series.filter{ it.attr("title") != ""}.forEach {
                    val seriesEl = it.selectFirst("div.series_name").selectFirst("h2").selectFirst("a")
                    val title = seriesEl.attr("title")
                    val link = Utils.hentaiBase + seriesEl.attr("href")
                    seriesLinks[title] = link
                }
                true
            }catch (e: Exception){
                e.printStackTrace(Utils.getPrintWriter())
                false
            }
        }

        override fun onPostExecute(result: Boolean) {
            if(result){
                browseRefresh.isRefreshing = false
                browseList.adapter = SimpleArrayAdapter(seriesLinks.keys.toMutableList(), simpleListener)
                browseList.visibility = View.VISIBLE
                if(mOptionsMenu != null){
                    val search_item = mOptionsMenu!!.findItem(R.id.action_search)
                    search_item!!.isVisible = true
                }
                isLoading = false
            }
        }

    }

    @SuppressLint("StaticFieldLeak")
    internal inner class HentaiBrowse(var mUser: String, var mPass: String, var link: String, private var firstPage: Boolean, private var series: Boolean = false): AsyncTask<Void, Void, Boolean>() {
        private val mangaItems = ArrayList<MangaItem>()

        override fun doInBackground(vararg voids: Void): Boolean {
            if (!Utils.login(Utils.HENTAI, mUser, mPass))
                return false
            try {
                val mainPage = Utils.getPage(Utils.HENTAI, mUser, mPass, link)
                if (firstPage) {
                    pages.clear()
                    val nav = mainPage.selectFirst("div#pagination")
                    val pages = nav.selectFirst("span").select("a")

                    if(pages.size > 0)
                        pages.forEach { this@HentaiBrowser.pages.add(Utils.hentaiBase + it.attr("href")) }
                }
                val rows = mainPage.selectFirst("div#content").select("div.content_row")
                rows.filter { it.text().contains("Хентай манга", true) }.forEach {
                    val row_info = it.selectFirst("a.title_link")
                    val title = row_info.text()

                    val series = it.selectFirst("h3.original").text()

                    val baseLink = Utils.hentaiBase + row_info.attr("href").replace("/manga/", "/online/")

                    var id: Long? = null
                    val pattern = Pattern.compile("/\\d+")
                    val matcher = pattern.matcher(row_info.attr("href"))
                    if (matcher.find())
                        id = matcher.group(0).substring(1).toLong()

                    val tags = it.selectFirst("div.genre").text()
                    val pluses = it.selectFirst("div.row4_left").text().replace("&nbsp;", "").trim()

                    val mangaItem = MangaItem(id!!, title, series, baseLink, tags, pluses)

                    if (!tags.contains("lolcon")) {
                        val imageLink = it.selectFirst("div.manga_images").selectFirst("a").selectFirst("img").attr("src")
                        val imageLinkHQ = imageLink.getHQThumbnail()
                        mangaItem.coverLink = imageLinkHQ
                    }

                    val chapPat = Pattern.compile("(глава\\s\\d+)|(часть\\s\\d+)", Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
                    val chapMat = chapPat.matcher(title)
                    if (chapMat.find())
                        mangaItem.haveChapters = true

                    mangaItems.add(mangaItem)
                }
                return !isCancelled
            } catch (e: Exception) {
                Log.e("GroupleSync", e.localizedMessage)
                e.printStackTrace(Utils.getPrintWriter())
                return false
            }
        }

        override fun onPostExecute(aBoolean: Boolean) {
            browseRefresh.isRefreshing = false
            hTask = null

            if (aBoolean) {
                if (browseList!!.visibility != View.VISIBLE)
                    browseList!!.visibility = View.VISIBLE

                if(browseList.adapter == null) {
                    val adapter = HBrowserAdapter(mangaItems = mangaItems, addFooter = pages.size > 0 && pages.size > curPage)
                    adapter.setItemClickListener(simpleListener)
                    browseList.adapter = adapter
                    visPos = 0
                }else{
                    val adapter = browseList.adapter
                    if(adapter is HBrowserAdapter) {
                        adapter.addFooter = pages.size > 0 && pages.size > curPage
                        adapter.setItemClickListener(simpleListener)
                        adapter.update(mangaItems, true)
                    }else{
                        browseList.adapter = HBrowserAdapter(mangaItems = mangaItems, addFooter = pages.size > 0 && pages.size > curPage).also { it.setItemClickListener(simpleListener) }
                        visPos = 0
                    }
                }

                browseList.scrollToPosition(visPos)
                isLoading = false
            }
        }
    }

    companion object {
        internal var imageLoader: ImageLoader? = HentaiBrowserImageLoader.getInstance()
        internal val mUser: String = HentaiFragment.mUser
        internal val mPass: String = HentaiFragment.mPass
    }
}
