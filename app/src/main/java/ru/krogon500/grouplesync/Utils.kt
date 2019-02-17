package ru.krogon500.grouplesync

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.TargetApi
import android.app.Activity
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.text.Html
import android.text.Spanned
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.ListView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.main_act2.*
import org.jsoup.Connection.Method
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.*
import java.net.URL
import java.text.DateFormat
import java.util.*
import kotlin.collections.HashMap

object Utils {
    const val HENTAI: Byte = 0
    const val GROUPLE: Byte = 1
    var groupleBase = "https://grouple.co"
    var cachePath = Environment.getExternalStorageDirectory().absolutePath + File.separator + "GroupleSync"
    val dbDir = File("$cachePath/db")

    var grouplePath = Environment.getExternalStorageDirectory().absolutePath + File.separator + "GroupleSync" + File.separator + "manga"
    var groupleAdd = "/internal/ajax/addBookmark"
    var groupleDelete = "$groupleBase/private/change"
    private const val groupleError = "https://grouple.co/internal/auth/login?login_error=1"

    var hentaiPath = Environment.getExternalStorageDirectory().absolutePath + File.separator + "GroupleSync" + File.separator + "hentai"
    var hentaiBase = "http://henchan.me"
    private var imgHentaiBase = "http://img2.henchan.me"

    private var allCookies: HashMap<String, Map<String, String>> = HashMap()

    fun login(type: Byte, user: String, pass: String): Boolean {
        val login_data = HashMap<String, String>()
        when (type) {
            GROUPLE -> {
                login_data["username"] = user
                login_data["password"] = pass
                login_data["remember_me"] = "on"
            }
            HENTAI -> {
                login_data["login"] = "submit"
                login_data["login_name"] = user
                login_data["login_password"] = pass
            }
        }
        return try {
            val result = Jsoup.connect(if (type == GROUPLE) "$groupleBase/login/authenticate" else hentaiBase).data(login_data).method(Method.POST).execute()
            val cock = result.cookies()
            val url = result.url().toString()
            if (url != groupleError && !cock.containsKey("dle_user_id") || cock.containsKey("dle_user_id") && cock["dle_user_id"] != "deleted") {
                allCookies[result.url().host] = cock
                true
            } else
                false
        } catch (e: IOException) {
            Log.e("GroupleSync", e.localizedMessage)
            false
        }

    }

    @Throws(Exception::class)
    fun getPage(type: Byte?, user: String, pass: String, targetUri: String): Document {
        val u = URL(targetUri)
        val host = u.host

        if (!allCookies.containsKey(host)) {
            val login_data = HashMap<String, String>()
            if (type == GROUPLE) {
                login_data["username"] = user
                login_data["password"] = pass
                login_data["remember_me"] = "on"
                login_data["targetUri"] = targetUri
                val result = Jsoup.connect("$groupleBase/login/authenticate").followRedirects(true).data(login_data).method(Method.POST).execute().bufferUp()
                if (result.url().toString() == groupleError)
                    throw Exception("Не удалось войти!")
                else {
                    allCookies[host] = result.cookies()
                    return result.parse()
                }
            } else if (type == HENTAI) {
                login_data["login"] = "submit"
                login_data["login_name"] = user
                login_data["login_password"] = pass
                login_data["development_access"] = "true"
                val result = Jsoup.connect(targetUri).followRedirects(true).data(login_data).method(Method.POST).execute().bufferUp()
                val cookies = result.cookies()
                if (cookies["dle_user_id"] == "deleted") {
                    throw Exception("Не удалось войти!")
                } else {
                    allCookies[host] = cookies
                    return result.parse()
                }
            } else
                throw Exception("Неверный тип!")
        } else {
            Log.d("lol", targetUri)
            val doc = Jsoup.connect(targetUri).cookies(allCookies[host]).data("development_access", "true").get()
            return if (type == GROUPLE && doc.selectFirst("a.strong").text() != user || type == HENTAI && (doc.selectFirst("a.bordr") != null && doc.selectFirst("a.bordr").text() != "Выйти" || doc.text().contains("Доступ ограничен"))) {
                Log.d("lol", "ne workaet")
                allCookies.remove(host)
                getPage(type, user, pass, targetUri)
            } else {
                Log.d("lol", "workaet return")
                doc
            }
        }
    }

