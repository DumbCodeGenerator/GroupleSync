package ru.krogon500.grouplesync.holder

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import ru.krogon500.grouplesync.interfaces.OnItemClickListener

abstract class ClickableViewHolder(itemView: View, listener: OnItemClickListener?) : RecyclerView.ViewHolder(itemView) {
    init {
        itemView.setOnClickListener{
            listener?.onItemClick(it, this.adapterPosition) ?: return@setOnClickListener
        }
    }
}