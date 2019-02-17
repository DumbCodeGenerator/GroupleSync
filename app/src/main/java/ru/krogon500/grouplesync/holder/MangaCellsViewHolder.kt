package ru.krogon500.grouplesync.holder

import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.lzyzsd.circleprogress.DonutProgress
import kotlinx.android.synthetic.main.cellgrid.view.*

class MangaCellsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val textView: TextView = itemView.mangaTitle
    val imageView: ImageView = itemView.mangaCover
    val newSign: ImageView = itemView.new_sign
    val hentaiSaved: ImageButton = itemView.hentai_saved
    val hentaiDButton: ImageButton = itemView.hentai_down_button
    val hentaiDown: DonutProgress = itemView.hentai_down
}