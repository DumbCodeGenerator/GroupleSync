package ru.krogon500.grouplesync

import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import com.nostra13.universalimageloader.core.ImageLoader
import ru.krogon500.grouplesync.interfaces.ICoverSettable

class RecyclerArray<T:ICoverSettable>(private val adapter: RecyclerView.Adapter<*>, private val imageLoader: ImageLoader?): ArrayList<T>() {
    override fun add(index: Int, element: T) {
        super.add(index, element)
        adapter.notifyItemInserted(index)
        if(imageLoader != null) element.setCover(imageLoader, adapter, index)
    }
    override fun addAll(elements: Collection<T>): Boolean {
        val startIndex = size
        val result = super.addAll(elements)
        adapter.notifyItemRangeInserted(startIndex, elements.size)
        if(imageLoader != null) elements.forEachIndexed { index, t ->  t.setCover(imageLoader, adapter, startIndex + index) }
        return result
    }
    override fun removeAll(elements: Collection<T>): Boolean {
        elements.forEach { adapter.notifyItemRemoved(this.indexOf(it)) }
        return super.removeAll(elements)
    }
    override fun removeAt(index: Int): T {
        adapter.notifyItemRemoved(index)
        return super.removeAt(index)
    }
    fun swap(data: Collection<T>){
        val itemCount = size
        super.clear()
        adapter.notifyItemRangeRemoved(0, itemCount)
        Log.d("lol", "removed $itemCount items, adding ${data.size} items")
        addAll(data)
    }
}