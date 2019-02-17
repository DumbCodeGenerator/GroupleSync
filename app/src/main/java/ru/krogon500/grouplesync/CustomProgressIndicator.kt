package ru.krogon500.grouplesync

import android.view.LayoutInflater
import android.view.View
import com.github.lzyzsd.circleprogress.DonutProgress
import com.github.piasy.biv.indicator.ProgressIndicator
import com.github.piasy.biv.view.BigImageView
import java.util.*

class CustomProgressIndicator : ProgressIndicator {
    private var mDonutProgressView: DonutProgress? = null

    override fun getView(parent: BigImageView): View? {
        mDonutProgressView = LayoutInflater.from(parent.context)
                .inflate(R.layout.custom_indicator, parent, false) as DonutProgress
        return mDonutProgressView
    }

    override fun onStart() {

    }

    override fun onProgress(progress: Int) {
        if (progress < 0 || progress > 100 || mDonutProgressView == null) {
            return
        }
        mDonutProgressView!!.progress = progress.toFloat()
        mDonutProgressView!!.text = String.format(Locale.getDefault(), "%d%%", progress)
    }

    override fun onFinish() {

    }
}
