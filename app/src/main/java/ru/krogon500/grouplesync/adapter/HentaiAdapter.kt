package ru.krogon500.grouplesync.adapter

import android.content.Context
import android.content.Intent
import android.util.Log
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.objectbox.Box
import io.objectbox.kotlin.query
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import ru.krogon500.grouplesync.R
import ru.krogon500.grouplesync.Utils
import ru.krogon500.grouplesync.Utils.isMyServiceRunning
import ru.krogon500.grouplesync.entity.HentaiManga
import ru.krogon500.grouplesync.entity.HentaiManga_
import ru.krogon500.grouplesync.event.DownloadEvent
import ru.krogon500.grouplesync.event.UpdateEvent
import ru.krogon500.grouplesync.fragment.HentaiFragment
import ru.krogon500.grouplesync.holder.MangaCellsViewHolder
import ru.krogon500.grouplesync.service.DownloadService
import se.ajgarn.mpeventbus.MPEventBus
import java.io.File
import java.util.*

class HentaiAdapter(private val ctx: Context, private var hentaiBookmarksBox: Box<HentaiManga>) : ClickableRecyclerViewAdapter<MangaCellsViewHolder>() {
    
    private lateinit var hentaiManga: ArrayList<HentaiManga>
    private val progresses = SparseArray<CustomArray>()

    internal inner class CustomArray(var max: Int, var current: Int)

    init {
        hentaiBookmarksBox.init()
        if (!EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().register(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MangaCellsViewHolder {
        val itemView = LayoutInflater.from(parent.context)
                .inflate(R.layout.cellgrid, parent, false)
        return MangaCellsViewHolder(itemView, onItemClickListener)
    }

    fun Box<HentaiManga>.init(){
        hentaiManga = this.query {
            equal(HentaiManga_.inFavs, true)
            sort { o1, o2 -> o1.id.compareTo(o2.id)} }.find() as ArrayList<HentaiManga>
        hentaiManga.reverse()
        hentaiManga.forEach { it.setCover(HentaiFragment.imageLoader, this@HentaiAdapter) }
    }


    override fun getItemCount(): Int {
        return hentaiManga.size
    }

    /*fun remove(pos: Int) {
        hentaiBookmarksBox.remove(hentaiManga[pos])
        notifyDataSetChanged()
    }*/

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onUpdateEvent(event: UpdateEvent) {
        if (event.type != Utils.HENTAI || event.original_id != null)
            return
        if (event.done && event.success) {
            setSaved(event.position)
            setDownload(event.position)
        } else if (!event.success) {
            setLoading(event.position, event.max, event.current)
        }
    }

    private fun setLoading(position: Int, max: Int, current: Int) {
        val item = hentaiManga[position]
        item.saved = false
        item.downloading = true
        progresses.put(position, CustomArray(max, current))
        hentaiBookmarksBox.put(item)
        notifyDataSetChanged()
    }

    private fun setSaved(position: Int) {
        val item = hentaiManga[position]
        item.saved = true
        item.downloading = false
        if (progresses.get(position) != null)
            progresses.removeAt(position)
        hentaiBookmarksBox.put(item)
        notifyDataSetChanged()
    }

    private fun setDownload(position: Int) {
        val item = hentaiManga[position]
        item.saved = false
        item.downloading = false
        if (progresses.get(position) != null)
            progresses.removeAt(position)
        hentaiBookmarksBox.put(item)
        notifyDataSetChanged()
    }

    fun update(hentaiBookmarksBox: Box<HentaiManga>) {
        this.hentaiBookmarksBox = hentaiBookmarksBox
        hentaiBookmarksBox.init()
        notifyDataSetChanged()
    }

    fun getItem(i: Int): HentaiManga {
        return hentaiManga[i]
    }

    override fun getItemId(i: Int): Long {
        return hentaiManga[i].id
    }

    override fun onBindViewHolder(holder: MangaCellsViewHolder, position: Int) {
        val item = hentaiManga[position]
        holder.textView.text = item.title
        holder.imageView.adjustViewBounds = true



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
                //Log.d("lol", "saved pos: " + position);
                holder.hentaiSaved.visibility = View.VISIBLE
                holder.hentaiDown.visibility = View.GONE
                holder.hentaiDButton.visibility = View.GONE
                holder.hentaiSaved.setOnClickListener {
                    val path = Utils.hentaiPath + File.separator + item.id
                    val mangaDir = File(path)
                    if(mangaDir.exists())
                        mangaDir.deleteRecursively()
                    val infoFile = File(Utils.getHentaiInfoFile(item.id))
                    if(infoFile.exists())
                        infoFile.delete()
                    setDownload(position)
                }
            }
            item.downloading -> {
                holder.hentaiSaved.visibility = View.GONE
                holder.hentaiDown.visibility = View.VISIBLE
                if (progresses.get(position) != null) {
                    holder.hentaiDown.visibility = View.VISIBLE
                    val sets = progresses.get(position)
                    holder.hentaiDown.progress = sets.current.toFloat()
                    holder.hentaiDown.max = sets.max
                } else {
                    holder.hentaiDown.visibility = View.GONE
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
