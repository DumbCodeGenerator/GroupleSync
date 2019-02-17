package ru.krogon500.grouplesync

import android.content.Context
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView

class RecyclerItemClickListener(context: Context, listener: OnItemClickListener): RecyclerView.OnItemTouchListener {
    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {

    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {

    }

    private var mListener: OnItemClickListener? = null
    var mGestureDetector: GestureDetector

    interface OnItemClickListener {
        fun onItemClick(adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>, position: Int)
    }

    init
    {
        mListener = listener
        mGestureDetector = GestureDetector (context, object: SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent?): Boolean {
                return true
            }
        })
    }

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        val childView = rv.findChildViewUnder (e.x, e.y) ?: return false
        if (rv.adapter != null && mListener != null && mGestureDetector.onTouchEvent(e)) {
            mListener!!.onItemClick(rv.adapter!!, rv.getChildLayoutPosition(childView))
            return true
        }
        return false
    }
}