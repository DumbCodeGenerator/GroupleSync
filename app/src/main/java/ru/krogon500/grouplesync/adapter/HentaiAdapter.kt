package ru.krogon500.grouplesync.adapter

import android.content.Context
import android.content.Intent
import android.util.Log
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.objectbox.Box
import io.objectbox.kotlin.query
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import ru.krogon500.grouplesync.R
import ru.krogon500.grouplesync.RecyclerArray
import ru.krogon500.grouplesync.Utils
import ru.krogon500.grouplesync.Utils.isMyServiceRunning
import ru.krogon500.grouplesync.entity.HentaiManga
import ru.krogon500.grouplesync.entity.HentaiManga_
import ru.krogon500.grouplesync.event.DownloadEvent
import ru.krogon500.grouplesync.event.UpdateEvent
import ru.krogon500.grouplesync.fragment.HentaiFragment
import ru.krogon500.grouplesync.holder.MangaCellsViewHolder
import ru.krogon500.grouplesync.interfaces.OnItemClickListener
import ru.krogon500.grouplesync.service.DownloadService
import se.ajgarn.mpeventbus.MPEventBus
import java.io.File

class HentaiAdapter(private val ctx: Context, private var hentaiBookmarksBox: Box<HentaiManga>, private var listener: OnItemClickListener? = null) : RecyclerView.Adapter<MangaCellsViewHolder>() {
    
    private var hentaiMangas = RecyclerArray<HentaiManga>(this, HentaiFragment.imageLoader)
    private val progresses = SparseArray<ProgressArray>()
    var selectedItem: Int? = null

    internal inner class ProgressArray(var max: Int, var current: Int)

    init {
        hentaiBookmarksBox.init()
        if (!EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().register(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MangaCellsViewHolder {
        val itemView = LayoutInflater.from(parent.context)
                .inflate(R.layout.cellgrid, parent, false)
        return MangaCellsViewHolder(itemView, listener, View.OnCreateContextMenuListener { menu, _, _ ->  menu.add(Menu.NONE, 32, 0, "Удалить")})
    }

    fun Box<HentaiManga>.init(){
        val newHentaiManga = this.query {
            equal(HentaiManga_.inFavs, true)
            sort { o1, o2 -> o1.id.compareTo(o2.id)} }.find().asReversed()

        if(hentaiMangas.isEmpty()){
            hentaiMangas.addAll(newHentaiManga, false)
        }else{
            val newIDs = ArrayList<Long>()
            newHentaiManga.forEach { newIDs.add(it.id) }

            hentaiMangas.removeAll(hentaiMangas.filter { it.id !in newIDs })

            val oldIDs = ArrayList<Long>()
            hentaiMangas.forEach { oldIDs.add(it.id) }

            newHentaiManga.forEachIndexed { index, hentaiManga ->
                if(hentaiManga.id !in oldIDs)
                    hentaiMangas.add(index, hentaiManga)
            }
        }
    }


    override fun getItemCount(): Int {
        return hentaiMangas.size
    }

    fun remove(item: HentaiManga) {
        hentaiBookmarksBox.remove(item)
        val pos = hentaiMangas.indexOf(item)
        hentaiMangas.removeAt(pos)
        notifyItemRemoved(pos)
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onUpdateEvent(event: UpdateEvent) {
        if (event.type != Utils.HENTAI || event.original_id != null)
            return
        if (event.done && event.success) {
            setSaved(event.position)
        }else if(event.done && !event.success){
            setDownload(event.position)
        } else if (!event.success) {
            setLoading(event.position, event.max, event.current)
        }
    }

    private fun setLoading(position: Int, max: Int, current: Int) {
        val item = hentaiMangas[position]
        item.saved = false
        item.downloading = true
        progresses.put(position, ProgressArray(max, current))
        HentaiFragment.hentaiBox.put(item)
        notifyItemChanged(position)
    }

    private fun setSaved(position: Int) {
        val item = hentaiMangas[position]
        item.saved = true
        item.downloading = false
        if (progresses.get(position) != null)
            progresses.removeAt(position)
        notifyItemChanged(position)
    }

    private fun setDownload(position: Int) {
        val item = hentaiMangas[position]
        item.saved = false
        item.downloading = false
        item.files = null
        if (progresses.get(position) != null)
            progresses.removeAt(position)
        HentaiFragment.hentaiBox.put(item)
        notifyItemChanged(position)
    }

    fun update(hentaiBookmarksBox: Box<HentaiManga>) {
        this.hentaiBookmarksBox = hentaiBookmarksBox
        hentaiBookmarksBox.init()
        //notifyDataSetChanged()
    }

    fun getItem(i: Int): HentaiManga {
        return hentaiMangas[i]
    }

    override fun getItemId(i: Int): Long {
        return hentaiMangas[i].id
    }

    override fun onBindViewHolder(holder: MangaCellsViewHolder, position: Int) {
        val item = hentaiMangas[position]
        holder.itemView.setOnLongClickListener {
            selectedItem = position
            false
        }
        holder.textView.text = item.title

        if (item.cover != null)
            holder.imageView.setImageBitmap(item.cover)
        else
            holder.imageView.setImageResource(android.R.color.darker_gray)

        when {
            item.hasChapters -> {
                holder.hentaiSaved.visibility = View.GONE
                holder.hentaiDown.visibility = View.GONE
                holder.hentaiDButton.visibility = View.GONE
            }
            item.saved -> {
                holder.hentaiSaved.visibility = View.VISIBLE
                holder.hentaiDown.visibility = View.GONE
                holder.hentaiDButton.visibility = View.GONE
                holder.hentaiSaved.setOnClickListener {
                    val path = Utils.hentaiPath + File.separator + item.id
                    val mangaDir = File(path)
                    if(mangaDir.exists())
                        mangaDir.deleteRecursively()
                    setDownload(position)
                }
            }
            item.downloading -> {
                holder.hentaiSaved.visibility = View.GONE
                holder.hentaiDown.visibility = View.VISIBLE
                if (progresses.get(position) != null) {
                    val sets = progresses.get(position)
                    holder.hentaiDown.progress = sets.current.toFloat()
                    holder.hentaiDown.max = sets.max
                }
                holder.hentaiDButton.visibility = View.GONE
            }
            else -> {
                holder.hentaiSaved.visibility = View.GONE
                holder.hentaiDown.visibility = View.GONE
                holder.hentaiDButton.visibility = View.VISIBLE
            }
        }

        holder.hentaiDButton.setOnClickListener {
            setLoading(position, 100, 0)
            if (!ctx.isMyServiceRunning(DownloadService::class.java)) {
                Log.d("lol", "ne runit")
                val service = Intent(ctx, DownloadService::class.java)
                service.putExtra("info", DownloadEvent(item.link, item.title, position, item.id, null, Utils.HENTAI, HentaiFragment.mUser, HentaiFragment.mPass))
                ctx.startService(service)
            }else
                MPEventBus.getDefault().postToAll(DownloadEvent(item.link, item.title, position, item.id, null, Utils.HENTAI, HentaiFragment.mUser, HentaiFragment.mPass))
        }

        holder.newSign.visibility = View.GONE
    }

}
