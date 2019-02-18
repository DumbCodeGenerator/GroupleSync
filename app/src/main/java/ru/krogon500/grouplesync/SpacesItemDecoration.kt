package ru.krogon500.grouplesync

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView


class SpacesItemDecoration(private val space: Int, var context: Context? = null): RecyclerView.ItemDecoration() {


    override fun getItemOffsets(outRect: Rect, view: View,
                                parent: RecyclerView, state: RecyclerView.State) {
        val layoutManager = parent.layoutManager ?: return
        if(layoutManager is GridLayoutManager) {
            val displayMetrics = context?.resources?.displayMetrics
            val displayWidth = displayMetrics?.widthPixels

            val params = view.layoutParams ?: return
            val spanCount = layoutManager.spanCount
            val last = layoutManager.itemCount
            var range = last % spanCount
            if (range == 0) range = spanCount

            params.width = displayWidth?.div(spanCount) ?: params.width
            if (parent.getChildLayoutPosition(view) in last - range until last) {
                outRect.bottom = 0
            } else {
                outRect.bottom = space
            }
        }else if(layoutManager is LinearLayoutManager){
            val last = layoutManager.itemCount - 1
            if(parent.getChildLayoutPosition(view) == last){
                outRect.bottom = 0
            }else{
                outRect.bottom = space
            }
        }
    }
}