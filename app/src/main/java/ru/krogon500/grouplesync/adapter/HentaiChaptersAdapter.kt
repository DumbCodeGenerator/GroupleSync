package ru.krogon500.grouplesync.adapter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
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
import ru.krogon500.grouplesync.holder.ChaptersViewHolder
import ru.krogon500.grouplesync.service.DownloadService
import se.ajgarn.mpeventbus.MPEventBus


class HentaiChaptersAdapter(private val mContext: Context, private val origin_manga: HentaiManga) : ClickableRecyclerViewAdapter<ChaptersViewHolder>() {
    var hChapters: ToMany<HentaiManga> = origin_manga.relateds
    val checkedItems: LinkedHashMap<Int, Boolean> = LinkedHashMap()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChaptersViewHolder {
        val itemView = LayoutInflater.from(parent.context)
                .inflate(R.layout.chapter_item, parent, false)
        return ChaptersViewHolder(itemView, onItemClickListener)
    }

    override fun onBindViewHolder(holder: ChaptersViewHolder, position: Int) {
        val chapterItem = hChapters[position]

        if(chapterItem.page_all == 0)
            holder.pages.text = String.format("Страница: %s", chapterItem.page + 1)
        else
            holder.pages.text = String.format("Страница: %s/%s", chapterItem.page + 1, chapterItem.page_all)

        holder.selected.visibility = View.VISIBLE
        holder.selected.setOnCheckedChangeListener { _, b ->
            checkedItems[position] = b
            val dSel = (mContext as Activity).scroll
            if (b && dSel != null && dSel.visibility != View.VISIBLE) {
                dSel.visibility = View.VISIBLE
            } else if (!b && dSel != null && dSel.visibility != View.GONE && isAllUnchecked) {
                dSel.visibility = View.GONE
            }
        }
        holder.selected.isChecked = checkedItems.getOrElse(position) {false}

        holder.title.text = chapterItem.title

        if (chapterItem.readed)
            holder.title.setTextColor(Color.GRAY)
        else
            holder.title.setTextColor(Color.WHITE)

        holder.download.visibility = if (chapterItem.saved || chapterItem.downloading) View.GONE else View.VISIBLE
        holder.loading.visibility = if (chapterItem.downloading) View.VISIBLE else View.GONE
        holder.saved.visibility = if (chapterItem.saved) View.VISIBLE else View.GONE

        holder.download.setOnClickListener {
            setLoading(position)
            if (!mContext.isMyServiceRunning(DownloadService::class.java)) {
                Log.d("lol", "ne runit")
                val service = Intent(mContext, DownloadService::class.java)
                service.putExtra("info", DownloadEvent(chapterItem.link, chapterItem.title, position, chapterItem.id, origin_manga.id, Utils.HENTAI, mUser, mPass))
                mContext.startService(service)
            }else
                MPEventBus.getDefault().postToAll(DownloadEvent(chapterItem.link, chapterItem.title, position, chapterItem.id, origin_manga.id, Utils.HENTAI, mUser, mPass))
        }
    }

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
        for (i in 0 until itemCount)
            checkedItems[i] = true
        notifyDataSetChanged()
    }

    fun uncheckAll() {
        for (i in 0 until itemCount)
            checkedItems[i] = false
        val dSel = (mContext as Activity).findViewById<HorizontalScrollView>(R.id.scroll)
        dSel.visibility = View.GONE
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
}
