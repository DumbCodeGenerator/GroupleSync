package ru.krogon500.grouplesync.entity

import android.graphics.Bitmap
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.nostra13.universalimageloader.core.ImageLoader
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener
import io.objectbox.annotation.Backlink
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToMany
import ru.krogon500.grouplesync.interfaces.ICoverSettable

@Entity class GroupleBookmark(@Id(assignable = true) var id: Long = 0,
                              var title: String,
                              var link: String,
                              var coverLink: String,
                              var readedLink: String,
                              var page: Int = 0,
                              var isNew: Boolean = false):ICoverSettable{

    @Backlink
    lateinit var chapters: ToMany<GroupleChapter>

    @Transient var cover: Bitmap? = null

    override fun setCover(imageLoader: ImageLoader?, adapter: RecyclerView.Adapter<*>?, position: Int) {
        imageLoader?.loadImage(coverLink, object : SimpleImageLoadingListener() {

            override fun onLoadingComplete(imageUri: String?, view: View?, loadedImage: Bitmap?) {
                loadedImage ?: return
                cover = loadedImage
                adapter?.notifyItemChanged(position) ?: return
            }
        }) ?: return
    }

}