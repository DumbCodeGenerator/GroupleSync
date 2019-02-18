package ru.krogon500.grouplesync.adapter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.objectbox.Box
import io.objectbox.relation.ToMany
import kotlinx.android.synthetic.main.manga_chapters.*
import ru.krogon500.grouplesync.R
import ru.krogon500.grouplesync.Utils
import ru.krogon500.grouplesync.Utils.getSpannedFromHtml
import ru.krogon500.grouplesync.Utils.isMyServiceRunning
import ru.krogon500.grouplesync.entity.GroupleChapter
import ru.krogon500.grouplesync.event.DownloadEvent
import ru.krogon500.grouplesync.holder.ChaptersViewHolder
import ru.krogon500.grouplesync.service.DownloadService
import se.ajgarn.mpeventbus.MPEventBus


class MangaChaptersAdapter(private val mContext: Context,
                           var gChapters: ToMany<GroupleChapter>,
                           var gChaptersBox: Box<GroupleChapter>) : ClickableRecyclerViewAdapter<ChaptersViewHolder>() {
    val checkedItems: LinkedHashMap<Int, Boolean> = LinkedHashMap()
    var reversed: Boolean = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChaptersViewHolder {
        val itemView = LayoutInflater.from(parent.context)
                .inflate(R.layout.chapter_item, parent, false)
        return ChaptersViewHolder(itemView, onItemClickListener)
    }

    override fun onBindViewHolder(holder: ChaptersViewHolder, position: Int) {
        val chapterItem = gChapters[position]

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
        holder.title.text = chapterItem.title.getSpannedFromHtml()

        holder.title.setTextColor(if (chapterItem.readed) Color.GRAY else Color.WHITE)

        if(chapterItem.page_all == 0)
            holder.pages.text = String.format("Страница: %s" , chapterItem.page + 1)
        else
            holder.pages.text = String.format("Страница: %s/%s", chapterItem.page + 1, chapterItem.page_all)

        holder.pages.setTextColor(if (chapterItem.readed) Color.GRAY else Color.WHITE)

        holder.download.visibility = if (chapterItem.saved || chapterItem.downloading) View.GONE else View.VISIBLE
        holder.loading.visibility = if (chapterItem.downloading) View.VISIBLE else View.GONE
        holder.saved.visibility = if (chapterItem.saved) View.VISIBLE else View.GONE

        holder.download.setOnClickListener {
            setLoading(position)
            if (!mContext.isMyServiceRunning(DownloadService::class.java)) {
                Log.d("lol", "ne runit")
                val service = Intent(mContext, DownloadService::class.java)
                service.putExtra("info", DownloadEvent(chapterItem.link, chapterItem.bookmark.target.title, position, chapterItem.id, Utils.GROUPLE, "b" + chapterItem.bookmark.target.id))
                mContext.startService(service)
            }else
                MPEventBus.getDefault().postToAll(DownloadEvent(chapterItem.link, chapterItem.bookmark.target.title, position, chapterItem.id, Utils.GROUPLE, "b" + chapterItem.bookmark.target.id))
        }
    }

    val isAllUnchecked: Boolean
        get() {
            if(checkedItems.values.any { it })
                return false
            return true
        }

    init {
        gChapters.sortByDescending { it.date }
    }

    fun update(chapterItems: ToMany<GroupleChapter>){
        this.gChapters = chapterItems
        this.gChapters.sortByDescending { it.date }
        notifyDataSetChanged()
    }

    fun setSaved(position: Int) {
        val chapterItem = gChapters[position]
        chapterItem.downloading = false
        chapterItem.saved = true
        gChaptersBox.put(chapterItem)
        notifyDataSetChanged()
    }

    fun setLoading(position: Int) {
        val chapterItem = gChapters[position]
        chapterItem.saved = false
        chapterItem.downloading = true
        gChaptersBox.put(chapterItem)
        notifyDataSetChanged()
    }

    fun setDownload(position: Int) {
        val chapterItem = gChapters[position]
        chapterItem.saved = false
        chapterItem.downloading = false
        gChaptersBox.put(chapterItem)
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
        val dSel = (mContext as Activity).scroll
        dSel.visibility = View.GONE
        notifyDataSetChanged()
    }

    fun checkUnreaded() {
        gChapters.forEachIndexed { index, groupleChapter -> checkedItems[index] = !groupleChapter.readed }
        notifyDataSetChanged()
    }

    fun getLastReaded(): Int{
        if(!reversed) {
            return try {
                val readedItem = gChapters.first { it.readed }
                val readed = gChapters.indexOf(readedItem)
                if(readed - 1 >= 0) readed - 1 else readed
            }catch (e:NoSuchElementException){
                itemCount - 1
            }
        } else {
            return try {
                val readedItem = gChapters.last { it.readed }
                val readed = gChapters.indexOf(readedItem)
                if(readed + 1 < itemCount) readed + 1 else readed
            }catch (e:NoSuchElementException){
                0
            }
        }
    }

    fun reverse(){
        gChapters.reverse()
        reversed = !reversed
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return gChapters.size
    }

    fun getItem(i: Int): GroupleChapter {
        return gChapters[i]
    }

    override fun getItemId(i: Int): Long {
        return gChapters[i].id
    }

}
