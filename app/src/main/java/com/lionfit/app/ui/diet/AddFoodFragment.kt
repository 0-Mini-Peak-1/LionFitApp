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
import com.lionfit.app.data.database.SupabaseManager
import com.lionfit.app.data.model.DietLog
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.launch

class AddFoodFragment : Fragment(R.layout.fragment_add_food) {

    private val db by lazy { AppDatabase.getDatabase(requireContext()) }
    private lateinit var mealType: String
    private var totalCal = 0

    companion object {
        fun newInstance(mealType: String) = AddFoodFragment().apply {
            arguments = Bundle().apply { putString("mealType", mealType) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mealType = arguments?.getString("mealType") ?: "Breakfast"

        view.findViewById<TextView>(R.id.tvMealTitle).text = mealType
        updateCalChip(view)

        val searchView = view.findViewById<SearchView>(R.id.searchFood)
        val rvResults  = view.findViewById<RecyclerView>(R.id.rvFoodResults)
        rvResults.layoutManager = LinearLayoutManager(requireContext())

        // แสดงรายการอาหารทั้งหมดทันทีเมื่อเปิดหน้า
        searchFood("", rvResults, view)

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchFood(query.orEmpty(), rvResults, view)
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                // ค้นหาทันทีที่พิมพ์ หรือถ้าลบจนว่างก็ให้โชว์ทั้งหมด
                searchFood(newText.orEmpty(), rvResults, view)
                return true
            }
        })

        // Back button - Cancel (just pop back)
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Done button — Save all and go back
        view.findViewById<View>(R.id.btnDone).setOnClickListener {
            saveAllSelectedFood()
        }
    }

    private var allFoodResults = listOf<FoodResult>()

    private fun searchFood(query: String, rv: RecyclerView, view: View) {
        if (allFoodResults.isEmpty()) {
            allFoodResults = listOf(
                FoodResult("ผัดกะเพราเนื้อ", 347, "1 จาน / 151.5 กรัม"),
                FoodResult("ข้าวมันไก่", 420, "1 จาน / 200 กรัม"),
                FoodResult("ส้มตำไทย", 120, "1 จาน / 150 กรัม"),
                FoodResult("ต้มยำกุ้ง", 210, "1 ถ้วย / 250 กรัม"),
                FoodResult("แกงเขียวหวานไก่", 380, "1 ถ้วย / 200 กรัม"),
                FoodResult("ข้าวผัดปู", 450, "1 จาน"),
                FoodResult("ผัดไทยกุ้งสด", 480, "1 จาน"),
                FoodResult("ข้าวไข่เจียว", 350, "1 จาน"),
                FoodResult("อเมริกาโน่เย็น (ไม่หวาน)", 5, "1 แก้ว"),
                FoodResult("ชาเขียวนมเย็น", 250, "1 แก้ว")
            )
        }

        val filteredResults = allFoodResults.filter { it.name.contains(query, ignoreCase = true) }

        rv.adapter = FoodResultAdapter(filteredResults) {
            updateTotalCalories(view)
        }
    }

    private fun updateTotalCalories(view: View) {
        totalCal = allFoodResults.sumOf { it.calories * it.selectedQuantity }
        updateCalChip(view)
    }

    private fun saveAllSelectedFood() {
        val currentUserId = SupabaseManager.client.auth.currentUserOrNull()?.id ?: "anonymous"

        lifecycleScope.launch {
            allFoodResults.filter { it.selectedQuantity > 0 }.forEach { food ->
                val dietLog = DietLog(
                    userId = currentUserId,
                    foodName = "${food.name} (x${food.selectedQuantity})",
                    mealType = mealType,
                    calories = food.calories * food.selectedQuantity,
                    dateLogged = System.currentTimeMillis()
                )
                // Save to Local Room
                db.dietDao().insertDietLog(dietLog)

                // Sync to Cloud (Supabase)
                SupabaseManager.syncDietToCloud(dietLog)
            }
            parentFragmentManager.popBackStack()
        }
    }

    private fun updateCalChip(view: View) {
        view.findViewById<Chip>(R.id.chipCal)?.text = getString(R.string.chip_cal, totalCal)
    }
}

data class FoodResult(
    val name: String,
    val calories: Int,
    val serving: String,
    var selectedQuantity: Int = 0
)