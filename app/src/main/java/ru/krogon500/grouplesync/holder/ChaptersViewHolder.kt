package ru.krogon500.grouplesync.holder

import android.view.View
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.github.lzyzsd.circleprogress.DonutProgress
import kotlinx.android.synthetic.main.chapter_item.view.*
import ru.krogon500.grouplesync.interfaces.OnItemClickListener

class ChaptersViewHolder(itemView: View, listener: OnItemClickListener?) : ClickableViewHolder(itemView, listener), View.OnLongClickListener {

    override fun onLongClick(v: View?): Boolean {
        selected.isChecked = !selected.isChecked
        return true
    }

    val title: TextView = itemView.chapterTitle
    val pages: TextView = itemView.page
    val selected: CheckBox = itemView.selected
    val download: ImageButton = itemView.download
    val loading: DonutProgress = itemView.chapterLoading
    val saved: ImageView = itemView.saved

    init {
        itemView.setOnLongClickListener(this)
    }
}