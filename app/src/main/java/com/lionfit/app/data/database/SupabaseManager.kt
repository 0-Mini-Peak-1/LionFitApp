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
import io.github.jan.supabase.postgrest.query.Columns
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

    // Security Gateway
    // Routes all network errors here. Only prints if testing in Android Studio.
    private fun secureLog(tag: String, e: Exception) {
        if (BuildConfig.DEBUG) {
            android.util.Log.e(tag, "Error: ${e.message}", e)
        }
    }

    suspend fun logOutUser() {
        withContext(Dispatchers.IO) {
            try {
                client.auth.signOut()
            } catch (e: Exception) {
                secureLog("SupabaseAuth", e)
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
            secureLog("SupabaseRun", e)
            emptyList()
        }
    }

    suspend fun getProfile(userId: String): UserProfile? {
        return withContext(Dispatchers.IO) {
            try {
                client.postgrest.from("profiles")
                    .select {
                        filter {
                            eq("id", userId)
                        }
                    }.decodeSingleOrNull<UserProfile>()
            } catch (e: Exception) {
                secureLog("SupabaseProfile", e)
                null
            }
        }
    }

    suspend fun updateProfile(profile: UserProfile) {
        withContext(Dispatchers.IO) {
            try {
                client.postgrest.from("profiles").upsert(profile)
            } catch (e: Exception) {
                secureLog("SupabaseProfile", e)
                throw e
            }
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
            secureLog("SupabaseRun", e)
            false
        }
    }

    suspend fun uploadRunSnapshot(userId: String, timestamp: Long, imageBytes: ByteArray): String? {
        return try {
            val fileName = "${userId}_${timestamp}.jpg"
            val bucket = client.storage["run-snapshots"]
            bucket.upload(path = fileName, data = imageBytes, upsert = true)
            bucket.publicUrl(fileName)
        } catch (e: Exception) {
            secureLog("SupabaseStorage", e)
            null
        }
    }

    suspend fun updateRunTitle(runId: String, newTitle: String) {
        try {
            client.postgrest["run_sessions"].update({
                set("title", newTitle)
            }) {
                filter { eq("id", runId) }
            }
        } catch (e: Exception) {
            secureLog("SupabaseRun", e)
            throw e
        }
    }

    suspend fun deleteRunSession(runId: String) {
        try {
            client.postgrest["run_sessions"].delete {
                filter { eq("id", runId) }
            }
        } catch (e: Exception) {
            secureLog("SupabaseRun", e)
            throw e
        }
    }

    suspend fun deleteRunSnapshot(fileName: String) {
        try {
            client.storage["run-snapshots"].delete(listOf(fileName))
        } catch (e: Exception) {
            secureLog("SupabaseStorage", e)
        }
    }

    suspend fun syncDietToCloud(dietLog: DietLog): Boolean {
        return try {
            client.postgrest["diet_logs"].insert(dietLog)
            true
        } catch (e: Exception) {
            secureLog("SupabaseDiet", e)
            false
        }
    }

    suspend fun deleteDietLogFromCloud(logId: String): Boolean {
        return try {
            client.postgrest["diet_logs"].delete {
                filter { eq("id", logId) }
            }
            true
        } catch (e: Exception) {
            secureLog("SupabaseDiet", e)
            false
        }
    }

    suspend fun deleteWaterLogFromCloud(logId: String): Boolean {
        return try {
            client.postgrest["water_logs"].delete {
                filter { eq("id", logId) }
            }
            true
        } catch (e: Exception) {
            secureLog("SupabaseWater", e)
            false
        }
    }

    suspend fun syncWaterToCloud(waterLog: com.lionfit.app.data.model.WaterLog): Boolean {
        val currentUser = client.auth.currentUserOrNull() ?: return false
        return withContext(Dispatchers.IO) {
            try {
                val logWithUser = waterLog.copy(userId = currentUser.id)
                client.postgrest.from("water_logs").insert(logWithUser)
                true
            } catch (e: Exception) {
                secureLog("SupabaseWater", e)
                false
            }
        }
    }

    @kotlinx.serialization.Serializable
    private data class DateLoggedOnly(@kotlinx.serialization.SerialName("date_logged") val dateLogged: Long)

    suspend fun getLoggedDates(): Set<Long> {
        val currentUser = client.auth.currentUserOrNull() ?: return emptySet()
        return withContext(Dispatchers.IO) {
            try {
                // Optimization: Only select "date_logged" to save bandwidth
                val dietDates = client.postgrest.from("diet_logs")
                    .select(columns = Columns.raw("date_logged")) {
                        filter { eq("user_id", currentUser.id) }
                    }
                    .decodeList<DateLoggedOnly>()
                    .map { (it.dateLogged / 86400000) * 86400000 }

                val waterDates = client.postgrest.from("water_logs")
                    .select(columns = Columns.raw("date_logged")) {
                        filter { eq("user_id", currentUser.id) }
                    }
                    .decodeList<DateLoggedOnly>()
                    .map { (it.dateLogged / 86400000) * 86400000 }

                (dietDates + waterDates).toSet()
            } catch (e: Exception) {
                secureLog("SupabaseSync", e)
                emptySet()
            }
        }
    }

    suspend fun getDietLogsFromCloud(start: Long, end: Long): List<DietLog> {
        val currentUser = client.auth.currentUserOrNull() ?: return emptyList()
        return try {
            client.postgrest.from("diet_logs").select {
                filter {
                    eq("user_id", currentUser.id)
                    gte("date_logged", start)
                    lte("date_logged", end)
                }
            }.decodeList<DietLog>()
        } catch (e: Exception) {
            secureLog("SupabaseDiet", e)
            emptyList()
        }
    }

    suspend fun getAllFoods(): List<com.lionfit.app.data.model.FoodItem> {
        val currentUser = client.auth.currentUserOrNull()
        return try {
            val publicFoods = client.postgrest.from("foods").select().decodeList<com.lionfit.app.data.model.FoodItem>()

            val privateFoods = if (currentUser != null) {
                client.postgrest.from("private_foods")
                    .select { filter { eq("user_id", currentUser.id) } }
                    .decodeList<com.lionfit.app.data.model.PrivateFood>()
                    .map {
                        com.lionfit.app.data.model.FoodItem(
                            name = it.name,
                            calories = it.calories,
                            fat = it.fat,
                            carb = it.carb,
                            protein = it.protein,
                            serving_size = it.servingSize
                        )
                    }
            } else emptyList()

            (publicFoods + privateFoods).distinctBy { it.name.lowercase() }
        } catch (e: Exception) {
            secureLog("SupabaseDiet", e)
            emptyList()
        }
    }

    suspend fun getOnlyPrivateFoods(): List<com.lionfit.app.data.model.PrivateFood> {
        val currentUser = client.auth.currentUserOrNull() ?: return emptyList()
        return try {
            client.postgrest.from("private_foods")
                .select { filter { eq("user_id", currentUser.id) } }
                .decodeList<com.lionfit.app.data.model.PrivateFood>()
        } catch (e: Exception) {
            secureLog("SupabaseDiet", e)
            emptyList()
        }
    }

    suspend fun addNewPrivateFood(food: com.lionfit.app.data.model.PrivateFood): Result<Unit> {
        return try {
            val existing = getAllFoods()
            if (existing.any { it.name.equals(food.name, ignoreCase = true) }) {
                return Result.failure(Exception("ALREADY_EXISTS"))
            }

            client.postgrest.from("private_foods").insert(food)
            Result.success(Unit)
        } catch (e: Exception) {
            secureLog("SupabaseDiet", e)
            Result.failure(e)
        }
    }

    suspend fun deletePrivateFood(name: String): Boolean {
        val currentUser = client.auth.currentUserOrNull() ?: return false
        return try {
            client.postgrest.from("private_foods").delete {
                filter {
                    eq("user_id", currentUser.id)
                    eq("name", name)
                }
            }
            true
        } catch (e: Exception) {
            secureLog("SupabaseDiet", e)
            false
        }
    }

    suspend fun getWaterLogsFromCloud(start: Long, end: Long): List<com.lionfit.app.data.model.WaterLog> {
        val currentUser = client.auth.currentUserOrNull() ?: return emptyList()
        return try {
            client.postgrest.from("water_logs").select {
                filter {
                    eq("user_id", currentUser.id)
                    gte("date_logged", start)
                    lte("date_logged", end)
                }
            }.decodeList<com.lionfit.app.data.model.WaterLog>()
        } catch (e: Exception) {
            secureLog("SupabaseWater", e)
            emptyList()
        }
    }

    suspend fun saveSleepRecord(sleepRecord: SleepRecord): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                client.postgrest["sleep_records"].insert(sleepRecord)
                true
            } catch (e: Exception) {
                secureLog("SupabaseSleep", e)
                false
            }
        }
    }

    suspend fun deleteSleepRecord(recordId: String) {
        withContext(Dispatchers.IO) {
            try {
                client.postgrest["sleep_records"].delete {
                    filter { eq("id", recordId) }
                }
            } catch (e: Exception) {
                secureLog("SupabaseSleep", e)
            }
        }
    }

    suspend fun getUserSleepHistory(userId: String): List<SleepRecord> {
        return try {
            client.postgrest["sleep_records"]
                .select {
                    filter { eq("user_id", userId) }
                }
                .decodeList<SleepRecord>()
        } catch (e: Exception) {
            secureLog("SupabaseSleep", e)
            emptyList()
        }
    }
}