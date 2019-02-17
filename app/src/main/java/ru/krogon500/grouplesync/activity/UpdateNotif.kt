package ru.krogon500.grouplesync.activity

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.manga_chapters.*
import ru.krogon500.grouplesync.R
import ru.krogon500.grouplesync.service.UpdateService.Companion.newChapters

class UpdateNotif : AppCompatActivity(){

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.manga_chapters)
        fab.hide()
        if (supportActionBar != null)
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        chaptersRefresh.isEnabled = false
        init()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            // Respond to the action bar's Up/Home button
            android.R.id.home -> onBackPressed()
        }
        return true
    }

    override fun onNewIntent(intent: Intent?) {
        init()
    }

    fun init(){
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, newChapters)
        chaptersList.adapter = adapter
        chaptersList.visibility = View.VISIBLE
    }
}
