package com.lionfit.app.ui.diet

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.lionfit.app.R
import com.lionfit.app.data.database.AppDatabase
import com.lionfit.app.data.model.DietLog
import kotlinx.coroutines.launch

class AddFoodFragment : Fragment(R.layout.fragment_add_food) {

    private val db by lazy { AppDatabase.getDatabase(requireContext()) }
    private lateinit var mealType: String

    // Nutrition summary for selected meal
    private var totalCal = 0; private var totalF = 0
    private var totalC = 0;   private var totalP = 0

    companion object {
        fun newInstance(mealType: String) = AddFoodFragment().apply {
            arguments = Bundle().apply { putString("mealType", mealType) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mealType = arguments?.getString("mealType") ?: "Breakfast"

        // Header title = meal type
        view.findViewById<TextView>(R.id.tvMealTitle).text = mealType

        // Macro chips
        updateMacroChips(view)

        // Search
        val searchView = view.findViewById<SearchView>(R.id.searchFood)
        val rvResults  = view.findViewById<RecyclerView>(R.id.rvFoodResults)
        rvResults.layoutManager = LinearLayoutManager(requireContext())

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchFood(query.orEmpty(), rvResults, view)
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                if ((newText?.length ?: 0) >= 2) searchFood(newText.orEmpty(), rvResults, view)
                return true
            }
        })

        // Back
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun searchFood(query: String, rv: RecyclerView, view: View) {
        // TODO: เชื่อม API ค้นหาอาหาร (เช่น Open Food Facts)
        // ตอนนี้ใช้ mock data
        val mockResults = listOf(
            FoodResult("Pad Krapow with Beef", 347, 29f, 4f, 18f, "151.5g / 1 serving"),
            FoodResult("Khao Man Gai", 420, 32f, 45f, 12f, "200g / 1 serving"),
            FoodResult("Som Tum", 120, 5f, 18f, 3f, "150g / 1 serving")
        ).filter { it.name.contains(query, ignoreCase = true) }

        rv.adapter = FoodResultAdapter(mockResults) { food ->
            addFoodToLog(food, view)
        }
    }

    private fun addFoodToLog(food: FoodResult, view: View) {
        lifecycleScope.launch {
            db.dietDao().insertDietLog(
                DietLog(
                    foodName  = food.name,
                    mealType  = mealType,
                    calories  = food.calories,
                    protein   = food.protein,
                    carbs     = food.carbs,
                    fat       = food.fat,
                    dateLogged = System.currentTimeMillis()
                )
            )
            totalCal += food.calories; totalF += food.fat.toInt()
            totalC   += food.carbs.toInt(); totalP += food.protein.toInt()
            updateMacroChips(view)
        }
    }

    private fun updateMacroChips(view: View) {
        view.findViewById<Chip>(R.id.chipCal)?.text = getString(R.string.chip_cal, totalCal)
        view.findViewById<Chip>(R.id.chipF)?.text   = getString(R.string.chip_fat, totalF)
        view.findViewById<Chip>(R.id.chipC)?.text   = getString(R.string.chip_carb, totalC)
        view.findViewById<Chip>(R.id.chipP)?.text   = getString(R.string.chip_protein, totalP)
    }
}

data class FoodResult(
    val name: String,
    val calories: Int,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val serving: String
)