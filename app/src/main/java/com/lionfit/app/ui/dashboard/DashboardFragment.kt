package com.lionfit.app.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import com.lionfit.app.R
import com.lionfit.app.data.database.AppDatabase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import com.lionfit.app.data.database.SupabaseManager
import io.github.jan.supabase.gotrue.auth
import androidx.compose.material3.MaterialTheme
import com.lionfit.app.MainActivity
import com.lionfit.app.ui.history.RunHistoryFragment
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import com.lionfit.app.ui.sleep.SleepChartContent

/**
 * Fragment สำหรับแสดงหน้า Dashboard หลักของแอปพลิเคชัน
 * แสดงข้อมูลสรุปจากส่วนต่างๆ เช่น อาหาร, การวิ่ง และการนอน
 */
class DashboardFragment : Fragment() {

    private val dashboardSleepRecords = androidx.compose.runtime.mutableStateListOf<com.lionfit.app.data.model.SleepRecord>()
    // เชื่อมต่อกับฐานข้อมูล Room
    private val db by lazy { AppDatabase.getDatabase(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate layout สำหรับหน้านี้
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // เริ่มต้นตั้งค่าส่วนต่างๆ ของหน้าจอ
        setupBanner(view)
        observeDietData(view)
        observeRunData(view)
        observeSleepData(view)
        val swipeRefreshLayout = view.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setOnRefreshListener {
            // เมื่อดึงลงมา ให้สั่งโหลดข้อมูลใหม่ทั้งหมด
            observeDietData(view)
            observeRunData(view)
            observeSleepData(view)

            // ปิดตัวหมุนรีเฟรชเมื่อทำงานเสร็จ (ในกรณีนี้คำสั่งทำงานเร็วมาก สามารถสั่งปิดได้เลย)
            swipeRefreshLayout.isRefreshing = false
        }
    }

    /**
     * ตั้งค่าส่วนแบนเนอร์ด้านบนสุดของหน้า Dashboard
     */
    private fun setupBanner(view: View) {
        val bannerImage = view.findViewById<ImageView>(R.id.bannerImage)
        // โหลดรูปภาพตัวอย่างจาก URL โดยใช้ Coil
        bannerImage.load("https://images.unsplash.com/photo-1615484477778-ca3b77940c25?q=80&w=1000&auto=format&fit=crop") {
            crossfade(true)
            placeholder(android.R.drawable.ic_menu_gallery)
        }
    }

    /**
     * ดึงข้อมูลและติดตามการเปลี่ยนแปลงของข้อมูลอาหาร (แคลอรี่) ของวันนี้
     */
    private fun observeDietData(view: View) {
        val tvEatenCal = view.findViewById<TextView>(R.id.tvEatenCal)
        val tvOverCal = view.findViewById<TextView>(R.id.tvOverCal)
        val tvGoalCal = view.findViewById<TextView>(R.id.tvGoalCal)

        // ดึงค่าเป้าหมายแคลอรี่จากทรัพยากรของระบบ
        val goalCalories = resources.getInteger(R.integer.goal_calories)
        tvGoalCal.text = String.format(Locale.getDefault(), "%,d", goalCalories)

        // คำนวณช่วงเวลาเริ่มต้นและสิ้นสุดของวันนี้
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val endOfDay = calendar.timeInMillis

        // ติดตามข้อมูลจาก dietDao
        lifecycleScope.launch {
            db.dietDao().getDietLogsForRange(startOfDay, endOfDay).collectLatest { logs ->
                val totalEaten = logs.sumOf { it.calories }
                // แสดงยอดที่ทานไปแล้ว
                tvEatenCal.text = String.format(Locale.getDefault(), "%,d", totalEaten)
                
                // คำนวณแคลอรี่ที่เกินจากเป้าหมาย
                val diff = totalEaten - goalCalories
                tvOverCal.text = if (diff > 0) String.format(Locale.getDefault(), "%,d", diff) else "0"
            }
        }
    }

    /**
     * ดึงข้อมูลกิจกรรมการวิ่งล่าสุดมาแสดงผลบนหน้าจอ
     */
    private fun observeRunData(view: View) {
        val ivMap = view.findViewById<ImageView>(R.id.ivLastRouteMap)
        val tvTitle = view.findViewById<TextView>(R.id.tvRunTitle)
        val tvDateTime = view.findViewById<TextView>(R.id.tvRunDateTime)
        val tvDistance = view.findViewById<TextView>(R.id.tvRunDistance)
        val tvPace = view.findViewById<TextView>(R.id.tvRunPace)
        val tvTime = view.findViewById<TextView>(R.id.tvRunTime)
        val cardLastRun = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardLastRun)

        cardLastRun.setOnClickListener {
            (requireActivity() as MainActivity).switchFragment("run_history",true)
        }

        // 1. ให้ UI เฝ้ามองการวิ่งล่าสุดจาก Room (อัปเดตทันทีที่กลับมาจาก SaveActivity)
        viewLifecycleOwner.lifecycleScope.launch {
            // สมมติว่ามีฟังก์ชัน getAllRuns() หรือดึงรายการวิ่งล่าสุดใน runDao
            db.runDao().getAllRunsSortedByDate().collectLatest { runs ->
                if (runs.isNotEmpty()) {
                    // จัดการแสดงผลข้อมูลจาก Local
                    val lastRun = runs.first() // ดึงข้อมูลอันล่าสุด

                    tvTitle.text = lastRun.title
                    val sdf = SimpleDateFormat("dd/MM/yyyy 'at' hh:mm a", Locale.getDefault())
                    tvDateTime.text = sdf.format(lastRun.timestamp)
                    tvDistance.text = String.format(Locale.getDefault(), "%.2f km", lastRun.distanceInKm)

                    val paceMin = lastRun.averagePace.toInt()
                    val paceSec = ((lastRun.averagePace - paceMin) * 60).toInt()
                    tvPace.text = String.format(Locale.getDefault(), "%d:%02d/km", paceMin, paceSec)

                    val durationMin = lastRun.durationInMillis / 60000
                    tvTime.text = String.format(Locale.getDefault(), "%d Min", durationMin)

                    if (lastRun.mapSnapshotUrl != null) {
                        ivMap.load(lastRun.mapSnapshotUrl) {
                            placeholder(android.R.drawable.ic_dialog_map)
                            error(android.R.drawable.ic_dialog_map)
                        }
                    }
                }
            }
        }

        // 2. ดึงข้อมูลจาก Supabase มาอัปเดตลง Room (ทำเบื้องหลัง)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val currentUser = SupabaseManager.client.auth.currentUserOrNull()
                if (currentUser != null) {
                    val cloudRuns = SupabaseManager.getUserRunHistory(currentUser.id)
                    if (cloudRuns.isNotEmpty()) {
                        // อัปเดตข้อมูลวิ่งทั้งหมดลง Room
                        db.runDao().insertAllRuns(cloudRuns)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * ดึงข้อมูลและคำนวณค่าเฉลี่ยการนอน
     */
    private fun observeSleepData(view: View) {
        val tvHours = view.findViewById<TextView>(R.id.tvSleepAvgHours)
        val tvMins = view.findViewById<TextView>(R.id.tvSleepAvgMins)

        // Find the Compose Bridge
        val composeView = view.findViewById<androidx.compose.ui.platform.ComposeView>(R.id.dashboard_sleep_chart)

        //
        composeView.setContent {
            MaterialTheme {
                SleepChartContent(
                    records = dashboardSleepRecords,
                    startOfWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)),
                    selectedRecord = null,
                    onRecordClick = {
//                        val bottomNav = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
//                        bottomNav?.selectedItemId = R.id.nav_sleep
                        (requireActivity() as MainActivity).switchFragment("sleep")

                    }
                )
            }
        }

        // 1. ให้ UI เฝ้ามอง Room Database (ทำงานแบบ Real-time)
        viewLifecycleOwner.lifecycleScope.launch {
            db.sleepDao().getAllSleepRecords().collectLatest { records ->

                dashboardSleepRecords.clear()
                dashboardSleepRecords.addAll(records)

                if (records.isNotEmpty()) {
                    val avgTotalHours = records.map { it.totalHoursSlept }.average()
                    val hours = avgTotalHours.toInt()
                    val minutes = ((avgTotalHours - hours) * 60).toInt()

                    tvHours.text = hours.toString()
                    tvMins.text = minutes.toString()
                } else {
                    tvHours.text = "0"
                    tvMins.text = "0"
                }
            }
        }

        // 2. ดึงข้อมูลจาก Supabase มาอัปเดตลง Room (ทำเบื้องหลัง)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val currentUser = SupabaseManager.client.auth.currentUserOrNull()
                if (currentUser != null) {
                    // ดึงข้อมูล Sleep ล่าสุดจาก Cloud
                    val cloudSleepRecords = SupabaseManager.getUserSleepHistory(currentUser.id) // สมมติว่าใช้ชื่อฟังก์ชันนี้ใน SupabaseManager

                    if (cloudSleepRecords.isNotEmpty()) {
                        db.sleepDao().insertAllSleepRecords(cloudSleepRecords)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}