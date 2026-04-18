package com.lionfit.app.ui.diet

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.lionfit.app.R
import com.lionfit.app.data.database.AppDatabase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DietFragment : Fragment(R.layout.fragment_diet) {

    private val db by lazy { AppDatabase.getDatabase(requireContext()) }
    private var currentWaterMl = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Water button → show bottom sheet popup
        view.findViewById<View>(R.id.btnAddWater).setOnClickListener {
            AddWaterBottomSheet { addedMl ->
                currentWaterMl += addedMl
                view.findViewById<TextView>(R.id.tvWaterAmount).text =
                    getString(R.string.format_water_amount, currentWaterMl)
            }.show(parentFragmentManager, "AddWater")
        }

        // Meal buttons → open AddFoodFragment
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

    private fun observeDietData(view: View) {
        val tvEaten          = view.findViewById<TextView>(R.id.tvEaten)
        val tvKcalLeft       = view.findViewById<TextView>(R.id.tvKcalLeft)
        val progressEaten    = view.findViewById<CircularProgressIndicator>(R.id.progressEaten)
        val progressKcalLeft = view.findViewById<CircularProgressIndicator>(R.id.progressKcalLeft)
        val progressGoal     = view.findViewById<CircularProgressIndicator>(R.id.progressGoal)

        val goalCalories = resources.getInteger(R.integer.goal_calories)
        progressGoal.progress = goalCalories

        lifecycleScope.launch {
            db.dietDao().getAllDietLogs().collectLatest { logs ->
                val totalCals = logs.sumOf { it.calories }
                val kcalLeft  = (goalCalories - totalCals).coerceAtLeast(0)

                tvEaten.text    = totalCals.toString()
                tvKcalLeft.text = if (totalCals > goalCalories)
                    getString(R.string.label_kcal_over)
                else
                    getString(R.string.format_kcal, kcalLeft)

                progressEaten.progress    = totalCals.coerceAtMost(goalCalories)
                progressKcalLeft.progress = kcalLeft
            }
        }
    }
}