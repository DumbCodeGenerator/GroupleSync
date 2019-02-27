package ru.krogon500.grouplesync.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.animation.LinearInterpolator
import android.widget.AdapterView
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
import org.jsoup.Connection
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
import java.io.File
import java.lang.ref.WeakReference
import java.util.*
import kotlin.collections.ArrayList

class GroupleFragment : Fragment() {


    private var mGetBookmarksTask: GetBookmarksTask? = null

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
        if (Build.VERSION.SDK_INT >= 23 && activity?.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 123)
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
            mGetBookmarksTask = GetBookmarksTask(mUser, mPass, true, this)
            mGetBookmarksTask?.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR) ?: return@OnRefreshListener}

        swipeRefresh.setOnRefreshListener(refreshListener)

        mGetBookmarksTask = GetBookmarksTask(mUser, mPass, false, this)
        mGetBookmarksTask?.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR) ?: return
    }

    override fun onDestroyView() {
        super.onDestroyView()

        if (mGetBookmarksTask != null)
            mGetBookmarksTask!!.cancel(true)
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        menu.add(Menu.NONE, 1, 0, "Удалить")
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val menuInfo = item.menuInfo as AdapterView.AdapterContextMenuInfo
        when (item.itemId) {
            1 -> {
                val adapter = mangaCells.adapter as? GroupleAdapter ?: return false

                val info = adapter.getItem(menuInfo.position)
                val id = info.id
                val cover = DiskCacheUtils.findInCache(info.coverLink, imageLoader.diskCache)
                if (cover.exists()) {
                    val coverRen = File(cover.absolutePath + System.currentTimeMillis())
                    cover.renameTo(coverRen)
                    val success = coverRen.delete()
                    if (success)
                        Log.d("lol", "file " + cover.absolutePath + " deleted")
                    else
                        Log.d("lol", "hueta kakaya-to")
                }
                adapter.remove(menuInfo.position)

                DeleteBookmarkTask(id.toString()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null as Void?)
                val mangaDir = File(Utils.grouplePath + File.separator + "b" + id)
                if (mangaDir.exists())
                    mangaDir.deleteRecursively()
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

    @SuppressLint("StaticFieldLeak")
    private inner class DeleteBookmarkTask internal constructor(private val id: String): AsyncTask<Void, Void, Boolean>() {

        override fun doInBackground(vararg voids: Void): Boolean? {
            val data = HashMap<String, String>()
            data["id"] = id
            data["status"] = "DROPPED"
            return try {
                Utils.makeRequest(Utils.GROUPLE, mUser, mPass, Utils.groupleDelete, data, Connection.Method.POST)
            } catch (e: Exception) {
                e.printStackTrace(Utils.getPrintWriter())
                Log.e("GroupleSync", e.localizedMessage)
                false
            }

        }

        override fun onPostExecute(success: Boolean) {
            if (success) {
                Toast.makeText(activity, "Закладка с сайта успешно удалена", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(activity, "Не удалось удалить закладку с сайта", Toast.LENGTH_SHORT).show()
            }
        }
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

    private class GetBookmarksTask internal constructor(private val mUser: String, private val mPass: String, private val refresh: Boolean,
                                                        context: GroupleFragment, val cacheDir: File = File(Utils.cachePath + File.separator + "covers")) : AsyncTask<Void, Void, Boolean>() {
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

            if (!cacheDir.exists())
                cacheDir.mkdirs()

            if (!imageLoader.isInited) {

                val options = DisplayImageOptions.Builder()
                        .cacheOnDisk(true)
                        .bitmapConfig(Bitmap.Config.RGB_565)
                        .build()
                val config = ImageLoaderConfiguration.Builder(fragment!!.activity!!)
                        .defaultDisplayImageOptions(options)
                        .denyCacheImageMultipleSizesInMemory()
                        .diskCache(UnlimitedDiskCache(cacheDir))
                        .build()
                imageLoader.init(config)
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
            fragment?.mGetBookmarksTask = null

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
                fragment?.registerForContextMenu(fragment?.mangaCells!!)
            }
        }


        override fun onCancelled() {
            if (fragment == null)
                return

            fragment!!.mGetBookmarksTask = null

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


