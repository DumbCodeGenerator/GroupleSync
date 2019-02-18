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
import android.view.*
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import com.nostra13.universalimageloader.core.DisplayImageOptions
import com.nostra13.universalimageloader.core.ImageLoader
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration
import kotlinx.android.synthetic.main.hbrowser_act.*
import org.jsoup.Connection
import ru.krogon500.grouplesync.R
import ru.krogon500.grouplesync.Utils
import ru.krogon500.grouplesync.Utils.getDPI
import ru.krogon500.grouplesync.Utils.getHQThumbnail
import ru.krogon500.grouplesync.Utils.hideView
import ru.krogon500.grouplesync.adapter.HBrowserAdapter
import ru.krogon500.grouplesync.fragment.HentaiFragment
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
    private lateinit var progressBar: View
    private var mOptionsMenu: Menu? = null
    private var searchView: SearchView? = null
    private var seriesViewPos: Int = 0

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

        browser_fab.setOnClickListener {
            browseList.smoothScrollBy(0,0)
            browseList.setSelection(0)
            it.hideView()
            appBar.setExpanded(true, false)
        }

        browseRefresh.setColorSchemeColors(ContextCompat.getColor(applicationContext, R.color.colorAccent))
        browseRefresh.isRefreshing = true
        browseRefresh.setOnRefreshListener {
            if(browseList.adapter is ArrayAdapter<*>) {
                browseRefresh.isRefreshing = false
                return@setOnRefreshListener
            }

            if(browseList.footerViewsCount == 0)
                browseList.addFooterView(progressBar)
            if(mOptionsMenu != null){
                val search_item = mOptionsMenu!!.findItem(R.id.action_search)
                search_item!!.isVisible = false
            }
            hTask = HentaiBrowse(mUser, mPass, Utils.hentaiBase + "/manga/", true)
            hTask?.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR) ?: return@setOnRefreshListener
        }

        if (browseList.adapter == null) {
            browseList.visibility = View.GONE
            progressBar = LayoutInflater.from(this).inflate(R.layout.is_loading, null) as ProgressBar
            browseList.addFooterView(progressBar)
            val adapter = HBrowserAdapter(applicationContext)
            browseList.adapter = adapter
            browseList.setOnScrollListener(object : AbsListView.OnScrollListener {
                var isAnimated = false
                val listener = object: AnimatorListenerAdapter(){
                    override fun onAnimationStart(animation: Animator?) {
                        isAnimated = true
                    }
                    override fun onAnimationEnd(animation: Animator?) {
                        isAnimated = false
                    }
                }

                override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {

                }

                override fun onScroll(view: AbsListView, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
                    if(firstVisibleItem == 0 && browser_fab.translationY == 0f && !isAnimated){
                        browser_fab.hideView(listener)
                    }else if(firstVisibleItem > 0 && browser_fab.translationY > 0 && !isAnimated){
                        browser_fab.animate().translationY(0f).setInterpolator(LinearInterpolator()).setDuration(150).setListener(listener).start()
                    }

                    if (browseList.adapter.count == 0 || pages.size == 0 || pages.size <= curPage || browseList.adapter !is HeaderViewListAdapter)
                        return

                    val l = visibleItemCount + firstVisibleItem
                    if (l >= totalItemCount && !isLoading) {
                        // It is time to add new data. We call the listener
                        isLoading = true

                        visPos = firstVisibleItem
                        hTask = HentaiBrowse(mUser, mPass, pages[curPage], false)
                        hTask!!.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
                        curPage++
                    }
                }
            })
        }

        hTask = HentaiBrowse(mUser, mPass, Utils.hentaiBase + "/manga/", true)
        hTask?.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR) ?: return
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("sViewPos", seriesViewPos)
        if (browseList.adapter != null)
            outState.putInt("viewPos", browseList.firstVisiblePosition)
        outState.putInt("page", curPage)
    }


    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        curPage = savedInstanceState.getInt("page")
        visPos = savedInstanceState.getInt("viewPos")
        seriesViewPos = savedInstanceState.getInt("sViewPos")
    }

    override fun onBackPressed() {
        if(seriesLinks.size > 0 && browseList.adapter is ArrayAdapter<*>){
            browser_fab.hideView()
            seriesLinks.clear()
            if(mOptionsMenu != null){
                val search_item = mOptionsMenu!!.findItem(R.id.action_search)
                search_item!!.isVisible = false
            }
            browseRefresh.isRefreshing = true
            browseList!!.visibility = View.GONE
            if(browseList.footerViewsCount == 0)
                browseList!!.addFooterView(progressBar)
            val adapter = HBrowserAdapter(applicationContext)
            browseList.adapter = adapter
            hTask = HentaiBrowse(mUser, mPass, Utils.hentaiBase + "/manga/", true)
            hTask!!.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        }else if(seriesLinks.size > 0){
            if(browseList.footerViewsCount > 0)
                browseList.removeFooterView(progressBar)

            browseList.adapter = ArrayAdapter(this@HentaiBrowser, android.R.layout.simple_list_item_1, seriesLinks.keys.toTypedArray())
            browseList.setDivider(this)
            browseList.setSelection(seriesViewPos)
            if(seriesViewPos == 0)
                browser_fab.hideView()
            browseList.setOnItemClickListener { parent, _, position, _ ->
                if(searchView != null)
                    searchView!!.clearFocus()
                seriesViewPos = parent.firstVisiblePosition
                browseRefresh.isRefreshing = true
                val key = parent.getItemAtPosition(position).toString()
                if (seriesLinks.containsKey(key)) {
                    curPage = 0
                    hTask = HentaiBrowse(HentaiBrowser.mUser, HentaiBrowser.mPass, seriesLinks[key]!!, firstPage = true, series = true)
                    hTask!!.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
                }
            }
        }else
            super.onBackPressed()
    }

    fun ListView.setDivider(context: Context){
        this.divider = ContextCompat.getDrawable(context, android.R.color.darker_gray)
        this.dividerHeight = 1
    }

    fun ListView.removeDivider(context: Context){
        this.divider = ContextCompat.getDrawable(context, android.R.color.transparent)
        this.dividerHeight = 10.getDPI(context)
    }

    override fun onPause() {
        if (browseList != null)
            visPos = browseList!!.firstVisiblePosition
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
                    if(adapter is ArrayAdapter<*>) {
                        filteredSeriesTitles.clear()
                        browseList.adapter = ArrayAdapter(this@HentaiBrowser, android.R.layout.simple_list_item_1, seriesLinks.keys.toTypedArray())
                        browseList.setDivider(this@HentaiBrowser)
                        return true
                    }else{
                        if(browseList.footerViewsCount > 0) {
                            browseList.removeFooterView(progressBar)
                        }
                        browseList.adapter = ArrayAdapter(this@HentaiBrowser, android.R.layout.simple_list_item_1, filteredSeriesTitles)
                        browseList.setDivider(this@HentaiBrowser)
                        browseList.setSelection(seriesViewPos)
                        if(seriesViewPos == 0)
                            browser_fab.hideView()
                        browseList.setOnItemClickListener { parent, _, position, _ ->
                            seriesViewPos = parent.firstVisiblePosition
                            if(searchView != null)
                                searchView!!.clearFocus()
                            browseRefresh.isRefreshing = true
                            val key = parent.getItemAtPosition(position).toString()
                            if (seriesLinks.containsKey(key)) {
                                curPage = 0
                                hTask = HentaiBrowse(HentaiBrowser.mUser, HentaiBrowser.mPass, seriesLinks[key]!!, firstPage = true, series = true)
                                hTask!!.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
                            }
                        }
                        return false
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
                val adapter = browseList.adapter
                if (adapter is ArrayAdapter<*> && seriesLinks.size > 0) {
                    filteredSeriesTitles.clear()
                    filteredSeriesTitles = seriesLinks.keys.filter { it.contains(newText, true) } as ArrayList<String>

                    browseList.adapter = ArrayAdapter(this@HentaiBrowser, android.R.layout.simple_list_item_1, filteredSeriesTitles)
                    browseList.setDivider(this@HentaiBrowser)
                    browser_fab.hideView()
                }
                return false
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                val adapter = browseList.adapter
                if (adapter is ArrayAdapter<*> && seriesLinks.size > 0) {
                    filteredSeriesTitles.clear()
                    filteredSeriesTitles = seriesLinks.keys.filter { it.contains(query, true) } as ArrayList<String>

                    browseList.adapter = ArrayAdapter(this@HentaiBrowser, android.R.layout.simple_list_item_1, filteredSeriesTitles)
                    browseList.setDivider(this@HentaiBrowser)
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
                if(browseList.footerViewsCount > 0)
                    browseList.removeFooterView(progressBar)

                //browseList.scroll
                isLoading = true
                browseList.visibility = View.GONE
                SeriesTask(mUser, mPass).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
                return true
            }

        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) {
        menu.add(Menu.NONE, 1, 0, "Все главы")
        menu.add(Menu.NONE, 2, 0, "Добавить в избранное")
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val menuInfo = item.menuInfo as AdapterView.AdapterContextMenuInfo
        val mangaItem = browseList!!.getItemAtPosition(menuInfo.position) as MangaItem
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
            try {
                val seriesPage = Utils.getPage(Utils.HENTAI, mUser, mPass, "${Utils.hentaiBase}/series/")
                val series = seriesPage.select("div.series_wrap")
                Log.d("lol", "series1 size: ${series.size}")
                series.filter{ it.attr("title") != ""}.forEach {
                    val seriesEl = it.selectFirst("div.series_name").selectFirst("h2").selectFirst("a")
                    val title = seriesEl.attr("title")
                    val link = Utils.hentaiBase + seriesEl.attr("href")
                    seriesLinks[title] = link
                }
            }catch (e: java.lang.Exception){
                e.printStackTrace(Utils.getPrintWriter())
                return false
            }
            return true
        }

        override fun onPostExecute(result: Boolean?) {
            if(result!!){
                Log.d("lol", "series size: ${seriesLinks.size}")
                browseRefresh.isRefreshing = false
                val adapter = ArrayAdapter(this@HentaiBrowser, android.R.layout.simple_list_item_1, seriesLinks.keys.toTypedArray())
                browseList.adapter = adapter
                browseList.setDivider(this@HentaiBrowser)
                browseList.visibility = View.VISIBLE
                if(mOptionsMenu != null){
                    val search_item = mOptionsMenu!!.findItem(R.id.action_search)
                    search_item!!.isVisible = true
                }
                isLoading = false
                browseList.setOnItemClickListener { parent, _, position, _ ->
                    if(searchView != null)
                        searchView!!.clearFocus()
                    seriesViewPos = parent.firstVisiblePosition
                    browseRefresh.isRefreshing = true
                    val key = parent.getItemAtPosition(position).toString()
                    if (seriesLinks.containsKey(key)) {
                        curPage = 0
                        hTask = HentaiBrowse(HentaiBrowser.mUser, HentaiBrowser.mPass, seriesLinks[key]!!, firstPage = true, series = true)
                        hTask!!.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
                    }
                }
            }
        }

    }

    @SuppressLint("StaticFieldLeak")
    internal inner class HentaiBrowse

    (var mUser: String, var mPass: String, var link: String, private var firstPage: Boolean, private var series: Boolean = false) : AsyncTask<Void, Void, Boolean>() {
        private val mangaItems = ArrayList<MangaItem>()
        private val newAdapter: HBrowserAdapter = HBrowserAdapter(applicationContext)

        override fun doInBackground(vararg voids: Void): Boolean? {
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
                    if (isCancelled)
                        return false
                }
            } catch (e: Exception) {
                Log.e("GroupleSync", e.localizedMessage)
                e.printStackTrace(Utils.getPrintWriter())
                return false
            }

            return !isCancelled
        }

        override fun onPostExecute(aBoolean: Boolean) {
            browseRefresh!!.isRefreshing = false
            hTask = null

            if (aBoolean) {
                if (browseList!!.visibility != View.VISIBLE)
                    browseList!!.visibility = View.VISIBLE

                if(series) {
                    Log.d("lol", "manga items size: ${mangaItems.size}")
                    if (pages.size > 0)
                        browseList.addFooterView(progressBar)

                    newAdapter.update(mangaItems, (pages.size > 0 && curPage > 0))
                    browseList.adapter = newAdapter
                }else
                    ((browseList.adapter as HeaderViewListAdapter).wrappedAdapter as HBrowserAdapter).update(mangaItems, !firstPage)
                if(pages.size == curPage && browseList.footerViewsCount > 0){
                    browseList.removeFooterView(progressBar)
                }
                browseList.removeDivider(this@HentaiBrowser)
                browseList.setSelection(visPos)
                registerForContextMenu(browseList)
                isLoading = false

                browseList!!.setOnItemClickListener { parent, _, position, _ ->
                    val mangaItem = parent.getItemAtPosition(position) as MangaItem
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
        }
    }

    companion object {
        internal var imageLoader: ImageLoader? = HentaiBrowserImageLoader.getInstance()
        internal val mUser: String = HentaiFragment.mUser
        internal val mPass: String = HentaiFragment.mPass
    }
}
