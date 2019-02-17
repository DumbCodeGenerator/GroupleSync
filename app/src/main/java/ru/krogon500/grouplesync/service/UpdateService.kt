package ru.krogon500.grouplesync.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.AsyncTask
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.objectbox.kotlin.boxFor
import org.jsoup.Jsoup
import ru.krogon500.grouplesync.App
import ru.krogon500.grouplesync.Utils
import ru.krogon500.grouplesync.activity.UpdateNotif
import ru.krogon500.grouplesync.entity.GroupleBookmark

class UpdateService: Service(){
    companion object {
        val newChapters = ArrayList<String>()
        private const val id = 1488
        lateinit var inboxStyle: NotificationCompat.InboxStyle
        lateinit var updateService: UpdateService
    }

    private lateinit var nm: NotificationManagerCompat
    private lateinit var mBuilder: NotificationCompat.Builder
    private lateinit var gBookmarks: List<GroupleBookmark>

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return Service.START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        updateService = this
        nm = NotificationManagerCompat.from(this)
        mBuilder = Utils.getNotificationBuilder(this,
                "ru.krogon500.GroupleSync.notification_expand.UPDATE_FOREGROUND", // Channel id
                NotificationManagerCompat.IMPORTANCE_DEFAULT)
        mBuilder.setContentTitle("Проверка новых глав...")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOnlyAlertOnce(true)
                .setProgress(0, 0, true)
        startForeground(id, mBuilder.build())
        nm.notify(id, mBuilder.build())
        gBookmarks = (application as App).boxStore.boxFor<GroupleBookmark>().all
        UpdateTask(gBookmarks, nm, mBuilder).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    class UpdateTask(private val gBookmarks: List<GroupleBookmark>, private val nm: NotificationManagerCompat, private val mBuilder: NotificationCompat.Builder): AsyncTask<Void, String, Boolean>(){

        override fun onPreExecute() {
            inboxStyle = NotificationCompat.InboxStyle()
            newChapters.clear()
        }

        override fun doInBackground(vararg params: Void?): Boolean {
            return try{
                gBookmarks.forEach {
                    val mangaPage = Jsoup.connect(it.link).get()
                    val table = mangaPage.selectFirst("table.table-hover > tbody")
                    val chapters = table.children()
                    val chapters_local = it.chapters
                    if(chapters_local.isNotEmpty() && chapters.size > chapters_local.size){
                        publishProgress(it.title)
                    }
                }
                true
            }catch (e: Exception){
                e.printStackTrace(Utils.getPrintWriter())
                false
            }
        }

        override fun onProgressUpdate(vararg values: String?) {
            if(values.isNotEmpty()){
                newChapters.add(values[0] ?: return)
                inboxStyle.addLine(values[0])
                mBuilder.setStyle(inboxStyle)
                nm.notify(id, mBuilder.build())
            }
        }

        override fun onPostExecute(result: Boolean) {
            var message = "Новых глав нет"
            if(result && newChapters.isNotEmpty()){
                message = "Есть новые главы"
            }else if(!result){
                message = "Не удалось обновить главы"
            }
            val intent = Intent(updateService.applicationContext, UpdateNotif::class.java)
            intent.putExtra("newChapters", newChapters)
            val pendingIntent = PendingIntent.getActivity(updateService.applicationContext, 0, intent, 0)
            mBuilder.setContentTitle(message)
                    .setContentIntent(pendingIntent)
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setProgress(0, 0, false)
            updateService.stopForeground(true)
            nm.notify(id, mBuilder.build())

            updateService.stopSelf()
        }

    }
}