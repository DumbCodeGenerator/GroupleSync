package ru.krogon500.grouplesync.fragment

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
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
import ru.krogon500.grouplesync.App
import ru.krogon500.grouplesync.R
import ru.krogon500.grouplesync.SpacesItemDecoration
import ru.krogon500.grouplesync.Utils
import ru.krogon500.grouplesync.Utils.getHQThumbnail
import ru.krogon500.grouplesync.activity.HentaiChapters
import ru.krogon500.grouplesync.activity.ImageActivity
import ru.krogon500.grouplesync.adapter.HentaiAdapter
import ru.krogon500.grouplesync.entity.HentaiManga
import ru.krogon500.grouplesync.entity.HentaiManga_
import ru.krogon500.grouplesync.image_loaders.HentaiImageLoader
import ru.krogon500.grouplesync.interfaces.OnItemClickListener
import ru.krogon500.grouplesync.interfaces.RequestListener
import java.io.File
import java.lang.ref.WeakReference
import java.util.*
import java.util.regex.Pattern


class HentaiFragment : Fragment() {
    
    private var mHentaiTask: HentaiTask? = null

    private val requestListener = object : RequestListener{
        override fun onComplete(item: Any?) {
            if (item !is HentaiManga) return
            val adapter = mangaCells.adapter as? HentaiAdapter ?: return
            Toast.makeText(activity, "Удалено из избранного", Toast.LENGTH_SHORT).show()
            val inCache = DiskCacheUtils.findInCache(item.coverLink, imageLoader!!.diskCache)
            adapter.remove(item)

            if (inCache.exists())
                inCache.delete()

            swipeRefresh!!.isRefreshing = true
            refreshListener.onRefresh()
        }

        override fun onFail(e: Exception) {
            Toast.makeText(activity, e.localizedMessage, Toast.LENGTH_SHORT).show()
        }
    }

    private val refreshListener = SwipeRefreshLayout.OnRefreshListener {
        imageLoader?.stop()
        mHentaiTask = HentaiTask(true, mUser, mPass, this@HentaiFragment).also { it.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null as Void?) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mangaCells.layoutManager = GridLayoutManager(context, Utils.calculateNoOfColumns(context))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(
                R.layout.fragment, container, false)
    }