    @Throws(Exception::class)
    fun makeRequest(type: Byte?, user: String, pass: String, targetUri: String, data: HashMap<String, String>, method: Method): Boolean {
        val u = URL(targetUri)
        val host = u.host


        //Log.d("lol", host);
        if (!allCookies.containsKey(host)) {
            val login_data = HashMap<String, String>()
            if (type == GROUPLE) {
                login_data["username"] = user
                login_data["password"] = pass
                login_data["remember_me"] = "on"
                login_data["targetUri"] = targetUri
                val result = Jsoup.connect("$groupleBase/login/authenticate").followRedirects(true).data(login_data).method(method).execute()
                if (result.url().toString() == groupleError)
                    throw Exception("Не удалось войти!")
                else {
                    allCookies[host] = result.cookies()
                    val result2 = Jsoup.connect(targetUri).method(method).cookies(result.cookies()).data(data).execute()
                    val resultString = result2.parse().toString()
                    return resultString.contains("success") || resultString.contains("ok")
                }
            } else if (type == HENTAI) {
                login_data["login"] = "submit"
                login_data["login_name"] = user
                login_data["login_password"] = pass
                val result = Jsoup.connect(targetUri).data(login_data).method(method).execute()
                if (result.parse().toString().contains("Внимание, обнаружена ошибка"))
                    throw Exception("Не удалось войти!")
                else {
                    allCookies[host] = result.cookies()
                    return makeRequest(type, user, pass, targetUri, data, method)
                }
            } else
                throw Exception("Неверный тип!")
        } else {
            val result1 = Jsoup.connect(targetUri).method(method).followRedirects(true).cookies(allCookies[host]).data(data).execute()
            val result1String = result1.parse().html()

            return if (!result1String.contains("success") && !result1String.contains("ok") || result1String.contains("Внимание, обнаружена ошибка")) {
                Log.d("lol", "false")
                allCookies.remove(host)
                makeRequest(type, user, pass, targetUri, data, method)
            } else {
                Log.d("lol", "true")
                true
            }
        }
    }

