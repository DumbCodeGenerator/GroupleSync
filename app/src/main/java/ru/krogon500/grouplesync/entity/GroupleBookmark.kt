package ru.krogon500.grouplesync.entity

import android.graphics.Bitmap
import android.view.View
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener
import io.objectbox.annotation.Backlink
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToMany
import ru.krogon500.grouplesync.adapter.GroupleAdapter
import ru.krogon500.grouplesync.fragment.GroupleFragment

@Entity class GroupleBookmark(@Id(assignable = true) var id: Long = 0,
                              var title: String,
                              var link: String,
                              var coverLink: String,
                              var readedLink: String,
                              var page: Int = 0,
                              var isNew: Boolean = false){
    @Backlink
    lateinit var chapters: ToMany<GroupleChapter>

    @Transient var cover: Bitmap? = null

    fun setCover(adapter: GroupleAdapter) {
        GroupleFragment.imageLoader.loadImage(coverLink, object : SimpleImageLoadingListener() {

            override fun onLoadingComplete(imageUri: String?, view: View?, loadedImage: Bitmap?) {
                loadedImage ?: return
                cover = loadedImage
                adapter.notifyDataSetChanged()
            }
        })
    }
}