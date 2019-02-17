package ru.krogon500.grouplesync.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.AsyncTask
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat.Builder
import androidx.core.app.NotificationManagerCompat
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.jsoup.Jsoup
import ru.krogon500.grouplesync.App
import ru.krogon500.grouplesync.R
import ru.krogon500.grouplesync.Utils
import ru.krogon500.grouplesync.Utils.getVolAndChapter
import ru.krogon500.grouplesync.entity.GroupleChapter
import ru.krogon500.grouplesync.entity.HentaiManga
import ru.krogon500.grouplesync.event.DownloadEvent
import ru.krogon500.grouplesync.event.UpdateEvent
import se.ajgarn.mpeventbus.MPEventBus
import java.io.File
import java.io.IOException
import java.util.regex.Pattern



class DownloadService : Service() {
    private lateinit var nm: NotificationManagerCompat
    private lateinit var gChaptersBox: Box<GroupleChapter>
    private lateinit var hentaiBox: Box<HentaiManga>
    private lateinit var mBuilder: Builder
    private val ACTION_CANCEL = "ru.krogon500.grouplesync.notifications.action_cancel"
    private var runningTask : Download? = null
    private var cancelled = false
    private val id = 1358

    @Subscribe
    fun onDownloadEvent(event: DownloadEvent) {
        //Log.d("lol", "kochaet");
        addTaskAndExecute(event.link, event.title, event.position, event.manga_id, event.original_id, event.type, event.user, event.pass, event.bId)
    }

    override fun onCreate() {
        super.onCreate()
        nm = NotificationManagerCompat.from(this)
        mBuilder = Utils.getNotificationBuilder(this,
                "ru.krogon500.GroupleSync.notification_expand.DOWNLOAD_FOREGROUND", // Channel id
                NotificationManagerCompat.IMPORTANCE_DEFAULT).setOnlyAlertOnce(true)
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //Log.d("lol", flags.toString());
        if(intent != null) {
            if (intent.action == ACTION_CANCEL){
                cancelled = true
                if(runningTask != null)
                    runningTask!!.cancel(true)
            }
            val args = intent.extras ?: return Service.START_NOT_STICKY
            gChaptersBox = (application as App).boxStore.boxFor()
            hentaiBox = (application as App).boxStore.boxFor()
            val de = args.getSerializable("info") as DownloadEvent
            addTaskAndExecute(de.link, de.title, de.position, de.manga_id, de.original_id, de.type, de.user, de.pass, de.bId)
        }
        return Service.START_NOT_STICKY
    }

