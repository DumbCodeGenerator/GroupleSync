package ru.krogon500.grouplesync.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.hbrowser_elem.view.*
import ru.krogon500.grouplesync.R
import ru.krogon500.grouplesync.RecyclerArray
import ru.krogon500.grouplesync.SpacesItemDecoration
import ru.krogon500.grouplesync.activity.HentaiBrowser
import ru.krogon500.grouplesync.entity.HentaiManga
import ru.krogon500.grouplesync.holder.ClickableViewHolder
import ru.krogon500.grouplesync.interfaces.OnItemClickListener


class HentaiBrowserChaptersAdapter(data: MutableList<HentaiManga>, private var listener: OnItemClickListener? = null) : RecyclerView.Adapter<HentaiBrowserChaptersAdapter.ViewHolder>() {
    var hChapters = RecyclerArray<HentaiManga>(this, HentaiBrowser.imageLoader)
    val space = 30

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.context)
                .inflate(R.layout.hbrowser_elem, parent, false)
        return ViewHolder(itemView, listener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val chapterItem = hChapters[position]

        holder.title.text = chapterItem.title
        holder.series.text = String.format("Серия: %s", chapterItem.series)

        if(chapterItem.cover != null)
            holder.covers.setImageBitmap(chapterItem.cover)
        else
            holder.covers.setImageResource(android.R.color.darker_gray)

        holder.pluses.visibility = View.GONE
        holder.tags.visibility = View.GONE
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        if(recyclerView.itemDecorationCount > 0 && recyclerView.getItemDecorationAt(0) is DividerItemDecoration){
            recyclerView.removeItemDecorationAt(0)
            recyclerView.addItemDecoration(SpacesItemDecoration(space))
        }else if(recyclerView.itemDecorationCount == 0){
            recyclerView.addItemDecoration(SpacesItemDecoration(space))
        }
    }

    init {
        hChapters.addAll(data, false)
        //hChapters.forEachIndexed { index, hentaiManga ->  hentaiManga.setCover(HentaiBrowser.imageLoader, this@HentaiBrowserChaptersAdapter, position = index) }
    }

    fun update(data: MutableList<HentaiManga>){
        hChapters = data as RecyclerArray<HentaiManga>
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return hChapters.size
    }

    fun getItem(i: Int): HentaiManga {
        return hChapters[i]
    }

    override fun getItemId(i: Int): Long {
        return hChapters[i].id
    }

    class ViewHolder(itemView: View, listener: OnItemClickListener?): ClickableViewHolder(itemView, listener){
        val title: TextView = itemView.title
        val series: TextView = itemView.series
        val pluses: TextView = itemView.pluses
        val tags: TextView = itemView.tags
        val covers: ImageView = itemView.cover
    }
}
