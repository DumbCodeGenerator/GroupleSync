package ru.krogon500.grouplesync.interfaces

import androidx.recyclerview.widget.RecyclerView
import com.nostra13.universalimageloader.core.ImageLoader

interface ICoverSettable {
    fun setCover(imageLoader: ImageLoader?, adapter: RecyclerView.Adapter<*>?, position: Int)
}