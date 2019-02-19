package ru.krogon500.grouplesync.adapter

import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.krogon500.grouplesync.SpacesItemDecoration
import ru.krogon500.grouplesync.Utils
import ru.krogon500.grouplesync.holder.ClickableViewHolder
import ru.krogon500.grouplesync.interfaces.OnItemClickListener


class SimpleArrayAdapter(private var titles: MutableList<String>, private var listener: OnItemClickListener? = null): RecyclerView.Adapter<SimpleArrayAdapter.ViewHolder>() {

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        if(recyclerView.itemDecorationCount > 0 && recyclerView.getItemDecorationAt(0) is SpacesItemDecoration){
            recyclerView.removeItemDecorationAt(0)
            recyclerView.addItemDecoration(Utils.dividerItemDecor(recyclerView.context))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)

        return ViewHolder(itemView, listener, parent.context)
    }

    override fun getItemCount(): Int {
        return titles.size
    }

    fun getItem(position: Int): String{
        return titles[position]
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = titles[position]
    }

    class ViewHolder(itemView: View, listener: OnItemClickListener?, context: Context) : ClickableViewHolder(itemView, listener){
        val textView: TextView = itemView.findViewById(android.R.id.text1)
        init {
            val outValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            itemView.setBackgroundResource(outValue.resourceId)
            itemView.isClickable = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                itemView.focusable = View.FOCUSABLE
            }
        }
    }
}