    @SuppressLint("RestrictedApi")
    private fun checkIfCanExecute(success: Boolean = false, cancelled: Boolean = false, msg: String? = null) {
        Log.d("lol", "step 1.1: ${DownloadService.queue.size}/$isTaskRunning/$cancelled")
        if (DownloadService.queue.size > 0 && !isTaskRunning && !cancelled) {
            val deleteIntent = Intent(this, DownloadService::class.java)
            deleteIntent.action = ACTION_CANCEL
            val deletePendingIntent = PendingIntent.getService(this, 0, deleteIntent, 0)

            val remoteView = RemoteViews(packageName, R.layout.notification)
            remoteView.setTextViewText(R.id.notif_title, DownloadService.titles[0])
            remoteView.setProgressBar(R.id.notif_progress, 0, 0, true)

            val expandRemoteView = RemoteViews(packageName, R.layout.notification_expand)
            expandRemoteView.setTextViewText(R.id.notif_title, DownloadService.titles[0])
            expandRemoteView.setTextViewText(R.id.notif_content, msg)
            expandRemoteView.setProgressBar(R.id.notif_progress, 0, 0, true)
            expandRemoteView.setOnClickPendingIntent(R.id.notif_cancel, deletePendingIntent)

            mBuilder.setContentTitle(DownloadService.titles[0])
                    //.setStyle(NotificationCompat.DecoratedCustomViewStyle())
                    //.setProgress(0, 0, true)
                    .setContentText(null)
                    .setCustomBigContentView(expandRemoteView)
                    .setCustomContentView(remoteView)
                    //.addAction(android.R.drawable.ic_delete, "Отмена", deletePendingIntent)
                    //.setVibrate(null)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
            startForeground(id, mBuilder.build())
            nm.notify(id, mBuilder.build())
            runningTask = DownloadService.queue[0]
            runningTask!!.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "")
        } else if (DownloadService.queue.size == 0 || cancelled) {
            val message: String = if (success && !cancelled) "Скачивание завершено" else if(cancelled) "Скачивание отменено" else "Скачивание не удалось"
            //nm.cancel(id)
            mBuilder.bigContentView.setTextViewText(R.id.notif_content, message)
            mBuilder.bigContentView.setViewVisibility(R.id.notif_cancel, View.GONE)
            mBuilder.bigContentView.setViewVisibility(R.id.notif_progress, View.GONE)

            mBuilder.contentView.setViewVisibility(R.id.notif_progress, View.GONE)
            mBuilder.contentView.setViewVisibility(R.id.notif_content, View.VISIBLE)
            mBuilder.contentView.setTextViewText(R.id.notif_content, message)
            mBuilder.setSmallIcon(android.R.drawable.stat_sys_download_done).setOngoing(false).setAutoCancel(true)
            stopForeground(true)
            nm.notify(id, mBuilder.build())

            stopSelf()
        }
    }

    private fun addTaskAndExecute(link: String, title: String, position: Int, manga_id: Long, original_id: Long?, type: Byte, user: String?, pass: String?, bId: String) {
        val split = link.split("/")
        var msg: String? = null
        DownloadService.titles.add(title)
        Log.d("lol", "link: $link")
        val path: String
        if (type == Utils.GROUPLE) {
            path = (Utils.grouplePath + File.separator + bId/*title.replaceAll("[\\>\\<\\|\\*\\:\\?\\^\\\\\\/\\'\\\"]", "")*/
                    + File.separator + split[split.size - 2] + File.separator + split[split.size - 1])
            msg = "Глава " + split[split.size - 2].replace("vol", "") + " – " + split[split.size - 1]
        } else {
            path = Utils.hentaiPath + File.separator + manga_id
        }

        DownloadService.queue.add(Download(link, path, msg, position, manga_id, original_id, type, user, pass, bId))
        checkIfCanExecute(msg = msg)
    }


    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        if (EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    @SuppressLint("StaticFieldLeak")
    private inner class Download internal constructor(private val link: String, private val path: String, private val msg: String?, private val position: Int,
                                                      private val manga_id: Long = 0, private val original_id: Long?, private val type: Byte,
                                                      private val user: String?, private val pass: String?, private var bId: String) : AsyncTask<String, Int, Boolean>() {
        var files = ArrayList<String>()
        var vol: Int? = null
        var chap: Int? = null
        var page_all: Int = 0
        var groupleManga: GroupleChapter? = null
        var hentaiManga: HentaiManga? = null

        init {

        }

        override fun onPreExecute() {
            super.onPreExecute()
            isTaskRunning = true

            val downloadDir = File(path)

            if (!downloadDir.exists()) {
                if (downloadDir.mkdirs()) {
                    Log.i("lol", "norm")
                } else {
                    cancel(true)
                }
            }
            try {
                File("$path/.nomedia").createNewFile()
            } catch (e: IOException) {
                Log.e("GroupleSync", e.localizedMessage)
                e.printStackTrace(Utils.getPrintWriter())
            }
        }

        override fun doInBackground(vararg params: String): Boolean? {
            //String img;
            return if (type == Utils.GROUPLE) {
                groupleManga = gChaptersBox[manga_id]
                groupleType(link)
            }else {
                hentaiManga = hentaiBox[manga_id]
                hentaiType(link, user!!, pass!!)
            }
        }

        private fun groupleType(link: String): Boolean {
            try {
                val volAndChap = link.getVolAndChapter()
                vol = volAndChap[0]
                chap = volAndChap[1]
                val mainPage = Jsoup.connect(link).data("mtr", "1").get()
                val script = mainPage.selectFirst("script:containsData(rm_h.init)")
                var counter = 1
                val content = script.html()
                val rows = content.split("\\r?\\n".toRegex())
                var needed = rows[rows.size - 1]
                needed = needed.substring(needed.indexOf('[') + 1, needed.lastIndexOf(']'))
                val parts = needed.split("],")
                val max = parts.size
                page_all = max
                for (part in parts) {
                    if(isCancelled)
                        return false
                    val imageLink = part.replace("[\\['\"\\]]".toRegex(), "").split(",")
                    val ext = imageLink[2].split("\\?".toRegex())[0]
                    val img = imageLink[1] + imageLink[0] + ext

                    writeToFile(img)

                    publishProgress(counter, max)
                    counter++
                }
            } catch (e: Exception) {
                Log.e("lol", e.localizedMessage)
                return false
            }

            return true
        }

        private fun hentaiType(link: String, user: String, pass: String): Boolean {
            if (!Utils.login(Utils.HENTAI, user, pass))
                return false
            try {
                //Log.d("lol", "workate 667");
                val mainPage = Utils.getPage(Utils.HENTAI, user, pass, link)
                //Log.d("lol", "link: " + link);
                val script = mainPage.selectFirst("script:containsData(fullimg)")
                var counter = 1
                val pattern = Pattern.compile("\"fullimg\":.+")
                val matcher = pattern.matcher(script.html())
                if (matcher.find()) {
                    val needed = matcher.group(0).replace("\"fullimg\":", "")
                    //Log.d("lol", "needed: " + needed);
                    val pattern2 = Pattern.compile("([\"])(\\\\?.)*?\\1")
                    val matcher2 = pattern2.matcher(needed)
                    //Log.d("lol", "matcher test: " + matcher2.);
                    var max = 0
                    while (matcher2.find())
                        max++

                    page_all = max

                    matcher2.reset()

                    while (matcher2.find()) {
                        if(isCancelled)
                            return false
                        writeToFile(matcher2.group(0).replace("\"", ""))
                        publishProgress(counter, max)
                        counter++
                    }
                }
            } catch (e: Exception) {
                Log.e("GroupleSync", e.localizedMessage)
                e.printStackTrace(Utils.getPrintWriter())
                return false
            }

            return true
        }

        @Throws(Exception::class)
        private fun writeToFile(link: String) {
            val split = link.split("/")
            //val fileSize = image.size.toLong()
            val localImg = File(path + File.separator + split[split.size - 1])
            files.add(localImg.absolutePath)
            if (localImg.exists())
                return
            val image = Jsoup.connect(link).ignoreContentType(true).execute().bodyAsBytes()
            localImg.writeBytes(image)
        }


        @SuppressLint("RestrictedApi")
        override fun onProgressUpdate(vararg values: Int?) {
            //mBuilder.setProgress(values[1]!!, values[0]!!, false)
            val remoteView = mBuilder.bigContentView
            remoteView.setProgressBar(R.id.notif_progress, values[1]!!, values[0]!!, false)
            mBuilder.contentView.setProgressBar(R.id.notif_progress, values[1]!!, values[0]!!, false)
            remoteView.setTextViewText(R.id.notif_content, "$msg ${values[0]}/${values[1]}")
            //mBuilder.setStyle(NotificationCompat.BigTextStyle().bigText(msg + " " + values[0] + "/" + values[1]))

            MPEventBus.getDefault().postToAll(UpdateEvent(values[0]!!, values[1]!!, position, false, false, link, original_id, type))
            nm.notify(id, mBuilder.build())
        }

        override fun onPostExecute(success: Boolean?) {
            if (success!!) {
                if (type == Utils.GROUPLE) {
                    groupleManga?.saved = true
                    groupleManga?.page_all = page_all
                    if(groupleManga != null)
                        gChaptersBox.put(groupleManga!!)
                    files.reverse()
                    Utils.saveListFile(files, "grouple/$bId", "$vol.$chap.dat")
                } else {
                    files.reverse()
                    Utils.saveListFile(files, "hentai", "$manga_id.dat")
                }
            }
            MPEventBus.getDefault().postToAll(UpdateEvent(0, page_all, position, true, success, link, original_id, type))
            isTaskRunning = false
            DownloadService.queue.removeAt(0)
            DownloadService.titles.removeAt(0)

            checkIfCanExecute(success)
        }

        override fun onCancelled() {
            File(path).deleteRecursively()
            files.clear()
            MPEventBus.getDefault().postToAll(UpdateEvent(0, 0, position, true, false, null, original_id, type))
            isTaskRunning = false
            DownloadService.queue.clear()
            DownloadService.titles.clear()
            groupleManga?.saved = false
            groupleManga?.downloading = false

            if(groupleManga != null)
                gChaptersBox.put(groupleManga!!)

            checkIfCanExecute(cancelled = true)
            runningTask = null
            super.onCancelled()
        }
    }

    companion object {
        private val queue = ArrayList<Download>()
        private val titles = ArrayList<String>()
        private var isTaskRunning: Boolean = false
    }
}