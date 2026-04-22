package com.lionfit.app.ui.diet
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.progressindicator.CircularProgressIndicator
import androidx.appcompat.app.AlertDialog
import com.lionfit.app.R
import com.lionfit.app.data.database.AppDatabase
import com.lionfit.app.data.model.DietLog
import com.lionfit.app.data.database.SupabaseManager
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.lionfit.app.data.database.WaterDao
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.DayViewDecorator
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Parcel
class DietFragment : Fragment(R.layout.fragment_diet) {

    private val db by lazy { AppDatabase.getDatabase(requireContext()) }

    private var currentWaterMl = 0

    private var selectedDate = java.util.Calendar.getInstance()
    private var isDateExplicitlySelected = false
    private var availableDates = setOf<String>()

    override fun onResume() {
        super.onResume()
        refreshDateIfNecessary()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            refreshDateIfNecessary()
        }
    }

    private fun refreshDateIfNecessary() {
        val now = java.util.Calendar.getInstance()
        // ถ้าไม่ได้เลือกวันที่เอง และ วันที่ในตัวแปรไม่ใช่ "วันนี้" จริงๆ ให้รีเฟรช
        if (!isDateExplicitlySelected && !isSameDay(selectedDate, now)) {
            selectedDate = now
            view?.let { 
                updateDateDisplay(it)
                observeDietData(it)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateDateDisplay(view)

        // คลิกที่วันที่เพื่อเปิดปฏิทิน
        view.findViewById<View>(R.id.tvSelectedDate).setOnClickListener {
            showDatePicker(view)
        }


        // ติดตามวันที่มีข้อมูลเพื่อเปิด/ปิดปุ่ม
        lifecycleScope.launch {
            db.dietDao().getDatesWithLogs().collectLatest { dates ->
                availableDates = dates.toSet()
                updateDateButtonsVisibility(view)
            }
        }

        view.findViewById<View>(R.id.btnAddWater).setOnClickListener {
            AddWaterBottomSheet { addedMl ->
                saveWaterLog(addedMl)
            }.show(parentFragmentManager, "AddWater")
        }

        val mealButtons = mapOf(
            R.id.btnAddBreakfast to "Breakfast",
            R.id.btnAddLunch     to "Lunch",
            R.id.btnAddDinner    to "Dinner",
            R.id.btnAddSnack     to "Snack"
        )
        mealButtons.forEach { (btnId, mealType) ->
            view.findViewById<View>(btnId).setOnClickListener {
                // แปลง selectedDate เป็น UTC Midnight ก่อนส่งไป AddFoodFragment
                val utcCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                utcCal.clear()
                utcCal.set(selectedDate.get(java.util.Calendar.YEAR), 
                           selectedDate.get(java.util.Calendar.MONTH), 
                           selectedDate.get(java.util.Calendar.DAY_OF_MONTH))
                
                childFragmentManager.beginTransaction()
                    .replace(R.id.diet_sub_container, AddFoodFragment.newInstance(mealType, utcCal.timeInMillis))
                    .addToBackStack(null)
                    .commit()
            }
        }

        view.findViewById<View>(R.id.btnUndoWater)?.setOnClickListener {
            // เช็คว่ามีปุ่มให้กดไหม
            android.util.Log.d("UndoWater", "ปุ่มถูกกดแล้ว!")

            val currentUser = SupabaseManager.client.auth.currentUserOrNull()
            if (currentUser == null) {
                android.widget.Toast.makeText(requireContext(), "Error: ไม่พบ User (คุณล็อกอินหรือยัง?)", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    // 1. คำนวณหาเวลาเริ่มต้นและสิ้นสุดของวันที่กำลังเลือกอยู่ (selectedDate)
                    val startOfDay = selectedDate.clone() as java.util.Calendar
                    startOfDay.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    startOfDay.set(java.util.Calendar.MINUTE, 0)
                    startOfDay.set(java.util.Calendar.SECOND, 0)
                    startOfDay.set(java.util.Calendar.MILLISECOND, 0)

                    val endOfDay = selectedDate.clone() as java.util.Calendar
                    endOfDay.set(java.util.Calendar.HOUR_OF_DAY, 23)
                    endOfDay.set(java.util.Calendar.MINUTE, 59)
                    endOfDay.set(java.util.Calendar.SECOND, 59)
                    endOfDay.set(java.util.Calendar.MILLISECOND, 999)

                    // 2. ค้นหาน้ำแก้วล่าสุดของวันนี้
                    val latestLog = db.waterDao().getLatestWaterLogOfDate(currentUser.id, startOfDay.timeInMillis, endOfDay.timeInMillis)

                    if (latestLog != null) {
                        // 3. ลบออกจาก Local (เครื่อง)
                        db.waterDao().deleteWaterLog(latestLog)

                        // 4. ลบออกจาก Cloud (Supabase)
                        SupabaseManager.deleteWaterLogFromCloud(latestLog.id)

                        // ถ้ามาถึงตรงนี้ได้ แสดงว่าลบสำเร็จ 100%
                        android.widget.Toast.makeText(requireContext(), "Undo ${latestLog.amountMl} ml", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(requireContext(), "ไม่พบประวัติ", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    // ถ้าพังระหว่างทาง มันจะฟ้องข้อความตรงนี้
                    android.widget.Toast.makeText(requireContext(), "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }

        observeDietData(view)
    }

    private fun saveWaterLog(amount: Int) {
        val currentUser = SupabaseManager.client.auth.currentUserOrNull() ?: return
        lifecycleScope.launch {
            val log = com.lionfit.app.data.model.WaterLog(
                userId = currentUser.id,
                amountMl = amount,
                dateLogged = selectedDate.timeInMillis
            )
            // Save to Local
            db.waterDao().insertWaterLog(log)
            // Sync to Cloud
            SupabaseManager.syncWaterToCloud(log)
        }
    }

    private fun showDatePicker(view: View) {
        val constraintsBuilder = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointBackward.now())

        // สร้าง Calendar สำหรับ UTC เพื่อให้ MaterialDatePicker เลือกวันได้ถูกต้อง
        val utcSelection = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        utcSelection.clear()
        utcSelection.set(selectedDate.get(java.util.Calendar.YEAR), 
                         selectedDate.get(java.util.Calendar.MONTH), 
                         selectedDate.get(java.util.Calendar.DAY_OF_MONTH))

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select Date")
            .setSelection(utcSelection.timeInMillis)
            .setCalendarConstraints(constraintsBuilder.build())
            .setDayViewDecorator(object : DayViewDecorator() {
                override fun getTextColor(
                    context: android.content.Context,
                    year: Int,
                    month: Int,
                    day: Int,
                    valid: Boolean,
                    selected: Boolean
                ): ColorStateList? {
                    if (!valid) return null

                    val cal = java.util.Calendar.getInstance()
                    cal.set(year, month, day)
                    
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    val dateStr = sdf.format(cal.time)

                    // วันที่มีข้อมูล หรือ วันนี้ หรือ วันที่กำลังเลือกอยู่
                    val isToday = isSameDay(cal, java.util.Calendar.getInstance())
                    val hasData = availableDates.contains(dateStr) || isToday

                    return if (!hasData && !selected) {
                        ColorStateList.valueOf(Color.LTGRAY)
                    } else {
                        null
                    }
                }

                override fun describeContents(): Int = 0
                override fun writeToParcel(dest: Parcel, flags: Int) {}
            })
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            // MaterialDatePicker คืนค่าเป็น UTC millis (Midnight ของวันนั้นใน UTC)
            val utcCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
            utcCal.timeInMillis = selection
            
            // ตั้งค่า selectedDate (Local) ให้ตรงกับวันที่ที่เลือกจาก UTC
            selectedDate.set(
                utcCal.get(java.util.Calendar.YEAR),
                utcCal.get(java.util.Calendar.MONTH),
                utcCal.get(java.util.Calendar.DAY_OF_MONTH)
            )
            
            isDateExplicitlySelected = true
            updateDateDisplay(view)
            observeDietData(view)
        }

        datePicker.show(parentFragmentManager, "MATERIAL_DATE_PICKER")
    }

    private fun hasDataOnDate(cal: java.util.Calendar): Boolean {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val dateStr = sdf.format(cal.time)
        return availableDates.contains(dateStr) || isSameDay(cal, java.util.Calendar.getInstance())
    }

    private fun updateDateButtonsVisibility(view: View) {
        val today = java.util.Calendar.getInstance()
        val prevDay = selectedDate.clone() as java.util.Calendar
        prevDay.add(java.util.Calendar.DAY_OF_YEAR, -1)
        
        val nextDay = selectedDate.clone() as java.util.Calendar
        nextDay.add(java.util.Calendar.DAY_OF_YEAR, 1)

    }

    private fun updateDateDisplay(view: View) {
        val sdf = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
        val today = java.util.Calendar.getInstance()
        
        val dateText = if (isSameDay(selectedDate, today)) {
            "Today"
        } else {
            sdf.format(selectedDate.time)
        }
        view.findViewById<TextView>(R.id.tvSelectedDate).text = dateText
        updateDateButtonsVisibility(view)
    }

    private fun isSameDay(cal1: java.util.Calendar, cal2: java.util.Calendar): Boolean {
        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
               cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
    }

    private var dietJob: kotlinx.coroutines.Job? = null

    private fun observeDietData(view: View) {
        dietJob?.cancel()
        
        val tvEaten          = view.findViewById<TextView>(R.id.tvEaten)
        val tvKcalLeft       = view.findViewById<TextView>(R.id.tvKcalLeft)
        val tvWaterAmount    = view.findViewById<TextView>(R.id.tvWaterAmount)
        val progressEaten    = view.findViewById<CircularProgressIndicator>(R.id.progressEaten)
        val progressKcalLeft = view.findViewById<CircularProgressIndicator>(R.id.progressKcalLeft)
        val progressGoal     = view.findViewById<CircularProgressIndicator>(R.id.progressGoal)

        val llBreakfast = view.findViewById<LinearLayout>(R.id.llBreakfastItems)
        val llLunch     = view.findViewById<LinearLayout>(R.id.llLunchItems)
        val llDinner    = view.findViewById<LinearLayout>(R.id.llDinnerItems)
        val llSnack     = view.findViewById<LinearLayout>(R.id.llSnackItems)

        val goalCalories = resources.getInteger(R.integer.goal_calories)
        progressGoal.progress = goalCalories
        view.findViewById<TextView>(R.id.tvGoal).text = goalCalories.toString()

        val startOfDay = selectedDate.clone() as java.util.Calendar
        startOfDay.set(java.util.Calendar.HOUR_OF_DAY, 0)
        startOfDay.set(java.util.Calendar.MINUTE, 0)
        startOfDay.set(java.util.Calendar.SECOND, 0)
        startOfDay.set(java.util.Calendar.MILLISECOND, 0)

        val endOfDay = selectedDate.clone() as java.util.Calendar
        endOfDay.set(java.util.Calendar.HOUR_OF_DAY, 23)
        endOfDay.set(java.util.Calendar.MINUTE, 59)
        endOfDay.set(java.util.Calendar.SECOND, 59)
        endOfDay.set(java.util.Calendar.MILLISECOND, 999)

        dietJob = lifecycleScope.launch {
            // 1. ดึงข้อมูลจาก Cloud มาลง Local ก่อนเพื่อให้ข้อมูลไม่หาย
            syncDataFromCloud(startOfDay.timeInMillis, endOfDay.timeInMillis)

            // 2. Observe Food Logs จาก Local
            launch {
                db.dietDao().getDietLogsForRange(startOfDay.timeInMillis, endOfDay.timeInMillis).collectLatest { logs ->
                    val totalCals = logs.sumOf { it.calories }
                    val goalCalories = resources.getInteger(R.integer.goal_calories)

                    tvEaten.text = totalCals.toString()
                    
                    if (totalCals > goalCalories) {
                        val over = totalCals - goalCalories
                        tvKcalLeft.text = over.toString()
                        view.findViewById<TextView>(R.id.tvKcalLeftLabel)?.text = getString(R.string.label_kcal_over)
                        progressKcalLeft.setIndicatorColor(Color.RED)
                    } else {
                        val kcalLeft = goalCalories - totalCals
                        tvKcalLeft.text = kcalLeft.toString()
                        view.findViewById<TextView>(R.id.tvKcalLeftLabel)?.text = getString(R.string.label_kcal_left)
                        progressKcalLeft.setIndicatorColor(Color.parseColor("#CDDC39"))
                    }

                    progressEaten.progress    = totalCals.coerceAtMost(goalCalories)
                    progressKcalLeft.progress = if (totalCals > goalCalories) 
                        (totalCals - goalCalories).coerceAtMost(goalCalories)
                    else 
                        (goalCalories - totalCals)

                    renderMealItems(llBreakfast, logs.filter { it.mealType == "Breakfast" })
                    renderMealItems(llLunch,     logs.filter { it.mealType == "Lunch" })
                    renderMealItems(llDinner,    logs.filter { it.mealType == "Dinner" })
                    renderMealItems(llSnack,     logs.filter { it.mealType == "Snack" })
                }
            }

            // 3. Observe Water Logs จาก Local
            launch {
                db.waterDao().getAllWaterLogs().collectLatest {
                    val totalWater = db.waterDao().getTotalWaterByDay(startOfDay.timeInMillis, endOfDay.timeInMillis) ?: 0
                    tvWaterAmount.text = getString(R.string.format_water_amount, totalWater)
                }
            }
        }
    }

    private suspend fun syncDataFromCloud(start: Long, end: Long) {
        try {
            // ดึง Diet จาก Cloud
            val cloudDiet = SupabaseManager.getDietLogsFromCloud(start, end)
            cloudDiet.forEach { log -> db.dietDao().insertDietLog(log) }

            // ดึง Water จาก Cloud
            val cloudWater = SupabaseManager.getWaterLogsFromCloud(start, end)
            cloudWater.forEach { log -> db.waterDao().insertWaterLog(log) }
        } catch (e: Exception) {
            android.util.Log.e("SyncCloud", "Sync error: ${e.message}")
        }
    }

    private fun renderMealItems(container: LinearLayout, logs: List<DietLog>) {
        container.removeAllViews()
        logs.forEach { log ->
            val itemView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_meal_food, container, false)
            itemView.findViewById<TextView>(R.id.tvMealFoodName).text = log.foodName
            itemView.findViewById<TextView>(R.id.tvMealFoodMacros).text =
                "P: ${log.protein}g | F: ${log.fat}g | C: ${log.carb}g"
            itemView.findViewById<TextView>(R.id.tvMealFoodCal).text =
                getString(R.string.format_kcal, log.calories)

            itemView.findViewById<View>(R.id.btnDeleteMealItem).setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete Item")
                    .setMessage("Are you sure you want to delete ${log.foodName}?")
                    .setPositiveButton("Delete") { _, _ ->
                        deleteDietLog(log)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            container.addView(itemView)
        }
    }

    private fun deleteDietLog(log: DietLog) {
        lifecycleScope.launch {
            // Delete from Local
            db.dietDao().deleteById(log.id)
            // Delete from Cloud
            SupabaseManager.deleteDietLogFromCloud(log.id)
        }
    }
}