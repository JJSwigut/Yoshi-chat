package com.example.yoshichat.data.model

data class AuthSession(
    val accessToken: String,
    val userId: String?,
    val email: String? = null,
    val cookieHeader: String? = null,
)
