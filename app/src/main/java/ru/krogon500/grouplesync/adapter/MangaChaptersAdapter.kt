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
import io.objectbox.Box
import io.objectbox.relation.ToMany
import kotlinx.android.synthetic.main.manga_chapters.*
import ru.krogon500.grouplesync.R
import ru.krogon500.grouplesync.Utils
import ru.krogon500.grouplesync.Utils.getSpannedFromHtml
import ru.krogon500.grouplesync.Utils.isMyServiceRunning
import ru.krogon500.grouplesync.entity.GroupleChapter
import ru.krogon500.grouplesync.event.DownloadEvent
import ru.krogon500.grouplesync.service.DownloadService
import se.ajgarn.mpeventbus.MPEventBus


class MangaChaptersAdapter(private val mContext: Context,
                           var gChapters: ToMany<GroupleChapter>,
                           var gChaptersBox: Box<GroupleChapter>) : BaseAdapter() {
    //private final ArrayList<String> chapterTitles, links;
    val checkedItems: LinkedHashMap<Int, Boolean> = LinkedHashMap()
    var reversed: Boolean = false
    //private var gChapters: ArrayList<GroupleChapter> = ArrayList()

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
        for (i in 0 until count)
            checkedItems[i] = true
        notifyDataSetChanged()
    }

    fun uncheckAll() {
        for (i in 0 until count)
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
                count - 1
            }
        } else {
            return try {
                val readedItem = gChapters.last { it.readed }
                val readed = gChapters.indexOf(readedItem)
                if(readed + 1 < count) readed + 1 else readed
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

    override fun getCount(): Int {
        return gChapters.size
    }

    override fun getItem(i: Int): Any {
        return gChapters[i]
    }

    override fun getItemId(i: Int): Long {
        return gChapters[i].id
    }

    @Suppress("NAME_SHADOWING")
    @SuppressLint("SetTextI18n")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var convertView = convertView
        val viewHolder: ViewHolder
        if (convertView == null) {
            val inflater = mContext
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
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
        val chapterItem = gChapters[position]

        //viewHolder.selected.setTag(links.get(position));
        viewHolder.selected!!.setOnCheckedChangeListener { _, b ->
            checkedItems[position] = b
            val dSel = (mContext as Activity).findViewById<HorizontalScrollView>(R.id.scroll)
            if (b && dSel != null && dSel.visibility != View.VISIBLE) {
                dSel.visibility = View.VISIBLE
            } else if (!b && dSel != null && dSel.visibility != View.GONE && isAllUnchecked) {
                dSel.visibility = View.GONE
            }
        }
        viewHolder.selected!!.isChecked = checkedItems.getOrElse(position) {false}
        viewHolder.title!!.text = chapterItem.title.getSpannedFromHtml()

        viewHolder.title!!.setTextColor(if (chapterItem.readed) Color.GRAY else Color.WHITE)

        if(chapterItem.page_all == 0)
            viewHolder.pages!!.text = "Страница: ${chapterItem.page + 1}"
        else
            viewHolder.pages!!.text = "Страница: ${chapterItem.page + 1}/${chapterItem.page_all}"
        //viewHolder.pages.setTag(pages[position]);

        viewHolder.pages!!.setTextColor(if (chapterItem.readed) Color.GRAY else Color.WHITE)

        viewHolder.download!!.visibility = if (chapterItem.saved || chapterItem.downloading) View.GONE else View.VISIBLE
        viewHolder.loading!!.visibility = if (chapterItem.downloading) View.VISIBLE else View.GONE
        viewHolder.saved!!.visibility = if (chapterItem.saved) View.VISIBLE else View.GONE

        viewHolder.download!!.setOnClickListener {
            setLoading(position)
            if (!mContext.isMyServiceRunning(DownloadService::class.java)) {
                Log.d("lol", "ne runit")
                val service = Intent(mContext, DownloadService::class.java)
                service.putExtra("info", DownloadEvent(chapterItem.link, chapterItem.bookmark.target.title, position, chapterItem.id, Utils.GROUPLE, "b" + chapterItem.bookmark.target.id))
                mContext.startService(service)
            }else
            MPEventBus.getDefault().postToAll(DownloadEvent(chapterItem.link, chapterItem.bookmark.target.title, position, chapterItem.id, Utils.GROUPLE, "b" + chapterItem.bookmark.target.id))
        }
        return convertView

    }

    private class ViewHolder {
        internal var title: TextView? = null
        internal var pages: TextView? = null
        internal var selected: CheckBox? = null
        internal var download: ImageButton? = null
        internal var loading: DonutProgress? = null
        internal var saved: ImageView? = null
    }
}
