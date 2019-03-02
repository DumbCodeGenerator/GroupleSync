package ru.krogon500.grouplesync.holder

import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.github.lzyzsd.circleprogress.DonutProgress
import kotlinx.android.synthetic.main.cellgrid.view.*
import ru.krogon500.grouplesync.interfaces.OnItemClickListener

class MangaCellsViewHolder(itemView: View, listener: OnItemClickListener?, contextMenuCreate: View.OnCreateContextMenuListener?) : ClickableViewHolder(itemView, listener) {
    val textView: TextView = itemView.mangaTitle
    val imageView: ImageView = itemView.mangaCover
    val newSign: ImageView = itemView.new_sign
    val hentaiSaved: ImageButton = itemView.hentai_saved
    val hentaiDButton: ImageButton = itemView.hentai_down_button
    val hentaiDown: DonutProgress = itemView.hentai_down

    init {
        itemView.setOnCreateContextMenuListener(contextMenuCreate)
    }
}