package com.bmarthi.hello_android

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform