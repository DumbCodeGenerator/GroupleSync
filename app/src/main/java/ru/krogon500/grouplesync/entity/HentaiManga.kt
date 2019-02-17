package ru.krogon500.grouplesync.entity

import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.view.View
import com.nostra13.universalimageloader.core.ImageLoader
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener
import io.objectbox.annotation.Backlink
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToMany
import io.objectbox.relation.ToOne
import ru.krogon500.grouplesync.adapter.HentaiAdapter
import ru.krogon500.grouplesync.adapter.HentaiBrowserChaptersAdapter

@Entity class HentaiManga(@Id(assignable = true) var id: Long = 0,
                          var title: String,
                          var link: String,
                          var date: Long = 0,
                          var coverLink: String = "",
                          var series: String = "",
                          var page: Int = 0,
                          var page_all: Int = 0,
                          var inFavs: Boolean = false,
                          var hasChapters: Boolean = false,
                          var saved: Boolean = false,
                          var downloading: Boolean = false,
                          var readed: Boolean = false){
    lateinit var origin: ToOne<HentaiManga>

    @Backlink
    lateinit var relateds: ToMany<HentaiManga>

    @Transient var cover: Bitmap? = null

    val Bitmap.cropWidth : Int
        get() = (this.height.toDouble()*(100.toDouble()/140.toDouble())).toInt()

    fun setCover(imageLoader: ImageLoader?, adapter: HentaiAdapter? = null, adapter2: HentaiBrowserChaptersAdapter? = null) {
        imageLoader?.loadImage(coverLink, object : SimpleImageLoadingListener() {
            override fun onLoadingComplete(imageUri: String?, view: View?, loadedImage: Bitmap?) {
                loadedImage ?: return

                cover = ThumbnailUtils.extractThumbnail(loadedImage, loadedImage.cropWidth, loadedImage.height, ThumbnailUtils.OPTIONS_RECYCLE_INPUT)

                adapter?.notifyDataSetChanged() ?: adapter2?.notifyDataSetChanged() ?: return
            }
        }) ?: return
    }
}