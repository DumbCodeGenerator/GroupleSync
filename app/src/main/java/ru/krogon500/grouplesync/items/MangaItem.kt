package ru.krogon500.grouplesync.items

import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.view.View
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener
import ru.krogon500.grouplesync.activity.HentaiBrowser
import ru.krogon500.grouplesync.adapter.HBrowserAdapter

class MangaItem(val id: Long, val title: String, val link: String) {
    var cover: Bitmap? = null
    var coverLink: String? = null
    lateinit var series: String
    var tags: String? = null
    var pluses: String? = null
    var haveChapters: Boolean = false
    var page: Int = 0
    val Bitmap.cropWidth : Int
        get() = (this.height.toDouble()*(100.toDouble()/140.toDouble())).toInt()

    constructor(id: Long, title: String, series: String, link: String, tags: String, pluses: String, haveChapters: Boolean = false) : this(id, title, link) {
        this.series = series
        this.tags = tags
        this.pluses = pluses
        this.haveChapters = haveChapters
    }

    fun setCover(adapter: HBrowserAdapter) {
        coverLink ?: return
        HentaiBrowser.imageLoader ?: return
        HentaiBrowser.imageLoader!!.loadImage(coverLink, object : SimpleImageLoadingListener() {
            override fun onLoadingComplete(imageUri: String?, view: View?, loadedImage: Bitmap?) {
                loadedImage ?: return

                cover = ThumbnailUtils.extractThumbnail(loadedImage, loadedImage.cropWidth, loadedImage.height, ThumbnailUtils.OPTIONS_RECYCLE_INPUT)

                adapter.notifyDataSetChanged()
            }
        })
    }
}
