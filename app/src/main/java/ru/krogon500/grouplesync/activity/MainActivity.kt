package ru.krogon500.grouplesync.activity

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.Menu
import android.view.MenuItem
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.tabs.TabLayout
import kotlinx.android.synthetic.main.fragment.*
import kotlinx.android.synthetic.main.main_act2.*
import ru.krogon500.grouplesync.App
import ru.krogon500.grouplesync.R
import ru.krogon500.grouplesync.Utils
import ru.krogon500.grouplesync.Utils.hideView
import ru.krogon500.grouplesync.Utils.isMyServiceRunning
import ru.krogon500.grouplesync.adapter.FragmentAdapter
import ru.krogon500.grouplesync.entity.MyObjectBox
import ru.krogon500.grouplesync.fragment.LoginFragment
import ru.krogon500.grouplesync.service.SyncService
import ru.krogon500.grouplesync.service.UpdateService

class MainActivity : AppCompatActivity() {
    private var mOptionsMenu: Menu? = null
    /*@BindView(R.id.view_pager)
    @JvmField
    var mViewPager: ViewPager? = null
    @BindView(R.id.tabs)
    @JvmField
    var tabs: TabLayout? = null*/
    //DbHelper groupleBookmarks, groupleChapters, hentaiFavorites;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_act2)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            // Permission is not granted
            // Should we show an explanation?
            // No explanation needed, we can request the permission.
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    MainActivity.MY_PERM_REQ_CODE)

            // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
            // app-defined int constant. The callback method gets the
            // result of the request.
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            start()
        else
            start()

    }

    private fun start() {
        val fragmentAdapter = FragmentAdapter(supportFragmentManager, PreferenceManager.getDefaultSharedPreferences(this))

        pager.adapter = fragmentAdapter
        tabs.setupWithViewPager(pager)
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            var isAnimated = false
            val listener = object: AnimatorListenerAdapter(){
                override fun onAnimationStart(animation: Animator?) {
                    isAnimated = true
                }
                override fun onAnimationEnd(animation: Animator?) {
                    isAnimated = false
                }
            }
            override fun onTabSelected(tab: TabLayout.Tab) {
                val item = fragmentAdapter.getItem(pager.currentItem)
                if(item !is LoginFragment){
                    val grid = item.mangaCells ?: return
                    val first = (grid.layoutManager as? GridLayoutManager ?: return).findFirstCompletelyVisibleItemPosition()
                    //Log.d("lol", "first in recycler: $first")
                    if(first == 0){
                        frag_fab.hideView(listener)
                        appBar.setExpanded(true)
                    }else{
                        frag_fab.animate().translationY(0f).setInterpolator(LinearInterpolator()).setDuration(150).setListener(listener).start()
                    }
                }
                if (mOptionsMenu != null) {
                    val hBrowser = mOptionsMenu!!.findItem(R.id.hBrowser)
                    val sync = mOptionsMenu!!.findItem(R.id.sync)
                    hBrowser.isVisible = tab.position != 0
                    sync.isVisible = tab.position != 1
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {

            }

            override fun onTabReselected(tab: TabLayout.Tab) {

            }
        })
        frag_fab.setOnClickListener {
            val item = fragmentAdapter.getItem(pager.currentItem)
            if(item !is LoginFragment){
                val grid = item.mangaCells ?: return@setOnClickListener
                grid.stopScroll()
                grid.layoutManager?.scrollToPosition(0)
                it.hideView()
                appBar.setExpanded(true, false)
            }
        }
    }



    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            MainActivity.MY_PERM_REQ_CODE -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    (application as App).boxStore.close()
                    (application as App).boxStore.deleteAllFiles()
                    (application as App).boxStore = MyObjectBox.builder().androidContext(applicationContext).directory(Utils.dbDir).build()

                    start()
                }else{
                    finish()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menu.findItem(R.id.update).isVisible = true
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        mOptionsMenu = menu
        val hBrowser = menu.findItem(R.id.hBrowser)
        val sync = menu.findItem(R.id.sync)
        hBrowser.isVisible = tabs.selectedTabPosition != 0
        sync.isVisible = tabs.selectedTabPosition != 1
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.hBrowser -> {
                val hBrowser = Intent(this, HentaiBrowser::class.java)
                startActivity(hBrowser)
                return true
            }
            R.id.sync -> {
                if (tabs!!.selectedTabPosition == 0 && !applicationContext.isMyServiceRunning(SyncService::class.java)) {
                    val service = Intent(applicationContext, SyncService::class.java)
                    startService(service)
                }
                return true
            }
            R.id.update -> {
                if (tabs!!.selectedTabPosition == 0 && !applicationContext.isMyServiceRunning(UpdateService::class.java)) {
                    val service = Intent(applicationContext, UpdateService::class.java)
                    startService(service)
                }
                return true
            }
            R.id.logout -> {
                val mSettings = PreferenceManager.getDefaultSharedPreferences(applicationContext)
                val adapter = pager.adapter as? FragmentAdapter ?: return true
                if (tabs!!.selectedTabPosition == 0) {
                    mSettings.edit().remove("remember").remove("user").remove("pass").apply()
                    adapter.goBack(Utils.GROUPLE)
                } else {
                    mSettings.edit().remove("remember_h").remove("user_h").remove("pass_h").apply()
                    adapter.goBack(Utils.HENTAI)
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private const val MY_PERM_REQ_CODE = 322
    }
}
