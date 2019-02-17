package ru.krogon500.grouplesync.interfaces

import android.os.Bundle

interface LoginListener {
    fun goToMain(type: Byte?, args: Bundle)
}
