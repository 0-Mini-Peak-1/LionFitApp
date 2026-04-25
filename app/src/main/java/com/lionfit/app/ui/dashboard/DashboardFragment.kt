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
import kotlinx.coroutines.withContext
import com.lionfit.app.data.database.SupabaseManager
import io.github.jan.supabase.gotrue.auth
import androidx.compose.material3.MaterialTheme
import com.lionfit.app.MainActivity
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import com.lionfit.app.ui.sleep.SleepChartContent
import kotlinx.coroutines.flow.combine
import com.lionfit.app.utils.Calculators
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.delay
import androidx.fragment.app.activityViewModels
import com.lionfit.app.ui.shared.SharedViewModel

/**
 * Fragment สำหรับแสดงหน้า Dashboard หลักของแอปพลิเคชัน
 * แสดงข้อมูลสรุปจากส่วนต่างๆ เช่น อาหาร, การวิ่ง และการนอน
 */
class DashboardFragment : Fragment() {

    private val dashboardSleepRecords = androidx.compose.runtime.mutableStateListOf<com.lionfit.app.data.model.SleepRecord>()
    private val sharedViewModel: SharedViewModel by activityViewModels()
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

        // Swipe to refresh
        val swipeRefreshLayout = view.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setOnRefreshListener {
            // เมื่อดึงลงมา ให้สั่งโหลดข้อมูลใหม่ทั้งหมด
            observeDietData(view)
            observeRunData(view)
            observeSleepData(view)

            // ปิดตัวหมุนรีเฟรชเมื่อทำงานเสร็จ (ในกรณีนี้คำสั่งทำงานเร็วมาก สามารถสั่งปิดได้เลย)
            swipeRefreshLayout.isRefreshing = false
        }

