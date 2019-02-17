package ru.krogon500.grouplesync.entity

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToOne

@Entity data class GroupleChapter(@Id var id: Long = 0,
                             var title: String,
                             var link: String,
                             var date: Long,
                             var vol: Int,
                             var chap: Int,
                             var page: Int = 0,
                             var page_all: Int = 0,
                             var saved: Boolean = false,
                             var downloading: Boolean = false,
                             var readed: Boolean = false){
    lateinit var bookmark: ToOne<GroupleBookmark>
}