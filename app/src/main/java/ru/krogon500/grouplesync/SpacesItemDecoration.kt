package ru.krogon500.grouplesync

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView


class SpacesItemDecoration(private val space: Int, var context: Context?): RecyclerView.ItemDecoration() {


    override fun getItemOffsets(outRect: Rect, view: View,
                                parent: RecyclerView, state: RecyclerView.State) {
        val displayMetrics = context?.resources?.displayMetrics
        val displayWidth = displayMetrics?.widthPixels

        val params = view.layoutParams ?: return
        val layoutManager = parent.layoutManager ?: return
        val spanCount = (layoutManager as GridLayoutManager).spanCount
        val last = layoutManager.itemCount
        var range = last % spanCount
        if(range == 0) range = spanCount

        params.width = displayWidth?.div(spanCount) ?: params.width
        if(parent.getChildLayoutPosition(view) in last - range until last){
            outRect.bottom = 0
        }else{
            outRect.bottom = space
        }
    }
}