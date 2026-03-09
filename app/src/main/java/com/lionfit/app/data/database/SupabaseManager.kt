package com.lionfit.app.data.database

import com.lionfit.app.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseManager {

    // This creates a single, global client using the secure keys we just set up
    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        // Install the PostgreSQL database plugin
        install(Postgrest)

        // Install the Authentication plugin
        install(Auth)
    }
}