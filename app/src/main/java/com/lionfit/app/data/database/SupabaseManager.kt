package com.lionfit.app.data.database

import com.lionfit.app.BuildConfig
import com.lionfit.app.data.model.RunSession
import com.lionfit.app.data.model.UserProfile
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SupabaseManager {

    // Initialize the client using your secure keys
    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Postgrest)
        install(Auth)
        install(Storage)
    }

    suspend fun logOutUser() {
        withContext(Dispatchers.IO) {
            try {
                client.auth.signOut()
            } catch (e: Exception) {
                client.auth.clearSession()
            }
        }
    }

    suspend fun getProfile(userId: String): UserProfile? {
        return withContext(Dispatchers.IO) {
            try {
                // In Kotlin, filters must be inside a select block!
                client.postgrest.from("profiles")
                    .select {
                        filter {
                            eq("id", userId)
                        }
                    }.decodeSingleOrNull<UserProfile>()
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun updateProfile(profile: UserProfile) {
        withContext(Dispatchers.IO) {
            client.postgrest.from("profiles").upsert(profile)
        }
    }

    suspend fun uploadProfilePicture(userId: String, byteArray: ByteArray): String {
        return withContext(Dispatchers.IO) {
            val fileName = "$userId.jpg"
            val bucket = client.storage.from("profile-pictures")

            bucket.upload(fileName, byteArray, upsert = true)
            bucket.publicUrl(fileName)
        }
    }

    // The Cloud Sync Function
    suspend fun syncRunToCloud(runSession: RunSession): Boolean {
        return try {
            client.postgrest["run_sessions"].insert(runSession)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            // If the user is offline or the server rejects it, it safely returns false
            false
        }
    }
}