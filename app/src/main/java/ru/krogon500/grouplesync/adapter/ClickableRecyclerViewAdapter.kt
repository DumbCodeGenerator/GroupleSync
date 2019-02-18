package ru.krogon500.grouplesync.adapter

import android.view.View
import androidx.recyclerview.widget.RecyclerView

abstract class ClickableRecyclerViewAdapter<T: RecyclerView.ViewHolder> : RecyclerView.Adapter<T>() {
    protected var onItemClickListener: View.OnClickListener? = null

    fun setItemClickListener(clickListener: View.OnClickListener?) {
        onItemClickListener = clickListener
    }
}