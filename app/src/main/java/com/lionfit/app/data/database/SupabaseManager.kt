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

    // ลบข้อมูล DietLog จาก Cloud
    suspend fun deleteDietLogFromCloud(logId: String): Boolean {
        return try {
            client.postgrest["diet_logs"].delete {
                filter { eq("id", logId) }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ลบข้อมูล WaterLog จาก Cloud
    suspend fun deleteWaterLogFromCloud(logId: String): Boolean {
        return try {
            client.postgrest["water_logs"].delete {
                filter { eq("id", logId) }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Sync Water to Cloud
    suspend fun syncWaterToCloud(waterLog: com.lionfit.app.data.model.WaterLog): Boolean {
        val currentUser = client.auth.currentUserOrNull() ?: return false
        return withContext(Dispatchers.IO) {
            try {
                // ต้องมั่นใจว่า userId เป็น UUID ของผู้ใช้จริง
                val logWithUser = waterLog.copy(userId = currentUser.id)
                client.postgrest.from("water_logs").insert(logWithUser)
                true
            } catch (e: Exception) {
                android.util.Log.e("SupabaseSync", "Water Sync Failed: ${e.message}")
                false
            }
        }
    }

    // บันทึกเมนูอาหารใหม่ลงตาราง foods
    // ดึงรายการวันที่ทั้งหมดที่มีข้อมูลจาก Cloud
    suspend fun getLoggedDates(): Set<Long> {
        val currentUser = client.auth.currentUserOrNull() ?: return emptySet()
        return withContext(Dispatchers.IO) {
            try {
                val dietDates = client.postgrest.from("diet_logs")
                    .select { filter { eq("user_id", currentUser.id) } }
                    .decodeList<DietLog>()
                    .map { (it.dateLogged / 86400000) * 86400000 }

                val waterDates = client.postgrest.from("water_logs")
                    .select { filter { eq("user_id", currentUser.id) } }
                    .decodeList<com.lionfit.app.data.model.WaterLog>()
                    .map { (it.dateLogged / 86400000) * 86400000 }

                (dietDates + waterDates).toSet()
            } catch (e: Exception) {
                android.util.Log.e("SupabaseSync", "Failed to fetch dates: ${e.message}")
                emptySet()
            }
        }
    }

    // ดึงข้อมูล DietLog ของวันที่กำหนดจาก Cloud
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
        } catch (e: Exception) { emptyList() }
    }

    // ดึงรายการอาหารทั้งหมด (รวมจาก foods กลาง และ private_foods ของผู้ใช้)
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
            e.printStackTrace()
            emptyList()
        }
    }

    // ดึงเฉพาะเมนูอาหารส่วนตัวของผู้ใช้
    suspend fun getOnlyPrivateFoods(): List<com.lionfit.app.data.model.PrivateFood> {
        val currentUser = client.auth.currentUserOrNull() ?: return emptyList()
        return try {
            client.postgrest.from("private_foods")
                .select { filter { eq("user_id", currentUser.id) } }
                .decodeList<com.lionfit.app.data.model.PrivateFood>()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // บันทึกเมนูอาหารใหม่ลงตาราง private_foods และเช็คชื่อซ้ำ
    suspend fun addNewPrivateFood(food: com.lionfit.app.data.model.PrivateFood): Result<Unit> {
        return try {
            // เช็คชื่อซ้ำในรายการอาหารที่มีอยู่แล้ว
            val existing = getAllFoods()
            if (existing.any { it.name.equals(food.name, ignoreCase = true) }) {
                return Result.failure(Exception("ALREADY_EXISTS"))
            }
            
            client.postgrest.from("private_foods").insert(food)
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // ลบเมนูอาหารส่วนตัว
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
            e.printStackTrace()
            false
        }
    }

    // ดึงข้อมูล WaterLog ของวันที่กำหนดจาก Cloud
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
            android.util.Log.e("SupabaseSync", "Failed to fetch water logs: ${e.message}")
            emptyList()
        }
    }

    // Sync Sleep to Cloud
    // --- SLEEP SYNC FUNCTIONS ---

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

    suspend fun getUserSleepHistory(userId: String): List<SleepRecord> {
        return try {
            client.postgrest["sleep_records"]
                .select {
                    filter { eq("user_id", userId) }
                }
                .decodeList<SleepRecord>()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}