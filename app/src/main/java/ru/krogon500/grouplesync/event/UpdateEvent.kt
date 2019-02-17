package ru.krogon500.grouplesync.event

import java.io.Serializable

class UpdateEvent(var current: Int, var max: Int, var position: Int, var done: Boolean, var success: Boolean, var link: String?, var original_id: Long?, var type: Byte) : Serializable
