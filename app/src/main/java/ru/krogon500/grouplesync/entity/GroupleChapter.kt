package ru.krogon500.grouplesync.entity

import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToOne

@Entity data class GroupleChapter(@Id var id: Long = 0,
                                  var title: String,
                                  var link: String,
                                  var order: Int,
                                  var vol: Int,
                                  var chap: Int ,
                                  var page: Int = 0,
                                  var page_all: Int = 0,
                                  var saved: Boolean = false,
                                  var downloading: Boolean = false,
                                  var readed: Boolean = false,
                                  @Convert(converter = ArrayToObjectBox::class, dbType = String::class)
                                  var files: List<String>? = null) {
    lateinit var bookmark: ToOne<GroupleBookmark>
}