package ru.krogon500.grouplesync.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.os.AsyncTask
import android.os.IBinder
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import org.jsoup.Connection
import org.jsoup.Jsoup
import ru.krogon500.grouplesync.App
import ru.krogon500.grouplesync.Utils
import ru.krogon500.grouplesync.Utils.getVolAndChapter
import ru.krogon500.grouplesync.entity.GroupleBookmark
import ru.krogon500.grouplesync.entity.GroupleChapter
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.util.*


class SyncService : Service() {
    private lateinit var nm: NotificationManagerCompat
    private lateinit var mBuilder: NotificationCompat.Builder
    private val id = 1366
    private var counter: Int = 0
    private var max: Int = 0
    private var current: Int = 0
    private lateinit var mSettings: SharedPreferences
    private lateinit var gBookmarksBox: Box<GroupleBookmark>
    private lateinit var gBookmarks: List<GroupleBookmark>

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        nm = NotificationManagerCompat.from(this)
        mBuilder = Utils.getNotificationBuilder(this,
                "ru.krogon500.GroupleSync.notification_expand.SYNC_FOREGROUND", // Channel id
                NotificationManagerCompat.IMPORTANCE_DEFAULT)
        mBuilder.setContentTitle("Синхронизация...")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOnlyAlertOnce(true)
                .setProgress(0, 0, true)
        startForeground(id, mBuilder.build())
        nm.notify(id, mBuilder.build())
        gBookmarksBox = (application as App).boxStore.boxFor()
        gBookmarks = gBookmarksBox.all
        mSettings = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        max = gBookmarks.size

        mBuilder.setProgress(max, current, false)
        nm.notify(id, mBuilder.build())
        Log.i("lol", "count: $max")
        gBookmarks.forEach {
            val readedCur = it.readedLink
            val readed_page = it.page
            val readed_vol_chap = readedCur.getVolAndChapter()

            val chapters = it.chapters
            if(chapters.isEmpty()) return@forEach
            chapters.sortByDescending { chapter -> chapter.order }
            val readed_chap: GroupleChapter
            try {
                readed_chap = chapters.first { chapter -> chapter.readed }
            }catch (e: NoSuchElementException){
                return@forEach
            }
            val readed = chapters.indexOf(readed_chap)
            val chapter = if(readed - 1 >= 0) chapters[readed - 1] else chapters[readed]
            val link = chapter.link
            val page = chapter.page
            val vol_chap = link.getVolAndChapter()

            if (vol_chap[0] > readed_vol_chap[0] || vol_chap[0] == readed_vol_chap[0] && vol_chap[1] > readed_vol_chap[1] || vol_chap[0] == readed_vol_chap[0] && vol_chap[1] == readed_vol_chap[1] && page > readed_page) {
                counter++
                it.readedLink = link
                it.page = page
                gBookmarksBox.put(it)
                AddBookmarkTask(link, page).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
            }
        }
        if (counter == 0) {
            stopService()
        }
    }

    internal fun stopService() {
        Toast.makeText(applicationContext, "Закладки обновлены", Toast.LENGTH_SHORT).show()
        stopForeground(true)
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return Service.START_NOT_STICKY
    }

    @SuppressLint("StaticFieldLeak")
    private inner class AddBookmarkTask internal constructor(private val link: String, private val page: Int) : AsyncTask<Void, Void, Boolean>() {
        private lateinit var host: String
        private var vol: Int = 0
        private var chapter: Int = 0

        init {
            try {
                val url = URL(link)
                host = String.format("%s://%s", url.protocol, url.host)
                val volandchap = link.getVolAndChapter()
                vol = volandchap[0]
                chapter = volandchap[1]
            } catch (e: MalformedURLException) {
                Toast.makeText(applicationContext, "Неверная ссылка", Toast.LENGTH_SHORT).show()
            }


        }

        override fun doInBackground(vararg voids: Void): Boolean? {
            val manga_id: Int
            try {
                val chapterPage = Jsoup.connect(link).data("mtr", "1").get()
                manga_id = Integer.parseInt(chapterPage.select("span.bookmark-menu").first().attr("data-id"))
            } catch (e: IOException) {
                Log.e("GroupleSync", e.localizedMessage)
                return false
            }

            val user = mSettings.getString("user", "")
            val pass = mSettings.getString("pass", "")
            val data = HashMap<String, String>()
            data["id"] = manga_id.toString()
            data["type"] = ""
            data["status"] = "WATCHING"
            data["vol"] = vol.toString()
            data["num"] = chapter.toString()
            data["page"] = page.toString()
            return try {
                Utils.makeRequest(Utils.GROUPLE, user!!, pass!!, host + Utils.groupleAdd, data, Connection.Method.POST)
            } catch (e: Exception) {
                Log.e("GroupleSync", e.localizedMessage)
                //Toast.makeText(getApplicationContext(), e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                false
            }

        }

        override fun onPostExecute(success: Boolean?) {
            counter--
            current++
            mBuilder.setProgress(max, current, false).setSmallIcon(android.R.drawable.stat_notify_sync)
            nm.notify(id, mBuilder.build())

            if (counter == 0) {
                stopService()
            } else if (!success!!) {
                Toast.makeText(applicationContext, "Не удалось обновить закладку", Toast.LENGTH_SHORT).show()
            }

        }
    }
}
