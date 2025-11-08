package com.example.visualide

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform