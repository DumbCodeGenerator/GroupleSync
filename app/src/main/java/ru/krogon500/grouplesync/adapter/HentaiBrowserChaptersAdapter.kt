package ru.krogon500.grouplesync.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import kotlinx.android.synthetic.main.hbrowser_elem.view.*
import ru.krogon500.grouplesync.R
import ru.krogon500.grouplesync.activity.HentaiBrowser
import ru.krogon500.grouplesync.entity.HentaiManga


class HentaiBrowserChaptersAdapter(private val mContext: Context, var hChapters: ArrayList<HentaiManga>) : BaseAdapter() {
    //private final ArrayList<String> chapterTitles, links, manga_ids;

    init {
        hChapters.forEach { it.setCover(HentaiBrowser.imageLoader, adapter2 = this@HentaiBrowserChaptersAdapter) }
    }

    fun update(hChapters: ArrayList<HentaiManga>){
        hChapters.forEach { it.setCover(HentaiBrowser.imageLoader, adapter2 = this@HentaiBrowserChaptersAdapter) }
        this.hChapters = hChapters
        notifyDataSetChanged()
    }

    override fun getCount(): Int {
        return hChapters.size
    }

    override fun getItem(i: Int): Any {
        return hChapters[i]
    }

    override fun getItemId(i: Int): Long {
        return hChapters[i].id
    }

    @SuppressLint("SetTextI18n")
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
        val chapterItem = hChapters[position]

        viewHolder.title.text = chapterItem.title
        viewHolder.series.text = "Серия: ${chapterItem.series}"

        if(chapterItem.cover != null)
            viewHolder.covers.setImageBitmap(chapterItem.cover)
        else
            viewHolder.covers.setImageResource(android.R.color.darker_gray)

        viewHolder.pluses.visibility = View.GONE
        viewHolder.tags.visibility = View.GONE

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
