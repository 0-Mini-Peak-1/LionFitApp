package com.lionfit.app.data.database

import com.lionfit.app.BuildConfig
import com.lionfit.app.data.model.DietLog
import com.lionfit.app.data.model.RunSession
import com.lionfit.app.data.model.SleepRecord
import com.lionfit.app.data.model.UserProfile
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.postgrest.query.Order
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

    suspend fun getUserRunHistory(userId: String): List<RunSession> {
        return try {
            client.postgrest["run_sessions"]
                .select {
                    filter { eq("user_id", userId) }
                    order("timestamp", order = Order.DESCENDING)
                }
                .decodeList<RunSession>()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList() // Return an empty list if something fails or they have no runs
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

    suspend fun saveRunSession(runSession: RunSession): Boolean {
        return try {
            client.postgrest["run_sessions"].insert(runSession)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            // If the user is offline or the server rejects it, it safely returns false
            false
        }
    }

    suspend fun uploadRunSnapshot(userId: String, timestamp: Long, imageBytes: ByteArray): String? {
        return try {
            // Create a unique file name using their ID and the time
            val fileName = "${userId}_${timestamp}.jpg"
            val bucket = client.storage["run-snapshots"]

            bucket.upload(path = fileName, data = imageBytes, upsert = true)

            // Return the public URL
            bucket.publicUrl(fileName)
        } catch (e: Exception) {
            android.util.Log.e("SupabaseStorage", "Failed to upload snapshot: ${e.message}")
            null
        }
    }

    suspend fun updateRunTitle(runId: String, newTitle: String) {
        client.postgrest["run_sessions"].update({
            set("title", newTitle)
        }) {
            filter { eq("id", runId) }
        }
    }

    // Delete the database row
    suspend fun deleteRunSession(runId: String) {
        client.postgrest["run_sessions"].delete {
            filter { eq("id", runId) }
        }
    }

    // Delete the image file to save storage space
    suspend fun deleteRunSnapshot(fileName: String) {
        try {
            client.storage["run-snapshots"].delete(listOf(fileName))
        } catch (e: Exception) {
            // We just catch and log this. If the image delete fails,
            // we don't want it to stop the whole database deletion process.
            android.util.Log.e("SupabaseStorage", "Failed to delete image: ${e.message}")
        }
    }

    // Sync Diet to Cloud
    suspend fun syncDietToCloud(dietLog: DietLog): Boolean {
        return try {
            client.postgrest["diet_logs"].insert(dietLog)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Sync Sleep to Cloud
    suspend fun saveSleepRecord(sleepRecord: SleepRecord): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                client.postgrest["sleep_records"].insert(sleepRecord)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    // Delete Sleep from Cloud
    suspend fun deleteSleepRecord(recordId: String) {
        withContext(Dispatchers.IO) {
            try {
                client.postgrest["sleep_records"].delete {
                    filter { eq("id", recordId) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}