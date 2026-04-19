package com.lionfit.app.ui.Dashboard

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

/**
 * Fragment สำหรับแสดงหน้า Dashboard หลักของแอปพลิเคชัน
 * แสดงข้อมูลสรุปจากส่วนต่างๆ เช่น อาหาร, การวิ่ง และการนอน
 */
class DashboardMenu : Fragment() {

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

        // ดึงข้อมูลการวิ่งที่บันทึกไว้ล่าสุด
        lifecycleScope.launch {
            db.runDao().getAllRunsSortedByDate().collectLatest { runs ->
                if (runs.isNotEmpty()) {
                    val lastRun = runs.first()
                    tvTitle.text = lastRun.title
                    
                    // แปลงรูปแบบวันที่และเวลา
                    val sdf = SimpleDateFormat("dd/MM/yyyy 'at' hh:mm a", Locale.getDefault())
                    tvDateTime.text = sdf.format(lastRun.timestamp)
                    
                    // แสดงระยะทางและ Pace
                    tvDistance.text = String.format(Locale.getDefault(), "%.2f km", lastRun.distanceInKm)
                    
                    val paceMin = lastRun.averagePace.toInt()
                    val paceSec = ((lastRun.averagePace - paceMin) * 60).toInt()
                    tvPace.text = String.format(Locale.getDefault(), "%d:%02d/km", paceMin, paceSec)
                    
                    // แสดงเวลาที่ใช้ในการวิ่ง (แปลงจากมิลลิวินาทีเป็นนาที)
                    val durationMin = lastRun.durationInMillis / 60000
                    tvTime.text = String.format(Locale.getDefault(), "%d Min", durationMin)

                    // โหลดรูปภาพเส้นทางวิ่ง (Map Snapshot)
                    lastRun.mapSnapshotUrl?.let { url ->
                        ivMap.load(url) {
                            placeholder(android.R.drawable.ic_dialog_map)
                        }
                    }
                }
            }
        }
    }

    /**
     * ดึงข้อมูลและคำนวณค่าเฉลี่ยการนอน
     */
    private fun observeSleepData(view: View) {
        val tvHours = view.findViewById<TextView>(R.id.tvSleepAvgHours)
        val tvMins = view.findViewById<TextView>(R.id.tvSleepAvgMins)

        // ดึงข้อมูลการนอนทั้งหมดเพื่อหาค่าเฉลี่ย
        lifecycleScope.launch {
            db.sleepDao().getAllSleepRecords().collectLatest { records ->
                if (records.isNotEmpty()) {
                    val avgTotalHours = records.map { it.totalHoursSlept }.average()
                    val hours = avgTotalHours.toInt()
                    val minutes = ((avgTotalHours - hours) * 60).toInt()
                    
                    // แสดงค่าเฉลี่ยเป็น ชั่วโมง และ นาที
                    tvHours.text = hours.toString()
                    tvMins.text = minutes.toString()
                }
            }
        }
    }
}