package ru.krogon500.grouplesync.event

import java.io.Serializable

class DownloadEvent: Serializable {
    var link: String
    var title: String
    var manga_id: Long
    var original_id: Long? = null
    var user: String? = null
    var pass: String? = null
    var position: Int = 0
    var type: Byte = 0
    var bId: String = ""

    constructor(link: String, title: String, position: Int, manga_id: Long, original_id: Long?, type: Byte, user: String, pass: String) {
        this.link = link
        this.title = title
        this.position = position
        this.manga_id = manga_id
        this.type = type
        this.user = user
        this.pass = pass
        this.original_id = original_id
    }

    constructor(link: String, title: String, position: Int, manga_id: Long, type: Byte, bId: String) {
        this.link = link
        this.title = title
        this.position = position
        this.manga_id = manga_id
        this.type = type
        this.bId = bId
    }
}
