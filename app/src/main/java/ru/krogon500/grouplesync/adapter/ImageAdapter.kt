package ru.krogon500.grouplesync.adapter

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.PointF
import android.net.Uri
import android.os.AsyncTask
import android.util.Log
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import com.davemorrissey.labs.subscaleview.ImageViewState
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.github.piasy.biv.BigImageViewer
import com.github.piasy.biv.loader.ImageLoader
import com.github.piasy.biv.loader.glide.GlideImageLoader
import com.github.piasy.biv.view.GlideImageViewFactory
import io.objectbox.Box
import kotlinx.android.synthetic.main.image_activity.*
import kotlinx.android.synthetic.main.image_fragment.view.*
import org.jsoup.Jsoup
import ru.krogon500.grouplesync.CustomProgressIndicator
import ru.krogon500.grouplesync.R
import ru.krogon500.grouplesync.Utils
import ru.krogon500.grouplesync.entity.GroupleChapter
import ru.krogon500.grouplesync.entity.HentaiManga
import ru.krogon500.grouplesync.fragment.HentaiFragment.Companion.mPass
import ru.krogon500.grouplesync.fragment.HentaiFragment.Companion.mUser
import ru.krogon500.grouplesync.system_helper.SystemUiHelper
import java.io.File
import java.util.regex.Pattern


class ImageAdapter(val mContext: Context, private val uiHelper: SystemUiHelper) : PagerAdapter() {
    private var type: Byte = 0
    private var filePaths = ArrayList<String>()
    private var count: Int = 0

    private val imageViewPos = SparseArray<ImageViewState>()

    private lateinit var hentaiBox: Box<HentaiManga>
    private lateinit var gChaptersBox: Box<GroupleChapter>

    init {
        BigImageViewer.initialize(GlideImageLoader.with(mContext))
    }

    constructor(mContext: Context, uiHelper: SystemUiHelper, images: List<String>): this(mContext, uiHelper) {
        type = Utils.HENTAI
        currentRange = images.size
        count = currentRange
        filePaths.addAll(images)
    }

    constructor(mContext: Context, uiHelper: SystemUiHelper, images: List<String>, box: Box<*>, type: Byte): this(mContext, uiHelper) {
        this.type = type

        if(type == Utils.HENTAI) {
            @Suppress("UNCHECKED_CAST")
            this.hentaiBox = box as Box<HentaiManga>
        }else{
            @Suppress("UNCHECKED_CAST")
            this.gChaptersBox = box as Box<GroupleChapter>
        }
        currentRange = images.size
        count = currentRange
        filePaths.addAll(images)
    }


    override fun getCount(): Int {
        return count
    }

    fun clearViewPos(){
        imageViewPos.clear()
        notifyDataSetChanged()
    }

    fun nextChapter(images: List<String>) {
        setFilepaths(images, false)
    }

    fun prevChapter(images: List<String>){
        setFilepaths(images, true)
    }

    private fun backgroundOnlinePart(link: String, tempLinks: ArrayList<String>): Boolean{
        if (type == Utils.GROUPLE) {

            val mainPage = Jsoup.connect(link).data("mtr", "1").get()

            val script = mainPage.selectFirst("script:containsData(rm_h.init)")
            val content = script.html()
            val rows = content.split("\\r?\\n".toRegex())
            var needed = rows[rows.size - 1]
            needed = needed.substring(needed.indexOf('[') + 1, needed.lastIndexOf(']'))
            val parts = needed.split("],")
            for (part in parts) {
                val imgLink = part.replace("[\\['\"\\]]".toRegex(), "").split(",")
                val ext = imgLink[2].split("\\?").first()
                val img = imgLink[1] + imgLink[0] + ext
                tempLinks.add(img)
            }
        } else {
            if (!Utils.login(Utils.HENTAI, mUser, mPass))
                return false

            val mainPage = Utils.getPage(Utils.HENTAI, mUser, mPass, link)

            val script = mainPage.selectFirst("script:containsData(fullimg)")
            val pattern = Pattern.compile("\"fullimg\":.+")
            val matcher = pattern.matcher(script.html())
            if (matcher.find()) {
                val needed = matcher.group(0).replace("\"fullimg\":", "")
                val pattern2 = Pattern.compile("([\"|'])(\\\\?.)*?\\1")
                val matcher2 = pattern2.matcher(needed)
                while (matcher2.find()) {
                    tempLinks.add(matcher2.group(0).replace("\"", "").replace("'", ""))
                }
            }
        }
        return true
    }

    private fun setFilepaths(images: Collection<String>, prevChapter: Boolean){
        if (count > currentRange) {
            if (filePaths.size > currentRange) {
                if (prevChapter)
                    filePaths.subList(0, currentRange).clear()
                else
                    filePaths.subList(currentRange, filePaths.size).clear()
            }
            count = filePaths.size
        }
        currentRange = if(prevChapter) count else images.size
        count += images.size

        if(prevChapter)
            filePaths.addAll(images)
        else
            filePaths.addAll(0, images)

        imageViewPos.clear()
        notifyDataSetChanged()
    }

