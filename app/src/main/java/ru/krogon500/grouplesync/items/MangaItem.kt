package ru.krogon500.grouplesync.items

import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.nostra13.universalimageloader.core.ImageLoader
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener
import ru.krogon500.grouplesync.interfaces.ICoverSettable

class MangaItem(val id: Long, val title: String, val link: String):ICoverSettable {

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

    override fun setCover(imageLoader: ImageLoader?, adapter: RecyclerView.Adapter<*>?, position: Int) {
        coverLink ?: return
        imageLoader?.loadImage(coverLink, object : SimpleImageLoadingListener() {
            override fun onLoadingComplete(imageUri: String?, view: View?, loadedImage: Bitmap?) {
                loadedImage ?: return

                cover = ThumbnailUtils.extractThumbnail(loadedImage, loadedImage.cropWidth, loadedImage.height, ThumbnailUtils.OPTIONS_RECYCLE_INPUT)

                adapter?.notifyItemChanged(position) ?: return
            }
        }) ?: return
    }
}
