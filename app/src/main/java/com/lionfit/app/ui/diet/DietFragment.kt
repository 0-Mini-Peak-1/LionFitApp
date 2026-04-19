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
import com.lionfit.app.R
import com.lionfit.app.data.database.AppDatabase
import com.lionfit.app.data.model.DietLog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DietFragment : Fragment(R.layout.fragment_diet) {

    private val db by lazy { AppDatabase.getDatabase(requireContext()) }

    private val prefs by lazy {
        requireContext().getSharedPreferences("diet_prefs", Context.MODE_PRIVATE)
    }
    private var currentWaterMl = 0

    private var selectedDate = java.util.Calendar.getInstance()
    private var availableDates = setOf<Long>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentWaterMl = prefs.getInt("water_ml", 0)
        view.findViewById<TextView>(R.id.tvWaterAmount).text =
            getString(R.string.format_water_amount, currentWaterMl)

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
                currentWaterMl += addedMl
                prefs.edit().putInt("water_ml", currentWaterMl).apply()
                view.findViewById<TextView>(R.id.tvWaterAmount).text =
                    getString(R.string.format_water_amount, currentWaterMl)
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
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, AddFoodFragment.newInstance(mealType))
                    .addToBackStack(null)
                    .commit()
            }
        }

        observeDietData(view)
    }

    private fun showDatePicker(view: View) {
        val dpd = android.app.DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                selectedDate.set(year, month, dayOfMonth)
                updateDateDisplay(view)
                observeDietData(view)
            },
            selectedDate.get(java.util.Calendar.YEAR),
            selectedDate.get(java.util.Calendar.MONTH),
            selectedDate.get(java.util.Calendar.DAY_OF_MONTH)
        )
        dpd.datePicker.maxDate = System.currentTimeMillis()
        dpd.show()
    }

    private fun hasDataOnDate(cal: java.util.Calendar): Boolean {
        // เทียบเฉพาะวันที่ (ตัดเวลาออก)
        val dateMillis = (cal.timeInMillis / 86400000) * 86400000
        return availableDates.contains(dateMillis) || isSameDay(cal, java.util.Calendar.getInstance())
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
            db.dietDao().getDietLogsForRange(startOfDay.timeInMillis, endOfDay.timeInMillis).collectLatest { logs ->
                val totalCals = logs.sumOf { it.calories }
                val kcalLeft  = (goalCalories - totalCals).coerceAtLeast(0)

                tvEaten.text    = totalCals.toString()
                tvKcalLeft.text = if (totalCals > goalCalories)
                    getString(R.string.label_kcal_over)
                else
                    getString(R.string.format_kcal, kcalLeft)

                progressEaten.progress    = totalCals.coerceAtMost(goalCalories)
                progressKcalLeft.progress = kcalLeft

                renderMealItems(llBreakfast, logs.filter { it.mealType == "Breakfast" })
                renderMealItems(llLunch,     logs.filter { it.mealType == "Lunch" })
                renderMealItems(llDinner,    logs.filter { it.mealType == "Dinner" })
                renderMealItems(llSnack,     logs.filter { it.mealType == "Snack" })
            }
        }
    }

    private fun renderMealItems(container: LinearLayout, logs: List<DietLog>) {
        container.removeAllViews()
        logs.forEach { log ->
            val itemView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_meal_food, container, false)
            itemView.findViewById<TextView>(R.id.tvMealFoodName).text = log.foodName
            itemView.findViewById<TextView>(R.id.tvMealFoodCal).text =
                getString(R.string.format_kcal, log.calories)
            container.addView(itemView)
        }
    }
}