    fun nextChapterOnline(link: String, id: Long) {
        GetChapterOnline(link, id, false).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    fun prevChapterOnline(link: String, id: Long){
        GetChapterOnline(link, id, true).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    @SuppressLint("StaticFieldLeak")
    private inner class GetChapterOnline(val link: String, val id: Long, val prevChapter: Boolean) : AsyncTask<Void, Void, Boolean>() {
        internal var tempLinks = ArrayList<String>()

        override fun doInBackground(vararg voids: Void): Boolean? {
            return try {
                backgroundOnlinePart(link, tempLinks)
            } catch (e: Exception) {
                Log.e("lol", e.localizedMessage)
                e.printStackTrace(Utils.getPrintWriter())
                false
            }
        }

        override fun onPostExecute(aBoolean: Boolean) {
            if (aBoolean) {
                if(type == Utils.GROUPLE && id > 0) {
                    val chapter = gChaptersBox[id]
                    if (chapter != null) {
                        chapter.page_all = tempLinks.size
                        gChaptersBox.put(chapter)
                    }
                }else if (type == Utils.HENTAI && id > 0){
                    val hentaiChapter = hentaiBox[id]
                    if(hentaiChapter != null) {
                        hentaiChapter.page_all = tempLinks.size
                        hentaiBox.put(hentaiChapter)
                    }
                }
                setFilepaths(tempLinks.reversed(), prevChapter)
            }
        }
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view === `object`
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        val view = `object` as View
        val bigImageView = view.mBigImage
        if (bigImageView.ssiv != null) {
            imageViewPos.put(position, bigImageView.ssiv.state)
            bigImageView.ssiv.recycle()
        }
        container.removeView(view)
    }

    override fun getItemPosition(`object`: Any): Int {
        return filePaths.indexOf((`object` as View).tag)
    }

    private fun imageClick(mContext: Context) {
        val appBar = (mContext as Activity).appBar
        if (!opened) {
            appBar.animate().alpha(1.0f).setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator?) {
                    appBar.visibility = View.VISIBLE
                }
            }).duration = 150
            uiHelper.show()
            opened = true
        } else {
            appBar.animate().alpha(0.0f).setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    appBar.visibility = View.GONE
                }
            }).duration = 150
            uiHelper.hide()
            opened = false
        }
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val mLayoutInflater = LayoutInflater.from(mContext)
        val view = mLayoutInflater.inflate(R.layout.image_fragment, container, false)

        val bigImage = view.mBigImage
        bigImage.setOnClickListener { imageClick(mContext) }
        bigImage.setImageViewFactory(GlideImageViewFactory())
        bigImage.setProgressIndicator(CustomProgressIndicator())

        val myImageLoaderCallback = object : ImageLoader.Callback {
            var iView = bigImage
            var pos = position

            override fun onCacheHit(imageType: Int, image: File) {
                // Image was found in the cache
            }

            override fun onCacheMiss(imageType: Int, image: File) {
                // Image was downloaded from the network
            }

            override fun onStart() {
                // Image download has started
            }

            override fun onProgress(progress: Int) {
                // Image download progress has changed
            }

            override fun onFinish() {
                // Image download has finished
            }

            override fun onSuccess(image: File) {
                // Image was retrieved successfully (either from cache or network)
                val imageView = iView.ssiv

                if (imageView != null) {
                    if (mContext.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                        imageView.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE)
                    else
                        imageView.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_START)

                    imageView.setOnImageEventListener(object : SubsamplingScaleImageView.OnImageEventListener {
                        override fun onReady() {

                        }

                        override fun onImageLoaded() {
                            imageView.setDoubleTapZoomDpi(80)
                            imageView.setDoubleTapZoomDuration(200)
                            imageView.isQuickScaleEnabled = false

                            if (imageViewPos.get(pos, null) != null) {
                                val state = imageViewPos.get(pos)
                                imageView.setScaleAndCenter(state.scale, state.center)
                            }else{
                                imageView.setScaleAndCenter(1f, PointF(imageView.sWidth/2f, 0f))
                            }
                        }

                        override fun onPreviewLoadError(e: Exception) {

                        }

                        override fun onImageLoadError(e: Exception) {
                            Log.e("lol", e.message)
                            e.printStackTrace(Utils.getPrintWriter())
                        }

                        override fun onTileLoadError(e: Exception) {

                        }

                        override fun onPreviewReleased() {

                        }
                    })
                }
            }

            override fun onFail(error: Exception) {
                // Image download failed
                error.printStackTrace()
                error.printStackTrace(Utils.getPrintWriter())
            }
        }

        bigImage.setImageLoaderCallback(myImageLoaderCallback)
        bigImage.showImage(Uri.parse(filePaths[position]))
        if (filePaths.size < position + 1)
            BigImageViewer.prefetch(Uri.parse(filePaths[position + 1]))

        view.tag = filePaths[position]
        container.addView(view)
        return view
    }

    companion object {
        var currentRange: Int = 0
        var opened: Boolean = false
    }

}
