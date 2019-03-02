package ru.krogon500.grouplesync.fragment

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.nostra13.universalimageloader.cache.disc.impl.UnlimitedDiskCache
import com.nostra13.universalimageloader.core.DisplayImageOptions
import com.nostra13.universalimageloader.core.ImageLoader
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration
import com.nostra13.universalimageloader.utils.DiskCacheUtils
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query
import kotlinx.android.synthetic.main.fragment.*
import kotlinx.android.synthetic.main.main_act2.*
import ru.krogon500.grouplesync.App
import ru.krogon500.grouplesync.R
import ru.krogon500.grouplesync.SpacesItemDecoration
import ru.krogon500.grouplesync.Utils
import ru.krogon500.grouplesync.activity.MangaChapters
import ru.krogon500.grouplesync.adapter.GroupleAdapter
import ru.krogon500.grouplesync.entity.GroupleBookmark
import ru.krogon500.grouplesync.entity.GroupleBookmark_
import ru.krogon500.grouplesync.entity.GroupleChapter
import ru.krogon500.grouplesync.interfaces.OnItemClickListener
import ru.krogon500.grouplesync.interfaces.RequestListener
import java.io.File
import java.lang.ref.WeakReference

class GroupleFragment : Fragment() {


    private var mGetWatchingBookmarksTask: GetWatchingBookmarksTask? = null
    internal var mGetPlanedBookmarksTask: GetPlanedBookmarksTask? = null
    internal var gotPlaned = false
    private val cacheDir = File("${Utils.cachePath}/covers")

    private val completeRequestListener = object : RequestListener{
        override fun onComplete(item: Any?) {
            Toast.makeText(context, "Закладка убрана в \"Прочитанное\"", Toast.LENGTH_SHORT).show()
        }

        override fun onFail(e: Exception) {
            Toast.makeText(context, e.localizedMessage, Toast.LENGTH_SHORT).show()
        }
    }

    private val watchingRequestListener = object : RequestListener{
        override fun onComplete(item: Any?) {
            Toast.makeText(context, "Закладка перенесена в \"Читаемое\"", Toast.LENGTH_SHORT).show()
        }

        override fun onFail(e: Exception) {
            Toast.makeText(context, e.localizedMessage, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(
                R.layout.fragment, container, false)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        val pos = (mangaCells.layoutManager as? GridLayoutManager ?: return).findFirstCompletelyVisibleItemPosition()
        super.onConfigurationChanged(newConfig)
        mangaCells.layoutManager = GridLayoutManager(context, Utils.calculateNoOfColumns(context))
        mangaCells.also { it.stopScroll() }.also { it.scrollToPosition(pos) }
        if(pos > 0){
            val fab = activity?.frag_fab ?: return
            fab.post {
                fab.animate().translationY(0f).setInterpolator(LinearInterpolator()).setDuration(150).start()
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (!cacheDir.exists())
            cacheDir.mkdirs()

        if (!imageLoader.isInited) {

            val options = DisplayImageOptions.Builder()
                    .cacheOnDisk(true)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .build()
            val config = ImageLoaderConfiguration.Builder(context)
                    .defaultDisplayImageOptions(options)
                    .denyCacheImageMultipleSizesInMemory()
                    .diskCache(UnlimitedDiskCache(cacheDir))
                    .build()
            imageLoader.init(config)
        }

        groupleBookmarksBox = (activity?.application as App).boxStore.boxFor()

        mangaCells.addItemDecoration(SpacesItemDecoration(25, context))
        mangaCells.layoutManager = GridLayoutManager(context, Utils.calculateNoOfColumns(context))
        mangaCells.addOnScrollListener(Utils.fragmentFabListener(activity))

        swipeRefresh.isRefreshing = true
        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(context!!, R.color.colorAccent))

        val args = arguments!!
        mUser = args.getString("user")!!
        mPass = args.getString("pass")!!

        val refreshListener : SwipeRefreshLayout.OnRefreshListener? = SwipeRefreshLayout.OnRefreshListener {
            imageLoader.stop()
            mGetWatchingBookmarksTask = GetWatchingBookmarksTask(true, this).also { it.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR) }
        }

        swipeRefresh.setOnRefreshListener(refreshListener)

        mGetWatchingBookmarksTask = GetWatchingBookmarksTask(false, this).also { it.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR) }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        if (mGetWatchingBookmarksTask != null)
            mGetWatchingBookmarksTask!!.cancel(true)

        if(mGetPlanedBookmarksTask != null)
            mGetPlanedBookmarksTask!!.cancel(true)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val adapter = mangaCells.adapter as? GroupleAdapter ?: return false
        val position = adapter.selectedItem ?: return false
        when (item.itemId) {
            1 -> {
                val bookmark = adapter.getItem(position)
                val id = bookmark.id
                val cover = DiskCacheUtils.findInCache(bookmark.coverLink, imageLoader.diskCache)
                if (cover.exists()) {
                    val coverRen = File(cover.absolutePath + System.currentTimeMillis())
                    cover.renameTo(coverRen)
                    val success = coverRen.delete()
                    if (success)
                        Log.d("lol", "file " + cover.absolutePath + " deleted")
                    else
                        Log.d("lol", "hueta kakaya-to")
                }
                adapter.remove(position)

                Utils.BookmarkTask(id, Utils.GROUPLE_COMPLETE_ACTION, completeRequestListener).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
                val mangaDir = File(Utils.grouplePath + File.separator + "b" + id)
                if (mangaDir.exists())
                    mangaDir.deleteRecursively()
                return true
            }
            2 ->{
                val bookmark = adapter.getItem(position)
                Utils.BookmarkTask(bookmark.id, Utils.GROUPLE_WATCHING_ACTION, watchingRequestListener).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
                return true
            }
        }
        return super.onContextItemSelected(item)
    }

    private fun showActivity() {
        if (!isAdded)
            return
        val shortAnimTime = resources.getInteger(android.R.integer.config_shortAnimTime)

        mangaCells.animate().setDuration(shortAnimTime.toLong()).alpha(1f)
    }

    /*private class SearchTask internal constructor(private val queryLink: String) : AsyncTask<Void, Void, Boolean>() {

        override fun doInBackground(vararg voids: Void): Boolean? {
            try {
                val searchPage = Jsoup.connect(queryLink).get()
                val results = searchPage.select("div.tiles.row")
                return true
            } catch (e: Exception) {
                Log.e("GroupleSync", e.localizedMessage)
                return false
            }

        }
    }*/

    fun getPlanedBookmarks(){
        mangaCells.adapter = null
        swipeRefresh.isRefreshing = true
        mGetPlanedBookmarksTask = GetPlanedBookmarksTask(this).also { it.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR) }
    }

