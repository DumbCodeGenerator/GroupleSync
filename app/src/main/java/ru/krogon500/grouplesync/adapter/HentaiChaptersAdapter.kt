package ru.krogon500.grouplesync.adapter

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.github.lzyzsd.circleprogress.DonutProgress
import io.objectbox.relation.ToMany
import kotlinx.android.synthetic.main.manga_chapters.*
import ru.krogon500.grouplesync.R
import ru.krogon500.grouplesync.Utils
import ru.krogon500.grouplesync.Utils.isMyServiceRunning
import ru.krogon500.grouplesync.entity.HentaiManga
import ru.krogon500.grouplesync.event.DownloadEvent
import ru.krogon500.grouplesync.fragment.HentaiFragment.Companion.hentaiBox
import ru.krogon500.grouplesync.fragment.HentaiFragment.Companion.mPass
import ru.krogon500.grouplesync.fragment.HentaiFragment.Companion.mUser
import ru.krogon500.grouplesync.service.DownloadService
import se.ajgarn.mpeventbus.MPEventBus


class HentaiChaptersAdapter(private val mContext: Context, private val origin_manga: HentaiManga) : BaseAdapter() {
    var hChapters: ToMany<HentaiManga> = origin_manga.relateds

    val checkedItems: LinkedHashMap<Int, Boolean> = LinkedHashMap()

    val isAllUnchecked: Boolean
        get() {
            if(checkedItems.values.any { it })
                return false
            return true
        }

    init {
        hChapters.sortBy{it.date}
    }

    fun update(hChapters: ToMany<HentaiManga>){
        this.hChapters = hChapters
        this.hChapters.sortBy{it.date}
        notifyDataSetChanged()
    }

    fun setSaved(position: Int) {
        val chapterItem = hChapters[position]
        chapterItem.downloading = false
        chapterItem.saved = true
        hentaiBox.put(chapterItem)
        notifyDataSetChanged()
    }

    fun setLoading(position: Int) {
        val chapterItem = hChapters[position]
        chapterItem.saved = false
        chapterItem.downloading = true
        hentaiBox.put(chapterItem)
        notifyDataSetChanged()
    }

    fun setDownload(position: Int) {
        val chapterItem = hChapters[position]
        chapterItem.saved = false
        chapterItem.downloading = false
        hentaiBox.put(chapterItem)
        notifyDataSetChanged()
    }

    fun checkAll() {
        for (i in 0 until count)
            checkedItems[i] = true
        notifyDataSetChanged()
    }

    fun uncheckAll() {
        for (i in 0 until count)
            checkedItems[i] = false
        val dSel = (mContext as Activity).findViewById<HorizontalScrollView>(R.id.scroll)
        dSel.visibility = View.GONE
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
            convertView = inflater.inflate(R.layout.chapter_item, parent, false)
            viewHolder = ViewHolder()
            viewHolder.title = convertView!!.findViewById(R.id.chapterTitle)
            viewHolder.pages = convertView.findViewById(R.id.page)
            viewHolder.selected = convertView.findViewById(R.id.selected)
            viewHolder.download = convertView.findViewById(R.id.download)
            viewHolder.loading = convertView.findViewById(R.id.chapterLoading)
            viewHolder.saved = convertView.findViewById(R.id.saved)
            convertView.tag = viewHolder
        } else {
            viewHolder = convertView.tag as ViewHolder
        }
        val chapterItem = hChapters[position]

        if(chapterItem.page_all == 0)
            viewHolder.pages.text = "Страница: ${chapterItem.page + 1}"
        else
            viewHolder.pages.text = "Страница: ${chapterItem.page + 1}/${chapterItem.page_all}"

        viewHolder.selected.visibility = View.VISIBLE
        viewHolder.selected.setOnCheckedChangeListener { _, b ->
            checkedItems[position] = b
            val dSel = (mContext as Activity).scroll
            if (b && dSel != null && dSel.visibility != View.VISIBLE) {
                dSel.visibility = View.VISIBLE
            } else if (!b && dSel != null && dSel.visibility != View.GONE && isAllUnchecked) {
                dSel.visibility = View.GONE
            }
        }
        viewHolder.selected.isChecked = checkedItems.getOrElse(position) {false}

        viewHolder.title.text = chapterItem.title

        if (chapterItem.readed)
            viewHolder.title.setTextColor(Color.GRAY)
        else
            viewHolder.title.setTextColor(Color.WHITE)

        viewHolder.download.visibility = if (chapterItem.saved || chapterItem.downloading) View.GONE else View.VISIBLE
        viewHolder.loading.visibility = if (chapterItem.downloading) View.VISIBLE else View.GONE
        viewHolder.saved.visibility = if (chapterItem.saved) View.VISIBLE else View.GONE

        viewHolder.download.setOnClickListener {
            setLoading(position)
            if (!mContext.isMyServiceRunning(DownloadService::class.java)) {
                Log.d("lol", "ne runit")
                val service = Intent(mContext, DownloadService::class.java)
                service.putExtra("info", DownloadEvent(chapterItem.link, chapterItem.title, position, chapterItem.id, origin_manga.id, Utils.HENTAI, mUser, mPass))
                mContext.startService(service)
            }else
                MPEventBus.getDefault().postToAll(DownloadEvent(chapterItem.link, chapterItem.title, position, chapterItem.id, origin_manga.id, Utils.HENTAI, mUser, mPass))
        }
        return convertView

    }

    private class ViewHolder {
        internal lateinit var title: TextView
        internal lateinit var pages: TextView
        internal lateinit var selected: CheckBox
        internal lateinit var download: ImageButton
        internal lateinit var loading: DonutProgress
        internal lateinit var saved: ImageView
    }
}
