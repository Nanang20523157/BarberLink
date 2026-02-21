package com.example.barberlink.Utils

import android.util.Log

object Logger {

    private fun buildLogMsg(message: String): String {
        val element = Throwable().stackTrace[2] // [0] = Throwable, [1] = buildLogMsg, [2] = caller
        val fileName = element.fileName
        val lineNumber = element.lineNumber
        val methodName = element.methodName
        return "#$methodName -> $message ($fileName:$lineNumber)"
    }

    fun d(tag: String, message: String) {
        Log.d(tag, buildLogMsg(message))
    }

    fun i(tag: String, message: String) {
        Log.i(tag, buildLogMsg(message))
    }

    fun w(tag: String, message: String) {
        Log.w(tag, buildLogMsg(message))
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, buildLogMsg(message), throwable)
    }

    fun v(tag: String, message: String) {
        Log.v(tag, buildLogMsg(message))
    }
}