    private fun showActivity() {
        if (!isAdded) return

        val shortAnimTime = resources.getInteger(android.R.integer.config_shortAnimTime)
        
        mangaCells.animate().setDuration(shortAnimTime.toLong()).alpha(1f)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        hentaiBox = ((activity?.application ?: return) as App).boxStore.boxFor()

        if (!imageLoader!!.isInited) {
            val cacheDir = File("${Utils.cachePath}/hentai_covers")

            if (!cacheDir.exists())
                cacheDir.mkdirs()

            val options = DisplayImageOptions.Builder()
                    .cacheOnDisk(true)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .build()
            val config = ImageLoaderConfiguration.Builder(context)
                    .defaultDisplayImageOptions(options)
                    .memoryCacheExtraOptions(450, 400)
                    .denyCacheImageMultipleSizesInMemory()
                    .diskCache(UnlimitedDiskCache(cacheDir))
                    .diskCacheExtraOptions(450, 400, null)
                    .build()
            imageLoader!!.init(config)
        }

        mangaCells.addItemDecoration(SpacesItemDecoration(25, context))
        mangaCells.layoutManager = GridLayoutManager(context, Utils.calculateNoOfColumns(context))
        mangaCells.addOnScrollListener(Utils.fragmentFabListener(activity))
        (mangaCells.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(context!!, R.color.colorAccent))
        swipeRefresh.isRefreshing = true

        val args = arguments!!//getIntent().getExtras();
        mUser = args.getString("user")!!
        mPass = args.getString("pass")!!

        swipeRefresh.setOnRefreshListener(refreshListener)

        mHentaiTask = HentaiTask(false, mUser, mPass, this)
        mHentaiTask!!.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null as Void?)
    }


    override fun onDestroyView() {
        super.onDestroyView()

        if (mHentaiTask != null)
            mHentaiTask!!.cancel(true)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val adapter = mangaCells.adapter as? HentaiAdapter ?: return false
        val position = adapter.selectedItem ?: return false
        val mangaItem = adapter.getItem(position)
        when (item.itemId) {
            32 -> Utils.HentaiRequestTask(mangaItem.id, Utils.HENTAI_DELETE_ACTION, requestListener, mangaItem).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        }
        return super.onContextItemSelected(item)
    }

    private class HentaiTask internal constructor(private val refresh: Boolean, private val mUser: String, private val mPass: String, context: HentaiFragment, private val fragmentReference: WeakReference<HentaiFragment> = WeakReference(context)) : AsyncTask<Void, Void, Boolean>() {
        internal var fragment: HentaiFragment? = null

        private var skip: Boolean = false
        internal var ids: ArrayList<Long> = ArrayList()

        override fun onPreExecute() {
            fragment = fragmentReference.get()
            if (fragment == null) {
                cancel(true)
                return
            }

            if (!hentaiBox.isEmpty && !refresh)
                skip = true
        }

        override fun doInBackground(vararg voids: Void): Boolean? {
            if (isCancelled)
                return false

            val options = BitmapFactory.Options()
            options.inPreferredConfig = Bitmap.Config.RGB_565
            if (skip) {
                return true
            }

            try {
                if (!Utils.login(Utils.HENTAI, mUser, mPass))
                    return false
                val doc = Utils.getPage(Utils.HENTAI, mUser, mPass, Utils.hentaiBase + "/favorites")
                val nav = doc.selectFirst("div.navigation")
                val pages = nav.select("a")
                val allFavor = doc.select("div.content_row")
                for (page in pages) {
                    if (page.text() == "Далее")
                        break
                    val pageD = Utils.getPage(Utils.HENTAI, mUser, mPass, page.attr("href"))
                    allFavor.addAll(pageD.select("div.content_row"))
                }

                allFavor.forEach {
                    if (isCancelled)
                        return false

                    val rowInfo = it.selectFirst("a.title_link")
                    val title = rowInfo.text()

                    val baseLink = rowInfo.attr("href").replace("/manga/", "/online/")

                    lateinit var id: String
                    val pattern = Pattern.compile("/\\d+")
                    val matcher = pattern.matcher(rowInfo.attr("href"))
                    if (matcher.find())
                        id = matcher.group(0).substring(1)

                    val imageLink = it.selectFirst("div.manga_images").selectFirst("img").attr("src")
                    val imageLinkHQ = imageLink.getHQThumbnail()

                    ids.add(id.toLong())
                    val mangaItem = hentaiBox[id.toLong()] ?: HentaiManga(id = id.toLong(), title = title, link = baseLink, coverLink = imageLinkHQ, inFavs = true)

                    val chapPat = Pattern.compile("(глава\\s\\d+)|(часть\\s\\d+)", Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
                    val chapMat = chapPat.matcher(title)
                    if (chapMat.find())
                        mangaItem.hasChapters = true

                    hentaiBox.put(mangaItem)
                    Log.d("lol", "${mangaItem.title} relateds: ${mangaItem.relateds.size}")
                }

                val forDelete = hentaiBox.query {
                    equal(HentaiManga_.inFavs, true)
                    notIn(HentaiManga_.id, ids.toLongArray()) }.find()
                forDelete.forEach { hentaiBox.remove(it.relateds) }
                hentaiBox.remove(forDelete)

                return true
            } catch (e: Exception) {
                e.printStackTrace(Utils.getPrintWriter())
                return false
            }

        }

        override fun onPostExecute(aBoolean: Boolean) {
            super.onPostExecute(aBoolean)
            if (fragment == null)
                return

            fragment!!.showActivity()
            fragment!!.swipeRefresh.isRefreshing = false
            fragment!!.mHentaiTask = null

            if (aBoolean) {
                val mangaCells = fragment?.mangaCells ?: return
                val listener = object : OnItemClickListener {
                    override fun onItemClick(view: View, position: Int) {
                        val adapter = mangaCells.adapter as? HentaiAdapter ?: return
                        val item = adapter.getItem(position)

                        if (item.hasChapters) {
                            val intent = Intent(fragment!!.activity, HentaiChapters::class.java)
                            intent.putExtra("id", item.id)
                            intent.putExtra("link", item.link.replace("/online/", "/related/"))
                            fragment!!.startActivity(intent)
                        } else {
                            val intent = Intent(fragment!!.activity, ImageActivity::class.java)
                            intent.putExtra("type", Utils.HENTAI)
                            intent.putExtra("id", item.id)
                            fragment!!.startActivity(intent)
                        }
                    }
                }

                val adapter = mangaCells.adapter as? HentaiAdapter
                if(adapter == null)
                    mangaCells.adapter = HentaiAdapter(fragment?.activity ?: return, hentaiBox, listener)
                else {
                    adapter.update(hentaiBox)
                }
            }
        }

        override fun onCancelled() {
            fragment!!.mHentaiTask = null

            fragment!!.showActivity()
        }
    }

    companion object {
        var mUser: String = "no login"
        var mPass: String = "no login"
        internal var imageLoader: ImageLoader? = HentaiImageLoader.getInstance()
        lateinit var hentaiBox: Box<HentaiManga>
    }

}
