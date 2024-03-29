package ru.krogon500.grouplesync.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.objectbox.Box
import io.objectbox.kotlin.query
import ru.krogon500.grouplesync.R
import ru.krogon500.grouplesync.RecyclerArray
import ru.krogon500.grouplesync.entity.GroupleBookmark
import ru.krogon500.grouplesync.fragment.GroupleFragment
import ru.krogon500.grouplesync.holder.MangaCellsViewHolder
import ru.krogon500.grouplesync.interfaces.OnItemClickListener
import java.text.Collator

class GroupleAdapter(private var itemClickListener: OnItemClickListener?, private val contextMenu: View.OnCreateContextMenuListener?) : RecyclerView.Adapter<MangaCellsViewHolder>() {
    private var groupleBookmarks = RecyclerArray<GroupleBookmark>(this, GroupleFragment.imageLoader)
    private var groupleBookmarksBox: Box<GroupleBookmark>? = null
    var selectedItem: Int? = null

    constructor(groupleBookmarksBox: Box<GroupleBookmark>, itemClickListener: OnItemClickListener?, contextMenu: View.OnCreateContextMenuListener?): this(itemClickListener, contextMenu){
        this.groupleBookmarksBox = groupleBookmarksBox
        groupleBookmarksBox.init()
    }

    constructor(data: Collection<GroupleBookmark>, itemClickListener: OnItemClickListener?, contextMenu: View.OnCreateContextMenuListener?): this(itemClickListener, contextMenu){
        groupleBookmarks.addAll(data, false)
    }

    fun Box<GroupleBookmark>.init(){
        val newBookmarks = this.query { sort { o1, o2 ->
            val ruCollator = Collator.getInstance(java.util.Locale("ru", "RU"))
            ruCollator.strength = Collator.PRIMARY
            ruCollator.compare(o1.title.removePrefix("*"), o2.title.removePrefix("*")) } }.find()
        if(groupleBookmarks.isEmpty()) {
            groupleBookmarks.addAll(newBookmarks, false)
        }else{
            val newIDs = ArrayList<Long>()
            newBookmarks.forEach { newIDs.add(it.id) }

            groupleBookmarks.removeAll(groupleBookmarks.filter { it.id !in newIDs })

            val oldIDs = ArrayList<Long>()
            groupleBookmarks.forEach { oldIDs.add(it.id) }

            newBookmarks.forEachIndexed { index, groupleBookmark ->
                if(groupleBookmark.id !in oldIDs)
                    groupleBookmarks.add(index, groupleBookmark)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MangaCellsViewHolder {
        val itemView = LayoutInflater.from(parent.context)
                .inflate(R.layout.cellgrid, parent, false)
        return MangaCellsViewHolder(itemView, itemClickListener, contextMenu)
    }

    override fun getItemCount(): Int {
        return groupleBookmarks.size
    }

    override fun onBindViewHolder(holder: MangaCellsViewHolder, position: Int) {
        val item = groupleBookmarks[position]

        holder.itemView.setOnLongClickListener {
            selectedItem = position
            false
        }
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


    fun remove(pos: Int) {
        groupleBookmarksBox?.remove(groupleBookmarks[pos])
        groupleBookmarks.removeAt(pos)
    }

    fun update(groupleBookmarksBox: Box<GroupleBookmark>) {
        this.groupleBookmarksBox = groupleBookmarksBox
        groupleBookmarksBox.init()
        //notifyDataSetChanged()
    }

    fun forceUpdate(groupleBookmarksBox: Box<GroupleBookmark>){
        this.groupleBookmarksBox = groupleBookmarksBox
        groupleBookmarks.clear()
        groupleBookmarksBox.init()
    }

    fun update(data: Collection<GroupleBookmark>){
        groupleBookmarks.swap(data)
        //notifyDataSetChanged()
    }

    fun getItem(position: Int): GroupleBookmark{
        return groupleBookmarks[position]
    }

    fun getItemPosById(id: Long): Int{
        return groupleBookmarks.indexOf(groupleBookmarks.find { it.id == id })
    }

    override fun getItemId(i: Int): Long {
        return groupleBookmarks[i].id
    }

}
