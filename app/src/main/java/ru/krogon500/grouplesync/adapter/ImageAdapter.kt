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
import android.widget.RelativeLayout
import androidx.viewpager.widget.PagerAdapter
import com.davemorrissey.labs.subscaleview.ImageViewState
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.github.piasy.biv.BigImageViewer
import com.github.piasy.biv.loader.ImageLoader
import com.github.piasy.biv.loader.glide.GlideImageLoader
import com.github.piasy.biv.view.GlideImageViewFactory
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query
import io.objectbox.relation.ToMany
import kotlinx.android.synthetic.main.image_fragment.view.*
import org.jsoup.Jsoup
import ru.krogon500.grouplesync.App
import ru.krogon500.grouplesync.CustomProgressIndicator
import ru.krogon500.grouplesync.R
import ru.krogon500.grouplesync.Utils
import ru.krogon500.grouplesync.activity.ImageActivity
import ru.krogon500.grouplesync.entity.GroupleBookmark
import ru.krogon500.grouplesync.entity.GroupleChapter
import ru.krogon500.grouplesync.entity.HentaiManga
import ru.krogon500.grouplesync.entity.HentaiManga_
import ru.krogon500.grouplesync.system_helper.SystemUiHelper
import java.io.File
import java.net.URL
import java.util.*
import java.util.regex.Pattern


class ImageAdapter : PagerAdapter {
    private var chapters: ArrayList<String>? = null
    private var count: Int = 0
    lateinit var mUser: String
    lateinit var mPass: String
    private var filePaths = ArrayList<String>()
    private val mContext: Context
    private val imageViewPos = SparseArray<ImageViewState>()
    private val uiHelper: SystemUiHelper
    private lateinit var hentaiBox: Box<HentaiManga>
    lateinit var gChaptersBox: Box<GroupleChapter>
    lateinit var gChapters: ToMany<GroupleChapter>

    constructor(mContext: Context, path: String, uiHelper: SystemUiHelper, bId: Long) {
        this.mContext = mContext
        this.uiHelper = uiHelper
        ImageAdapter.type = Utils.GROUPLE

        val gBookmark = ((mContext as Activity).application as App).boxStore.boxFor<GroupleBookmark>()[bId]
        gChapters = gBookmark.chapters
        gChaptersBox = (mContext.application as App).boxStore.boxFor()

        BigImageViewer.initialize(GlideImageLoader.with(mContext))

        val images = Utils.getSavedListFile(path) ?: return

        ImageAdapter.offset = images.size
        count = ImageAdapter.offset
        filePaths.addAll(images)
    }

    constructor(mContext: Context, uiHelper: SystemUiHelper, linkImages: ArrayList<String>, chapters: ArrayList<String>?, currentChapter: Int, user: String, pass: String) {
        this.mContext = mContext
        this.uiHelper = uiHelper
        ImageAdapter.type = Utils.HENTAI
        this.chapters = chapters
        ImageAdapter.currentChapter = currentChapter

        if (chapters != null && chapters.size > currentChapter + 1)
            ImageAdapter.nextChapter = chapters[currentChapter + 1]

        BigImageViewer.initialize(GlideImageLoader.with(mContext))

        mUser = user
        mPass = pass
        ImageAdapter.offset = linkImages.size
        count = ImageAdapter.offset
        filePaths = linkImages
        if(chapters != null) {
            hentaiBox = ((mContext as Activity).application as App).boxStore.boxFor()
            val hentaiManga = hentaiBox.query{
                equal(HentaiManga_.link, chapters[currentChapter])
            }.findFirst() ?: return
            hentaiManga.page_all = offset
            hentaiBox.put(hentaiManga)
        }
    }

    constructor(mContext: Context, uiHelper: SystemUiHelper, linkImages: ArrayList<String>, chapters: ArrayList<String>?, currentChapter: Int, bId: Long) {
        this.mContext = mContext
        this.uiHelper = uiHelper
        ImageAdapter.type = Utils.GROUPLE
        this.chapters = chapters
        ImageAdapter.currentChapter = currentChapter

        if (chapters != null && chapters.size > currentChapter + 1)
            ImageAdapter.nextChapter = chapters[currentChapter + 1]

        BigImageViewer.initialize(GlideImageLoader.with(mContext))

        ImageAdapter.offset = linkImages.size
        count = ImageAdapter.offset
        filePaths = linkImages
        if(chapters != null) {
            val gBookmark = ((mContext as Activity).application as App).boxStore.boxFor<GroupleBookmark>()[bId]
            gChapters = gBookmark.chapters
            gChaptersBox = (mContext.application as App).boxStore.boxFor()
            val chapter = gChapters.find { it.link == chapters[currentChapter] } ?: return
            chapter.page_all = offset
            gChaptersBox.put(chapter)
        }
    }


    override fun getCount(): Int {
        return count
    }

    fun clearViewPos(){
        imageViewPos.clear()
        notifyDataSetChanged()
    }