        // Listen for profile changes
        sharedViewModel.profileUpdatedSignal.observe(viewLifecycleOwner) { timestamp ->
            // If the timestamp is greater than 0, a new update just happened
            if (timestamp > 0L) {
                observeDietData(view)
                observeRunData(view)
                observeSleepData(view)
            }
        }
    }

    /**
     * ตั้งค่าส่วนแบนเนอร์ด้านบนสุดของหน้า Dashboard
     */
    private fun setupBanner(view: View) {
        val viewPager = view.findViewById<ViewPager2>(R.id.bannerViewPager)

        // Replace with ads or anything
        val adImages = listOf(
            "https://images.unsplash.com/photo-1517836357463-d25dfeac3438?q=80&w=1000&auto=format&fit=crop", // Gym ad
            "https://images.unsplash.com/photo-1461896836934-ffe607ba8211?q=80&w=1000&auto=format&fit=crop", // Running shoes ad
            "https://images.unsplash.com/photo-1540189549336-e6e99c3679fe?q=80&w=1000&auto=format&fit=crop"  // Healthy food ad
        )

        // Attach the adapter
        viewPager.adapter = BannerAdapter(adImages)

        // The Auto-Sliding
        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                delay(3500) // Wait 3.5 seconds

                // If we aren't at the end of the list, go to the next one. Otherwise, loop back to 0
                if (viewPager.adapter != null) {
                    val itemCount = viewPager.adapter?.itemCount ?: 0
                    if (itemCount > 0) {
                        val nextItem = (viewPager.currentItem + 1) % itemCount
                        viewPager.setCurrentItem(nextItem, true) // 'true' makes it slide smoothly
                    }
                }
            }
        }
    }

    inner class BannerAdapter(private val imageUrls: List<String>) :
        androidx.recyclerview.widget.RecyclerView.Adapter<BannerAdapter.BannerViewHolder>() {

        inner class BannerViewHolder(val imageView: ImageView) : androidx.recyclerview.widget.RecyclerView.ViewHolder(imageView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BannerViewHolder {
            val imageView = ImageView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            return BannerViewHolder(imageView)
        }

        override fun onBindViewHolder(holder: BannerViewHolder, position: Int) {
            holder.imageView.load(imageUrls[position]) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_gallery)
            }
        }

        override fun getItemCount() = imageUrls.size
    }

    /**
     * ดึงข้อมูลและติดตามการเปลี่ยนแปลงของข้อมูลอาหาร (แคลอรี่) ของวันนี้
     * พร้อมระบบประมวลผล BMR/TDEE และคำแนะนำการออกกำลังกาย (Smart Suggestion)
     */
    private fun observeDietData(view: View) {
        val tvNetCal = view.findViewById<TextView>(R.id.tvEatenCal)
        val tvOverCal = view.findViewById<TextView>(R.id.tvOverCal)
        val tvGoalCal = view.findViewById<TextView>(R.id.tvGoalCal)

        // ผูก UI ของ Suggestion Card (อย่าลืมเพิ่มใน XML)
        val cardSuggestion = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardSuggestion)
        val tvSuggestionText = view.findViewById<TextView>(R.id.tvSuggestionText)

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

        viewLifecycleOwner.lifecycleScope.launch {
            // 1. กำหนดค่าพื้นฐานไว้ก่อน (เผื่ออินเทอร์เน็ตมีปัญหา)
            var dynamicGoal = resources.getInteger(R.integer.goal_calories)
            var userWeight = 70.0

            // 2. ดึงข้อมูลส่วนตัวจาก Supabase มาคำนวณ TDEE แบบไดนามิก
            withContext(Dispatchers.IO) {
                try {
                    val currentUser = SupabaseManager.client.auth.currentUserOrNull()
                    if (currentUser != null) {
                        val profile = SupabaseManager.getProfile(currentUser.id)
                        if (profile != null) {
                            userWeight = if (profile.weightKg > 0) profile.weightKg else 70.0
                            val height = if (profile.heightCm > 0) profile.heightCm else 170.0
                            val age = Calculators.calculateAge(profile.birthDate)
                            val gender = profile.gender ?: "male"

                            // คำนวณสมการ Mifflin-St Jeor
                            val bmr = Calculators.calculateBMR(userWeight, height, age, gender)
                            dynamicGoal = Calculators.calculateTDEE(bmr).toInt()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // อัปเดตตัวเลขเป้าหมายในวงกลมส้ม
            tvGoalCal.text = String.format(Locale.getDefault(), "%,d", dynamicGoal)

            // เริ่มรวม Flow ของอาหารและการวิ่ง
            val dietFlow = db.dietDao().getDietLogsForRange(startOfDay, endOfDay)
            val runFlow = db.runDao().getAllRunsSortedByDate()

            dietFlow.combine(runFlow) { logs, runs ->
                val totalEaten = logs.sumOf { it.calories }
                val todayRuns = runs.filter { it.timestamp in startOfDay..endOfDay }
                val totalBurned = todayRuns.sumOf { it.caloriesBurned }

                // ส่ง Net Calories ออกไป
                totalEaten - totalBurned
            }.collectLatest { netCalories ->

                // Format the Net Calories
                tvNetCal.text = String.format(Locale.getDefault(), "%,d", netCalories)

                val diff = netCalories - dynamicGoal
                tvOverCal.text = if (diff > 0) String.format(Locale.getDefault(), "%,d", diff) else "0"

                // THE SMART SUGGESTION ENGINE
                if (cardSuggestion != null && tvSuggestionText != null) {
                    cardSuggestion.visibility = View.VISIBLE

                    if (diff > 0) {
                        // STATE 1: Over Goal (Surplus) -> Suggest Exercise with Paces!
                        // We use maxOf(1, ...) to ensure it never says "0 mins" if they are only 5 kcal over
                        val walkMins = maxOf(1, ((diff.toDouble() / (3.5 * userWeight)) * 60).toInt())
                        val jogMins = maxOf(1, ((diff.toDouble() / (8.0 * userWeight)) * 60).toInt())
                        val runMins = maxOf(1, ((diff.toDouble() / (9.8 * userWeight)) * 60).toInt())

                        cardSuggestion.setCardBackgroundColor(android.graphics.Color.parseColor("#FFF3E0")) // Soft Orange
                        tvSuggestionText.text = "You are ${String.format(Locale.getDefault(), "%,d", diff)} kcal over your target. Burn it off with:\n\n" +
                                "🚶 Walk (Light, ~12:30/km): ${String.format(Locale.getDefault(), "%,d", walkMins)} mins\n" +
                                "🏃 Jog (Medium, ~7:30/km): ${String.format(Locale.getDefault(), "%,d", jogMins)} mins\n" +
                                "🏃\u200D♂\uFE0F\uD83D\uDCA8 Run (Fast, ~6:00/km): ${String.format(Locale.getDefault(), "%,d", runMins)} mins"

                    } else if (diff < 0) {
                        // Under Goal (Deficit) -> We break this into 3 specific zones
                        val deficit = -diff
                        val formattedDeficit = String.format(Locale.getDefault(), "%,d", deficit)

                        if (deficit in 300..700) {
                            // STATE 2: The Weight Loss "Sweet Spot" (300 - 700 kcal deficit)
                            cardSuggestion.setCardBackgroundColor(android.graphics.Color.parseColor("#E8F5E9")) // Soft Blue
                            tvSuggestionText.text = "You are $formattedDeficit kcal under your goal. This is a great range! If you maintain this daily deficit, you can expect to lose about 0.5 kg per week safely."

                        } else if (deficit > 700) {
                            // STATE 3: Too low! (Dangerous deficit)
                            cardSuggestion.setCardBackgroundColor(android.graphics.Color.parseColor("#f7abab")) // Soft Red
                            tvSuggestionText.text = "You are $formattedDeficit kcal short of your daily goal. Your deficit is quite high! Treat yourself to a healthy meal to keep your metabolism running properly."

                        } else {
                            // STATE 4: Close to the goal (1 - 299 kcal deficit)
                            // Calculate how much more they need to burn to reach the 300 kcal weight loss zone
                            val extraBurnNeeded = 300 - deficit
                            val extraJogMins = maxOf(1, ((extraBurnNeeded.toDouble() / (8.0 * userWeight)) * 60).toInt())

                            cardSuggestion.setCardBackgroundColor(android.graphics.Color.parseColor("#F3E5F5")) // Soft Purple
                            tvSuggestionText.text = "You are $formattedDeficit kcal under your goal. This is perfect for maintaining your weight!\n\n" +
                                    "Want to reach the weight loss zone? Burn $extraBurnNeeded more kcal with a quick $extraJogMins-minute jog (~7:30/km)!"
                        }

                    } else {
                        // STATE 5: Exactly 0
                        cardSuggestion.setCardBackgroundColor(android.graphics.Color.parseColor("#E3F2FD")) // Soft Blue
                        tvSuggestionText.text = "Perfect! You hit your calorie goal exactly. Keep up the great work!"
                    }
                }
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
        val emptyLastRun = view.findViewById<View>(R.id.layout_empty_last_run)

        cardLastRun.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                delay(200)
                (requireActivity() as MainActivity).switchFragment("run_history", true)
            }
        }

        // 1. ให้ UI เฝ้ามองการวิ่งล่าสุดจาก Room (อัปเดตทันทีที่กลับมาจาก SaveActivity)
        viewLifecycleOwner.lifecycleScope.launch {
            db.runDao().getAllRunsSortedByDate().collectLatest { runs ->
                if (runs.isNotEmpty()) {
                    // Show the card, hide the empty state
                    cardLastRun.visibility = View.VISIBLE
                    emptyLastRun.visibility = View.GONE

                    val lastRun = runs.first()

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
                } else {
                    // Hide the card, show the empty state
                    cardLastRun.visibility = View.GONE
                    emptyLastRun.visibility = View.VISIBLE
                }
            }
        }

        // ดึงข้อมูลจาก Supabase มาอัปเดตลง Room (ทำเบื้องหลัง)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val currentUser = SupabaseManager.client.auth.currentUserOrNull()
                if (currentUser != null) {
                    val cloudRuns = SupabaseManager.getUserRunHistory(currentUser.id)
                    if (cloudRuns.isNotEmpty()) {
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
        val cardSleep = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardSleep)
        val sleepShield = view.findViewById<View>(R.id.viewSleepShield)

        val navigateToSleep = {
            viewLifecycleOwner.lifecycleScope.launch {
                delay(200)
                (requireActivity() as MainActivity).switchFragment("sleep")
            }
        }

        cardSleep?.setOnClickListener { navigateToSleep() }
        sleepShield?.setOnClickListener { navigateToSleep() }

        val composeView = view.findViewById<androidx.compose.ui.platform.ComposeView>(R.id.dashboard_sleep_chart)
        composeView.setContent {
            MaterialTheme {
                SleepChartContent(
                    records = dashboardSleepRecords,
                    startOfWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)),
                    selectedRecord = null,
                    onRecordClick = { }
                )
            }
        }

        // ให้ UI เฝ้ามอง Room Database (ทำงานแบบ Real-time)
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

                    val tvSleepFeedback = view.findViewById<TextView>(R.id.tvSleepFeedback)
                    if (avgTotalHours < 6.0) {
                        tvSleepFeedback?.text = "Low sleep detected. Prioritize rest tonight to avoid muscle fatigue!"
                        tvSleepFeedback?.setTextColor(android.graphics.Color.parseColor("#E57373")) // Soft Red
                    } else if (avgTotalHours in 6.0..8.0) {
                        tvSleepFeedback?.text = "Perfect sleep average! Your body is primed for running."
                        tvSleepFeedback?.setTextColor(android.graphics.Color.parseColor("#81C784")) // Soft Green
                    } else {
                        tvSleepFeedback?.text = "Great recovery time! You are well-rested."
                        tvSleepFeedback?.setTextColor(android.graphics.Color.parseColor("#64B5F6")) // Soft Blue
                    }
                } else {
                    tvHours.text = "0"
                    tvMins.text = "0"

                    // Reset suggestion text
                    val tvSleepFeedback = view.findViewById<TextView>(R.id.tvSleepFeedback)
                    tvSleepFeedback?.text = "Log your sleep tonight to get recovery insights!"
                    tvSleepFeedback?.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                }
            }
        }

        // 2. ดึงข้อมูลจาก Supabase มาอัปเดตลง Room (ทำเบื้องหลัง)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val currentUser = SupabaseManager.client.auth.currentUserOrNull()
                if (currentUser != null) {
                    val cloudSleepRecords = SupabaseManager.getUserSleepHistory(currentUser.id)
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