package ru.krogon500.grouplesync

import android.app.Application
import android.util.Log
import io.objectbox.BoxStore
import io.objectbox.exception.DbException
import ru.krogon500.grouplesync.entity.MyObjectBox
import se.ajgarn.mpeventbus.MPEventBus

class App : Application() {
    lateinit var boxStore: BoxStore

    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(CustomizedExceptionHandler(Utils.cachePath))
        try {
            boxStore = MyObjectBox.builder().androidContext(applicationContext).directory(Utils.dbDir).build()
            //Utils.readCookies(applicationContext.cacheDir.absolutePath)
        } catch (e: DbException){
            boxStore = MyObjectBox.builder().androidContext(applicationContext).build()
        } catch (e: Exception) {
            Log.e("GroupleSync", e.localizedMessage)
            e.printStackTrace(Utils.getPrintWriter())
        }
        MPEventBus.init(applicationContext)
    }
}