    fun nextChapter(path: String) {
        val nextChapterImg = Utils.getSavedListFile(path) ?: return
        if (count > ImageAdapter.offset) {
            count = ImageAdapter.offset

            if (filePaths.size > ImageAdapter.offset) {
                filePaths.subList(ImageAdapter.offset, filePaths.size).clear()
            }
        }
        ImageAdapter.offset = nextChapterImg.size
        count += ImageAdapter.offset

        filePaths.addAll(0, nextChapterImg)
        imageViewPos.clear()
        notifyDataSetChanged()
    }

    fun nextChapterOnline() {
        NextChapterOnline().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    @SuppressLint("StaticFieldLeak")
    private inner class NextChapterOnline : AsyncTask<Void, Void, Boolean>() {
        internal var tempLinks = ArrayList<String>()

        override fun doInBackground(vararg voids: Void): Boolean? {
            try {
                ImageAdapter.nextChapter ?:
                throw Exception("Дальше глав нет!")

                if (ImageAdapter.type == Utils.GROUPLE) {

                    currentChapter = chapters?.indexOf(nextChapter) ?: 0
                    val url = URL(ImageAdapter.nextChapter)
                    val protocol = url.protocol
                    val host = url.host

                    val mainPage = Jsoup.connect(ImageAdapter.nextChapter).data("mtr", "1").get()
                    val script = mainPage.selectFirst("script:containsData(rm_h.init)")
                    val content = script.html()
                    val pattern = Pattern.compile("var nextChapterLink = \"(.*)\";")
                    val matcher = pattern.matcher(content)
                    if (matcher.find()) {
                        ImageAdapter.nextChapter = String.format("%s://%s%s", protocol, host, matcher.group(1)).replace("?mtr=1", "")
                        if (ImageAdapter.nextChapter!!.contains("/list/like"))
                            ImageAdapter.nextChapter = null
                    }
                    val rows = content.split("\\r?\\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    var needed = rows[rows.size - 1]
                    needed = needed.substring(needed.indexOf('[') + 1, needed.lastIndexOf(']'))
                    val parts = needed.split("],".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    for (part in parts) {
                        val link = part.replace("[\\['\"\\]]".toRegex(), "").split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        val ext = link[2].split("\\?".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
                        val img = link[1] + link[0] + ext
                        tempLinks.add(img)
                    }
                } else {
                    if (!Utils.login(Utils.HENTAI, mUser, mPass))
                        return false

                    val mainPage = Utils.getPage(Utils.HENTAI, mUser, mPass, nextChapter!!)

                    currentChapter = chapters!!.indexOf(nextChapter!!)

                    nextChapter = if (chapters!!.size > currentChapter + 1)
                        chapters!![currentChapter + 1]
                    else
                        null

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

        override fun onPostExecute(aBoolean: Boolean?) {
            super.onPostExecute(aBoolean)
            // get a reference to the activity if it is still there
            if (aBoolean!!) {
                if (count > ImageAdapter.offset) {
                    count = ImageAdapter.offset
                    if (filePaths.size > ImageAdapter.offset) {
                        filePaths.subList(ImageAdapter.offset, filePaths.size).clear()
                    }
                }
                ImageAdapter.offset = tempLinks.size
                if(type == Utils.GROUPLE && !ImageActivity.fromBrowser) {
                    val chapter = gChapters.find { it.link == chapters?.get(currentChapter) ?: "" }
                    if (chapter != null) {
                        chapter.page_all = offset
                        gChaptersBox.put(chapter)
                    }
                }else if (!ImageActivity.fromBrowser){
                    val hentaiChapter = hentaiBox.query { equal(HentaiManga_.link, chapters!![currentChapter])}.findFirst()
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
        val rl = (mContext as Activity).findViewById<RelativeLayout>(R.id.rl)
        if (!opened) {
            rl.animate().alpha(1.0f).setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator?) {
                    rl.visibility = View.VISIBLE
                }
            }).duration = 100
            uiHelper.show()
            opened = true
        } else {
            rl.animate().alpha(0.0f).setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    rl.visibility = View.GONE
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

                        }

                        override fun onTileLoadError(e: Exception) {

                        }

                        override fun onPreviewReleased() {

                        }
                    })
                }
            }

            override fun onFail(error: Exception) {
                Log.d("lol", error.localizedMessage)
                // Image download failed
            }
        }

        bigImage.setImageLoaderCallback(myImageLoaderCallback)
        bigImage.showImage(Uri.parse(filePaths[position]))
        if (filePaths.size < position + 1)
            BigImageViewer.prefetch(Uri.parse(filePaths[position + 1]))

        view.tag = position
        container.addView(view)
        return view
    }

    companion object {
        var offset: Int = 0
        var currentChapter: Int = 0
        var opened: Boolean = false
        private var type: Byte = 0
        var nextChapter: String? = null
    }

}
