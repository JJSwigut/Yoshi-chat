package com.example.yoshichat.data.auth

import com.example.yoshichat.data.model.AuthSession

interface AuthRepository {
    suspend fun getSession(): AuthSession
}