    internal class GetPlanedBookmarksTask(context: GroupleFragment) : AsyncTask<Void, Void, Boolean>() {
        private var fragmentWeakReference: WeakReference<GroupleFragment> = WeakReference(context)
        internal var fragment: GroupleFragment? = null
        private val planedBookmarks = ArrayList<GroupleBookmark>()

        override fun onPreExecute() {
            fragment = fragmentWeakReference.get()
            if (fragment == null) {
                cancel(true)
                return
            }
        }

        override fun doInBackground(vararg voids: Void): Boolean? {
            try {
                if (!Utils.login(Utils.GROUPLE, mUser, mPass))
                    return false
                val doc = Utils.getPage(Utils.GROUPLE, mUser, mPass, Utils.groupleBase + "/private/bookmarks?status=PLANED")
                val table = doc.selectFirst("table.table-hover > tbody")
                val rows = table.children()
                rows.forEach {
                    if (isCancelled)
                        return false

                    val screenshot = it.select("td[width=850] > a.screenshot").first()

                    val title = screenshot.attr("title")

                    val id = it.attr("id").replace("b", "").toLongOrNull() ?: 0

                    val imageLink = screenshot.attr("rel")

                    val baseLink = it.selectFirst("a.site-element").attr("href")

                    val isNew = it.select("span.manga-updated").size == 1

                    val mangaItem = GroupleBookmark(id, title, baseLink, imageLink, "", 0, isNew)
                    planedBookmarks.add(mangaItem)
                }
                return !isCancelled

            } catch (e: Exception) {
                Log.e("lol", e.localizedMessage)
                e.printStackTrace()
                e.printStackTrace(Utils.getPrintWriter())
                return false
            }

        }

        override fun onPostExecute(aBoolean: Boolean) {
            super.onPostExecute(aBoolean)

            if (fragment == null)
                return

            fragment?.showActivity()
            fragment?.swipeRefresh?.isRefreshing = false
            fragment?.swipeRefresh?.isEnabled = false
            fragment?.mGetPlanedBookmarksTask = null

            if (aBoolean) {
                val listener = object : OnItemClickListener {
                    override fun onItemClick(view: View, position: Int) {
                        val adapter = fragment?.mangaCells?.adapter as? GroupleAdapter ?: return
                        val mangaItem = adapter.getItem(position)

                        val intent = Intent(fragment!!.activity, MangaChapters::class.java)
                        intent.putExtra("id", mangaItem.id)
                        fragment?.startActivity(intent)
                    }
                }
                fragment?.mangaCells?.adapter = GroupleAdapter(planedBookmarks, listener)
                fragment?.gotPlaned = true
            }
        }


