package ru.krogon500.grouplesync.holder

import android.view.View
import androidx.recyclerview.widget.RecyclerView

abstract class ClickableViewHolder(itemView: View, listener: View.OnClickListener?) : RecyclerView.ViewHolder(itemView) {
    init {
        itemView.setOnClickListener(listener)
    }
}