package ru.krogon500.grouplesync.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import kotlinx.android.synthetic.main.hbrowser_elem.view.*
import ru.krogon500.grouplesync.R
import ru.krogon500.grouplesync.items.MangaItem

class HBrowserAdapter(private val mContext: Context, private var mangaItems: ArrayList<MangaItem> = ArrayList()) : BaseAdapter() {

    init {
        if(mangaItems.size > 0)
            mangaItems.forEach { it.setCover(this) }
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

    override fun getCount(): Int {
        return mangaItems.size
    }

    override fun getItem(position: Int): Any {
        return mangaItems[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var convertView = convertView
        val viewHolder: ViewHolder
        if (convertView == null) {
            val inflater = (mContext
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater)
            convertView = inflater.inflate(R.layout.hbrowser_elem, parent, false)
            viewHolder = ViewHolder()
            viewHolder.title = convertView!!.title
            viewHolder.series = convertView.series
            viewHolder.pluses = convertView.pluses
            viewHolder.tags = convertView.tags
            viewHolder.covers = convertView.cover
            convertView.tag = viewHolder
        } else {
            viewHolder = convertView.tag as ViewHolder
        }
        val mangaItem = mangaItems[position]
        viewHolder.title.text = mangaItem.title
        viewHolder.series.text = "Серия: ${mangaItem.series}"
        //viewHolder.title.setTag(ids.get(position) + "///" + links.get(position));
        viewHolder.pluses.text = mangaItem.pluses
        viewHolder.tags.text = String.format("Тэги: %s", mangaItem.tags)
        if (mangaItem.cover != null)
            viewHolder.covers.setImageBitmap(mangaItem.cover)
        else
            viewHolder.covers.setImageResource(android.R.color.darker_gray)

        return convertView
    }

    private class ViewHolder {
        internal lateinit var title: TextView
        internal lateinit var series: TextView
        internal lateinit var pluses: TextView
        internal lateinit var tags: TextView
        internal lateinit var covers: ImageView
    }
}
