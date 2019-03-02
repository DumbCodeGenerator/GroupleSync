package ru.krogon500.grouplesync.interfaces

interface RequestListener {
    fun onComplete(item: Any?)
    fun onFail(e: Exception)
}