    @Suppress("Deprecation")
    fun getNotificationBuilder(context: Context, channelId: String, importance: Int): NotificationCompat.Builder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            prepareChannel(context, channelId, importance)
            NotificationCompat.Builder(context, channelId)
        } else {
            NotificationCompat.Builder(context)
        }
    }

    @TargetApi(26)
    private fun prepareChannel(context: Context, id: String, importance: Int) {
        val appName = context.getString(R.string.app_name)
        val description = "Downloads"
        val nm = context.getSystemService(Activity.NOTIFICATION_SERVICE) as NotificationManager

        var nChannel: NotificationChannel? = nm.getNotificationChannel(id)

        if (nChannel == null) {
            nChannel = NotificationChannel(id, appName, importance)
            nChannel.description = description
            nm.createNotificationChannel(nChannel)
        }
    }

    /*@Throws(IOException::class)
    internal fun saveCookies(path: String) {
        val dir = File(path)
        if(!dir.exists())
            dir.mkdirs()

        val file = File("$path/cookies.dat")
        Log.i("lol", file.absolutePath)
        val fileOutputStream = FileOutputStream(file)
        val objectOutputStream = ObjectOutputStream(fileOutputStream)

        objectOutputStream.writeObject(allCookies)
        objectOutputStream.close()
        fileOutputStream.close()
        Log.i("lol", "Размер файла с куками: " + file.length())
    }

    @Throws(Exception::class)
    internal fun readCookies(path: String) {
        val file = File("$path/cookies.dat")
        if (!file.exists()) {
            allCookies = HashMap()
            Log.i("lol", "Файла с куками нет")
            return
        }
        val fileInputStream = FileInputStream(file)
        val objectInputStream = ObjectInputStream(fileInputStream)

        @Suppress("UNCHECKED_CAST")
        allCookies = objectInputStream.readObject() as HashMap<String, Map<String, String>>

        objectInputStream.close()
        fileInputStream.close()
    }*/

    fun getPrintWriter(): PrintWriter{
        val dir = File(cachePath, "Crash_Reports")
        if(!dir.exists())
            dir.mkdirs()

        val filename = DateFormat.getDateTimeInstance().format(Date()) + ".stacktrace"
        return PrintWriter(File(dir, filename))
    }

    @Suppress("Deprecation")
    fun String.getSpannedFromHtml(): Spanned {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY)
        } else {
            Html.fromHtml(this)
        }
    }

    /*@Suppress("Deprecation")
    fun Context?.isConnected(): Boolean {
        val cm = this?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {

            val activeNetworks = cm.allNetworks
            for (n in activeNetworks) {
                val nInfo = cm.getNetworkInfo(n)
                if (nInfo.isConnected)
                    return true
            }

        } else {
            val info = cm.allNetworkInfo
            if (info != null)
                for (anInfo in info)
                    if (anInfo.state == NetworkInfo.State.CONNECTED) {
                        return true
                    }
        }

        return false

    }*/

    fun String.getVolAndChapter(): IntArray {
        val split = this.split("/")
        val vol = Integer.parseInt(split[split.size - 2].replace("vol", ""))
        val chapter = Integer.parseInt(split[split.size - 1])
        return intArrayOf(vol, chapter)
    }

    fun ListView.getViewByPosition(pos: Int): View {
        val firstListItemPosition = this.firstVisiblePosition
        val lastListItemPosition = firstListItemPosition + this.childCount - 1

        return if (pos < firstListItemPosition || pos > lastListItemPosition) {
            this.adapter.getView(pos, null, this)
        } else {
            val childIndex = pos - firstListItemPosition
            this.getChildAt(childIndex)
        }
    }

    fun Int.getDPI(context: Context): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), context.resources.displayMetrics).toInt()

    @Suppress("DEPRECATION")
    internal fun Context.isMyServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = this.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    fun getSavedListFile(filename: String): ArrayList<String>? {
        val savedArrayList: ArrayList<String>?
        val file = File(filename)
        if (!file.exists()) {
            Log.i("lol", "Файла с инфой нет")
            return null
        }

        val fileInputStream = FileInputStream(file)
        val objectInputStream = ObjectInputStream(fileInputStream)

        @Suppress("UNCHECKED_CAST")
        savedArrayList = objectInputStream.readObject() as ArrayList<String>

        objectInputStream.close()
        fileInputStream.close()

        return savedArrayList
    }

    fun saveListFile(files : ArrayList<String>, filepath: String, filename: String){
        val dir = File("$cachePath/info/$filepath")
        if(!dir.exists())
            dir.mkdirs()

        val file = File(dir.absolutePath + File.separator + filename)
        Log.i("lol", file.absolutePath)
        val fileOutputStream = FileOutputStream(file)
        val objectOutputStream = ObjectOutputStream(fileOutputStream)

        objectOutputStream.writeObject(files)
        objectOutputStream.close()
        fileOutputStream.close()
        Log.i("lol", "Размер файла с инфо: " + file.length())
    }

    fun getHentaiInfoFile(id: Long) : String{
        return cachePath + File.separator + "info/hentai" + File.separator + id + ".dat"
    }

    fun String.getHQThumbnail(): String {
        return when {
            this.contains("manga_thumbs") -> this.replace(hentaiBase, imgHentaiBase).replace("manga_thumbs", "manganew_webp").replace(".jpg|.png|.gif|.jpeg".toRegex(), ".webp")
            else -> this.replace("manganew_thumbs", "manganew_webp").replace(".jpg|.png|.gif|.jpeg".toRegex(), ".webp")
        }
    }

    fun View?.hideView(listener: AnimatorListenerAdapter? = null){
        this ?: return
        val layoutParams = this.layoutParams as CoordinatorLayout.LayoutParams
        val fab_bottomMargin = layoutParams.bottomMargin
        this.animate().translationY((this.height + fab_bottomMargin).toFloat()).setInterpolator(LinearInterpolator()).setListener(listener).setDuration(150).start()
    }

    fun toggleKeyboard(context: Context?) {
        val imm = context?.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(0,0)
        //imm.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    fun calculateNoOfColumns(context: Context?): Int {
        val displayMetrics = context?.resources?.displayMetrics ?: return 1
        val dpWidth = displayMetrics.widthPixels / displayMetrics.density
        return (dpWidth / 150).toInt()
    }

    fun fragmentFabListener(activity: Activity?): RecyclerView.OnScrollListener {
        return object: RecyclerView.OnScrollListener() {
            var isAnimated = false
            val listener = object: AnimatorListenerAdapter(){
                override fun onAnimationStart(animation: Animator?) {
                    isAnimated = true
                }
                override fun onAnimationEnd(animation: Animator?) {
                    isAnimated = false
                }
            }
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {

                val layoutManager = recyclerView.layoutManager as? GridLayoutManager ?: return
                val fab = activity?.frag_fab ?: return
                if(layoutManager.findFirstCompletelyVisibleItemPosition() == 0 && fab.translationY == 0f && !isAnimated){
                    fab.hideView(listener)
                }else if(layoutManager.findFirstCompletelyVisibleItemPosition() > 0 && fab.translationY > 0 &&!isAnimated){
                    fab.animate().translationY(0f).setInterpolator(LinearInterpolator()).setDuration(150).setListener(listener).start()
                }
            }
        }
    }
}
