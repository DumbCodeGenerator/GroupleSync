package ru.krogon500.grouplesync.adapter

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
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
import io.objectbox.kotlin.query
import kotlinx.android.synthetic.main.image_activity.*
import kotlinx.android.synthetic.main.image_fragment.view.*
import org.jsoup.Jsoup
import ru.krogon500.grouplesync.CustomProgressIndicator
import ru.krogon500.grouplesync.R
import ru.krogon500.grouplesync.Utils
import ru.krogon500.grouplesync.activity.ImageActivity
import ru.krogon500.grouplesync.entity.GroupleChapter
import ru.krogon500.grouplesync.entity.GroupleChapter_
import ru.krogon500.grouplesync.entity.HentaiManga
import ru.krogon500.grouplesync.entity.HentaiManga_
import ru.krogon500.grouplesync.fragment.HentaiFragment.Companion.mPass
import ru.krogon500.grouplesync.fragment.HentaiFragment.Companion.mUser
import ru.krogon500.grouplesync.system_helper.SystemUiHelper
import java.io.File
import java.util.regex.Pattern


class ImageAdapter(val mContext: Context, private val uiHelper: SystemUiHelper) : PagerAdapter() {
    private var filePaths = ArrayList<String>()
    private var count: Int = 0

    private val imageViewPos = SparseArray<ImageViewState>()

    private lateinit var hentaiBox: Box<HentaiManga>
    private lateinit var gChaptersBox: Box<GroupleChapter>

    init {
        BigImageViewer.initialize(GlideImageLoader.with(mContext))
    }

    constructor(mContext: Context, uiHelper: SystemUiHelper, images: List<String>): this(mContext, uiHelper) {
        ImageAdapter.type = Utils.HENTAI
        ImageAdapter.offset = images.size
        count = ImageAdapter.offset
        filePaths.addAll(images)
    }

    constructor(mContext: Context, uiHelper: SystemUiHelper, images: List<String>, box: Box<*>, type: Byte): this(mContext, uiHelper) {
        ImageAdapter.type = type

        if(type == Utils.HENTAI) {
            @Suppress("UNCHECKED_CAST")
            this.hentaiBox = box as Box<HentaiManga>
        }else{
            @Suppress("UNCHECKED_CAST")
            this.gChaptersBox = box as Box<GroupleChapter>
        }
        ImageAdapter.offset = images.size
        count = ImageAdapter.offset
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
        if (count > ImageAdapter.offset) {
            count = ImageAdapter.offset

            if (filePaths.size > ImageAdapter.offset) {
                filePaths.subList(ImageAdapter.offset, filePaths.size).clear()
            }
        }
        ImageAdapter.offset = images.size
        count += ImageAdapter.offset

        filePaths.addAll(0, images)
        imageViewPos.clear()
        notifyDataSetChanged()
    }

    fun nextChapterOnline(link: String) {
        NextChapterOnline(link).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    @SuppressLint("StaticFieldLeak")
    private inner class NextChapterOnline(val link: String) : AsyncTask<Void, Void, Boolean>() {
        internal var tempLinks = ArrayList<String>()

        override fun doInBackground(vararg voids: Void): Boolean? {
            try {
                if (ImageAdapter.type == Utils.GROUPLE) {

                    val mainPage = Jsoup.connect(link).data("mtr", "1").get()

                    val script = mainPage.selectFirst("script:containsData(rm_h.init)")
                    val content = script.html()
                    val rows = content.split("\\r?\\n".toRegex())
                    var needed = rows[rows.size - 1]
                    needed = needed.substring(needed.indexOf('[') + 1, needed.lastIndexOf(']'))
                    val parts = needed.split("],")
                    for (part in parts) {
                        val link = part.replace("[\\['\"\\]]".toRegex(), "").split(",")
                        val ext = link[2].split("\\?").first()
                        val img = link[1] + link[0] + ext
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
            } catch (e: Exception) {
                Log.e("lol", e.localizedMessage)
                e.printStackTrace(Utils.getPrintWriter())
                return false
            }

            return true
        }

        override fun onPostExecute(aBoolean: Boolean) {
            if (aBoolean) {
                if (count > ImageAdapter.offset) {
                    count = ImageAdapter.offset
                    if (filePaths.size > ImageAdapter.offset) {
                        filePaths.subList(ImageAdapter.offset, filePaths.size).clear()
                    }
                }
                ImageAdapter.offset = tempLinks.size
                if(type == Utils.GROUPLE && !ImageActivity.fromBrowser) {

                    val chapter = gChaptersBox.query { equal(GroupleChapter_.link, link) }.findFirst()
                    if (chapter != null) {
                        chapter.page_all = offset
                        gChaptersBox.put(chapter)
                    }
                }else if (!ImageActivity.fromBrowser){
                    val hentaiChapter = hentaiBox.query { equal(HentaiManga_.link, link)}.findFirst()
                    if(hentaiChapter != null) {
                        hentaiChapter.page_all = offset
                        hentaiBox.put(hentaiChapter)
                    }
                }
                count += ImageAdapter.offset
                tempLinks.reverse()
                filePaths.addAll(0, tempLinks)
                imageViewPos.clear()
                notifyDataSetChanged()
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
        return (`object` as View).tag as Int + ImageAdapter.offset
    }

    private fun imageClick(mContext: Context) {
        val appBar = (mContext as Activity).appBar
        if (!opened) {
            appBar.animate().alpha(1.0f).setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator?) {
                    appBar.visibility = View.VISIBLE
                }
            }).duration = 100
            uiHelper.show()
            opened = true
        } else {
            appBar.animate().alpha(0.0f).setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    appBar.visibility = View.GONE
                }
            }).duration = 100
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
                //Log.d("lol", "callback");
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
                //Log.d("lol", "callback");
            }

            override fun onSuccess(image: File) {
                // Image was retrieved successfully (either from cache or network)
                //Log.d("lol", "callback wtf")
                val imageView = iView.ssiv

                if (imageView != null) {
                    imageView.setMinimumTileDpi(160)

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
                            val display = (mContext as Activity).windowManager.defaultDisplay
                            val size = Point()
                            display.getSize(size)
                            val width = size.x

                            if (imageViewPos.get(pos, null) != null) {
                                val state = imageViewPos.get(pos)
                                imageView.setScaleAndCenter(state.scale, state.center)
                            }else if(imageView.sWidth < width){
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
                //Log.e("lol", error.message)
                error.printStackTrace()
                error.printStackTrace(Utils.getPrintWriter())
                // Image download failed
            }
        }

        bigImage.setImageLoaderCallback(myImageLoaderCallback)
        Log.d("lol", "filepath: ${filePaths[position]}")
        bigImage.showImage(Uri.parse(filePaths[position]))
        if (filePaths.size < position + 1)
            BigImageViewer.prefetch(Uri.parse(filePaths[position + 1]))

        view.tag = position
        container.addView(view)
        return view
    }

    companion object {
        var offset: Int = 0
        var opened: Boolean = false
        private var type: Byte = 0
    }

}
