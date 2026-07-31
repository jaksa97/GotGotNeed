package com.gotgotneed

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform