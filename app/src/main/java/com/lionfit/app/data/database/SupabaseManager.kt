package com.lionfit.app.data.database

import com.lionfit.app.BuildConfig
import com.lionfit.app.data.model.RunSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest

object SupabaseManager {

    // Initialize the client using your secure keys
    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Postgrest)
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