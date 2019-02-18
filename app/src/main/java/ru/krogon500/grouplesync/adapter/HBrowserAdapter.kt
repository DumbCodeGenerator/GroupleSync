package ru.krogon500.grouplesync.adapter

import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.hbrowser_elem.view.*
import ru.krogon500.grouplesync.R
import ru.krogon500.grouplesync.SpacesItemDecoration
import ru.krogon500.grouplesync.holder.ClickableViewHolder
import ru.krogon500.grouplesync.items.MangaItem

class HBrowserAdapter(private var mangaItems: ArrayList<MangaItem> = ArrayList(), var addFooter: Boolean = false) : ClickableRecyclerViewAdapter<RecyclerView.ViewHolder>() {
    val FOOTER_VIEW = 1
    val space = 30
    var selectedItem: Int? = null

    init {
        if(mangaItems.size > 0)
            mangaItems.forEach { it.setCover(this) }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)

        if(recyclerView.itemDecorationCount > 0 && recyclerView.getItemDecorationAt(0) is DividerItemDecoration){
            recyclerView.removeItemDecorationAt(0)
            recyclerView.addItemDecoration(SpacesItemDecoration(space))
        }else if(recyclerView.itemDecorationCount == 0)
            recyclerView.addItemDecoration(SpacesItemDecoration(space))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if(viewType == FOOTER_VIEW){
            val itemView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.is_loading, parent, false)

            FooterViewHolder(itemView)
        }else{
            val itemView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.hbrowser_elem, parent, false)
            ItemViewHolder(itemView, onItemClickListener)
        }
    }

    fun update(mangaItems: ArrayList<MangaItem>, append: Boolean) {
        mangaItems.forEach { it.setCover(this) }
        if (append) {
            this.mangaItems.addAll(mangaItems)
        } else {
            this.mangaItems = mangaItems
        }
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return if(mangaItems.size == 0) 0 else if(addFooter) mangaItems.size + 1 else mangaItems.size
    }

    fun getItem(position: Int): MangaItem {
        return mangaItems[position]
    }

    override fun getItemId(position: Int): Long {
        if(position == mangaItems.size){
            return FOOTER_VIEW.toLong()
        }

        return mangaItems[position].id
    }

    override fun getItemViewType(position: Int): Int {
        if(position == mangaItems.size){
            return FOOTER_VIEW
        }
        return super.getItemViewType(position)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if(holder is ItemViewHolder) {
            holder.itemView.setOnLongClickListener {
                selectedItem = position
                false
            }
            val mangaItem = mangaItems[position]
            holder.title.text = mangaItem.title
            holder.series.text = String.format("Серия: %s", mangaItem.series)
            holder.pluses.text = mangaItem.pluses
            holder.tags.text = String.format("Тэги: %s", mangaItem.tags)
            if (mangaItem.cover != null)
                holder.covers.setImageBitmap(mangaItem.cover)
            else
                holder.covers.setImageResource(android.R.color.darker_gray)
        }
    }

    class ItemViewHolder(itemView: View, listener: View.OnClickListener?): ClickableViewHolder(itemView, listener), View.OnCreateContextMenuListener{
        override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
            menu?.add(Menu.NONE, 1, 0, "Все главы")
            menu?.add(Menu.NONE, 2, 0, "Добавить в избранное")
        }

        val title: TextView = itemView.title
        val series: TextView = itemView.series
        val pluses: TextView = itemView.pluses
        val tags: TextView = itemView.tags
        val covers: ImageView = itemView.cover

        init {
            itemView.tag = this
            itemView.setOnCreateContextMenuListener(this)
        }
    }

    class FooterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