        override fun onCancelled() {
            if (fragment == null)
                return

            imageLoader.stop()
            fragment?.mGetPlanedBookmarksTask = null
            fragment?.swipeRefresh?.isEnabled = true
            fragment?.swipeRefresh?.isRefreshing = false
            fragment?.swipeRefresh?.isRefreshing = true

            fragment?.mGetWatchingBookmarksTask = GetWatchingBookmarksTask(false, fragment!!).also { it.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR) }

            //fragment?.showActivity()
        }
    }

    fun getWatchingBookmarks(){
        gotPlaned = false
        mangaCells.adapter = null
        swipeRefresh.isEnabled = true
        swipeRefresh.isRefreshing = true
        mGetWatchingBookmarksTask = GetWatchingBookmarksTask(false, this).also { it.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR) }
    }

    private class GetWatchingBookmarksTask(private val refresh: Boolean, context: GroupleFragment) : AsyncTask<Void, Void, Boolean>() {
        internal var fragmentWeakReference: WeakReference<GroupleFragment> = WeakReference(context)
        internal var fragment: GroupleFragment? = null
        internal var ids: ArrayList<Long> = ArrayList()
        private var skip: Boolean = false

        override fun onPreExecute() {
            fragment = fragmentWeakReference.get()
            if (fragment == null) {
                cancel(true)
                return
            }

            if (!groupleBookmarksBox.isEmpty && !refresh)
                skip = true
        }

        override fun doInBackground(vararg voids: Void): Boolean? {
            if (skip)
               return true

            try {
                if (!Utils.login(Utils.GROUPLE, mUser, mPass))
                    return false
                val doc = Utils.getPage(Utils.GROUPLE, mUser, mPass, Utils.groupleBase + "/private/bookmarks?status=WATCHING")
                val table = doc.selectFirst("table.table-hover > tbody")
                val rows = table.children()
                rows.forEach {
                    if (isCancelled)
                        return false

                    var page = 0

                    val screenshot = it.select("td[width=850] > a.screenshot").first()

                    val title = screenshot.attr("title")

                    val id = it.attr("id").replace("b", "").toLongOrNull() ?: 0

                    val imageLink = screenshot.attr("rel")

                    val baseLink = it.selectFirst("a.site-element").attr("href")

                    val goToLink = it.selectFirst("a.go-to-chapter").attr("href")
                    val split1 = goToLink.split("=")
                    val readedLink = goToLink.split("#")[0]

                    if (split1.size == 2)
                        page = Integer.parseInt(split1[1])


                    val isNew = it.select("span.manga-updated").size == 1

                    ids.add(id)

                    var mangaItem = groupleBookmarksBox[id]
                    if(mangaItem == null)
                        mangaItem = GroupleBookmark(id, title, baseLink, imageLink, readedLink, page, isNew)
                    else{
                        mangaItem.isNew = isNew
                        mangaItem.readedLink = readedLink
                        mangaItem.page = page
                    }
                    groupleBookmarksBox.put(mangaItem)
                }
                val gChaptersBox: Box<GroupleChapter> = (fragment?.activity?.application as App).boxStore.boxFor()
                val forDelete = groupleBookmarksBox.query { notIn(GroupleBookmark_.id, ids.toLongArray()) }.find()
                forDelete.forEach {
                    gChaptersBox.remove(it.chapters)
                }
                groupleBookmarksBox.remove(forDelete)
                return !isCancelled

            } catch (e: Exception) {
                e.printStackTrace(Utils.getPrintWriter())
                return false
            }

        }

        override fun onPostExecute(aBoolean: Boolean) {
            super.onPostExecute(aBoolean)

            if (fragment == null)
                return

            fragment?.showActivity()
            fragment?.swipeRefresh?.isRefreshing = false
            fragment?.mGetWatchingBookmarksTask = null

            if (aBoolean) {
                val listener = object : OnItemClickListener {
                    override fun onItemClick(view: View, position: Int) {
                        val adapter = fragment?.mangaCells?.adapter as? GroupleAdapter ?: return
                        val mangaItem = adapter.getItem(position)

                        val intent = Intent(fragment!!.activity, MangaChapters::class.java)
                        intent.putExtra("id", mangaItem.id)
                        fragment?.startActivity(intent)
                    }
                }

                val adapter = fragment?.mangaCells?.adapter as? GroupleAdapter
                if(adapter == null){
                    fragment?.mangaCells?.adapter = GroupleAdapter(groupleBookmarksBox, listener)
                }else{
                    adapter.update(groupleBookmarksBox)
                }
            }
        }


        override fun onCancelled() {
            if (fragment == null)
                return

            fragment!!.mGetWatchingBookmarksTask = null

            fragment!!.showActivity()
        }
    }

    companion object {
        var mUser: String = "no auth"
        var mPass: String = "no auth"
        lateinit var groupleBookmarksBox: Box<GroupleBookmark>
        val imageLoader = ImageLoader.getInstance()!!
    }
}


