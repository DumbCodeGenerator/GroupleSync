package ru.krogon500.grouplesync.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.objectbox.Box
import io.objectbox.kotlin.query
import ru.krogon500.grouplesync.R
import ru.krogon500.grouplesync.entity.GroupleBookmark
import ru.krogon500.grouplesync.holder.MangaCellsViewHolder
import java.text.Collator
import java.util.*

class GroupleAdapter(private val ctx: Context, private var groupleBookmarksBox: Box<GroupleBookmark>) : RecyclerView.Adapter<MangaCellsViewHolder>() {

    private lateinit var groupleBookmarks: ArrayList<GroupleBookmark>

    init {
        groupleBookmarksBox.init()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MangaCellsViewHolder {
        val itemView = LayoutInflater.from(parent.context)
                .inflate(R.layout.cellgrid, parent, false)
        return MangaCellsViewHolder(itemView)
    }

    override fun getItemCount(): Int {
        return groupleBookmarksBox.count().toInt()
    }

    override fun onBindViewHolder(holder: MangaCellsViewHolder, position: Int) {
        val item = groupleBookmarks[position]

        holder.textView.text = item.title

        if (item.cover != null)
            holder.imageView.setImageBitmap(item.cover)
        else
            holder.imageView.setImageResource(android.R.color.darker_gray)

        holder.hentaiDown.visibility = View.GONE
        holder.hentaiDButton.visibility = View.GONE
        holder.hentaiSaved.visibility = View.GONE
        if (item.isNew)
            holder.newSign.visibility = View.VISIBLE
        else
            holder.newSign.visibility = View.GONE
    }


    fun Box<GroupleBookmark>.init(){
        groupleBookmarks = this.query { sort { o1, o2 ->
            val ruCollator = Collator.getInstance(java.util.Locale("ru", "RU"))
            ruCollator.strength = Collator.PRIMARY
            ruCollator.compare(o1.title, o2.title) } }.find() as ArrayList<GroupleBookmark>

        groupleBookmarks.forEach {
            if(it.cover == null)
                it.setCover(this@GroupleAdapter)
        }
    }

    fun remove(pos: Int) {
        groupleBookmarksBox.remove(groupleBookmarks[pos])
        notifyDataSetChanged()
    }

    fun update(groupleBookmarksBox: Box<GroupleBookmark>) {
        this.groupleBookmarksBox = groupleBookmarksBox
        groupleBookmarksBox.init()
        notifyDataSetChanged()
    }

    fun getItem(position: Int): GroupleBookmark{
        return groupleBookmarks[position]
    }

    override fun getItemId(i: Int): Long {
        return groupleBookmarks[i].id
    }

}
