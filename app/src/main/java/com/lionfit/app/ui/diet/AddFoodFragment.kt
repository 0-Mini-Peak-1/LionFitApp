package com.lionfit.app.ui.diet

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
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
import com.lionfit.app.data.model.FoodItem
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.launch

class AddFoodFragment : Fragment(R.layout.fragment_add_food) {

    private val db by lazy { AppDatabase.getDatabase(requireContext()) }
    private lateinit var mealType: String
    private var selectedDateMillis: Long = System.currentTimeMillis()
    private var totalCal = 0
    private var totalFat = 0.0
    private var totalCarb = 0.0
    private var totalProtein = 0.0

    private var allFoodResults = mutableListOf<FoodResult>()
    private var currentPage = 0
    private val pageSize = 10

    companion object {
        fun newInstance(mealType: String, dateMillis: Long) = AddFoodFragment().apply {
            arguments = Bundle().apply { 
                putString("mealType", mealType)
                putLong("selectedDate", dateMillis)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mealType = arguments?.getString("mealType") ?: "Breakfast"
        selectedDateMillis = arguments?.getLong("selectedDate") ?: System.currentTimeMillis()

        view.findViewById<TextView>(R.id.tvMealTitle).text = mealType
        updateSummary(view)

        val searchView = view.findViewById<SearchView>(R.id.searchFood)
        val rvResults  = view.findViewById<RecyclerView>(R.id.rvFoodResults)
        rvResults.layoutManager = LinearLayoutManager(requireContext())

        // Load data from Supabase foods table
        loadFoodsFromCloud(rvResults, view)

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                filterAndDisplay(query.orEmpty(), rvResults, view)
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                filterAndDisplay(newText.orEmpty(), rvResults, view)
                return true
            }
        })

        // Manual Add Button (+)
        view.findViewById<View>(R.id.btnAddManual).setOnClickListener {
            showAddMenuDialog(rvResults, view)
        }

        // Pagination
        view.findViewById<Button>(R.id.btnPrevPage).setOnClickListener {
            if (currentPage > 0) {
                currentPage--
                displayCurrentPage(rvResults, view)
            }
        }
        view.findViewById<Button>(R.id.btnNextPage).setOnClickListener {
            if ((currentPage + 1) * pageSize < filteredResults.size) {
                currentPage++
                displayCurrentPage(rvResults, view)
            }
        }

        // Done button — Save all and go back
        view.findViewById<View>(R.id.btnDone).setOnClickListener {
            saveAllSelectedFood()
        }
    }

    private var filteredResults = listOf<FoodResult>()

    private fun loadFoodsFromCloud(rv: RecyclerView, view: View) {
        lifecycleScope.launch {
            val cloudFoods = SupabaseManager.getAllFoods()
            allFoodResults = cloudFoods.map {
                FoodResult(it.name, it.calories, it.serving_size, it.protein.toDouble(), it.fat.toDouble(), it.carb.toDouble())
            }.toMutableList()
            
            // If cloud is empty, use defaults
            if (allFoodResults.isEmpty()) {
                allFoodResults = mutableListOf(
                    FoodResult("ผัดกะเพราเนื้อ", 347, "1 จาน / 151.5 กรัม", 18.0, 15.0, 35.0),
                    FoodResult("ข้าวมันไก่", 420, "1 จาน / 200 กรัม", 20.0, 22.0, 45.0),
                    FoodResult("ส้มตำไทย", 120, "1 จาน / 150 กรัม", 3.0, 2.0, 25.0),
                    FoodResult("ต้มยำกุ้ง", 210, "1 ถ้วย / 250 กรัม", 15.0, 8.0, 12.0),
                    FoodResult("แกงเขียวหวานไก่", 380, "1 ถ้วย / 200 กรัม", 14.0, 25.0, 15.0),
                    FoodResult("ข้าวผัดปู", 450, "1 จาน", 15.0, 18.0, 55.0),
                    FoodResult("ผัดไทยกุ้งสด", 480, "1 จาน", 12.0, 20.0, 65.0),
                    FoodResult("ข้าวไข่เจียว", 350, "1 จาน", 10.0, 22.0, 30.0),
                    FoodResult("อเมริกาโน่เย็น (ไม่หวาน)", 5, "1 แก้ว", 0.0, 0.0, 1.0),
                    FoodResult("ชาเขียวนมเย็น", 250, "1 แก้ว", 5.0, 10.0, 35.0),
                    FoodResult("ต้มจืดเต้าหู้หมูสับ", 150, "1 ถ้วย", 12.0, 8.0, 5.0),
                    FoodResult("สลัดผัก", 80, "1 จาน", 2.0, 1.0, 15.0)
                )
            }
            filteredResults = allFoodResults
            displayCurrentPage(rv, view)
        }
    }

    private fun filterAndDisplay(query: String, rv: RecyclerView, view: View) {
        filteredResults = allFoodResults.filter { it.name.contains(query, ignoreCase = true) }
        currentPage = 0
        displayCurrentPage(rv, view)
    }

    private fun displayCurrentPage(rv: RecyclerView, view: View) {
        val start = currentPage * pageSize
        val end = (start + pageSize).coerceAtMost(filteredResults.size)
        val pageItems = if (start < filteredResults.size) filteredResults.subList(start, end) else emptyList()
        
        rv.adapter = FoodResultAdapter(pageItems) {
            updateSummary(view)
        }
        
        view.findViewById<TextView>(R.id.tvPageNum).text = "Page ${currentPage + 1}"
    }

    private fun updateSummary(view: View) {
        totalCal = allFoodResults.sumOf { it.calories * it.selectedQuantity }
        totalFat = allFoodResults.sumOf { it.fat * it.selectedQuantity }
        totalCarb = allFoodResults.sumOf { it.carbs * it.selectedQuantity }
        totalProtein = allFoodResults.sumOf { it.protein * it.selectedQuantity }

        view.findViewById<Chip>(R.id.chipCal)?.text = "${totalCal} cal"
        view.findViewById<Chip>(R.id.chipFat)?.text = "F ${totalFat.toInt()}"
        view.findViewById<Chip>(R.id.chipCarb)?.text = "C ${totalCarb.toInt()}"
        view.findViewById<Chip>(R.id.chipProtein)?.text = "P ${totalProtein.toInt()}"
    }

    private fun showAddMenuDialog(rv: RecyclerView, view: View) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_custom_food, null)
        val etName = dialogView.findViewById<EditText>(R.id.etFoodName)
        val etCal = dialogView.findViewById<EditText>(R.id.etCalories)
        val etFat = dialogView.findViewById<EditText>(R.id.etFat)
        val etCarb = dialogView.findViewById<EditText>(R.id.etCarb)
        val etProtein = dialogView.findViewById<EditText>(R.id.etProtein)
        val etServing = dialogView.findViewById<EditText>(R.id.etServing)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveCustomFood)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        btnSave.setOnClickListener {
            val name = etName.text.toString()
            val cal = etCal.text.toString().toIntOrNull() ?: 0
            val fat = etFat.text.toString().toDoubleOrNull() ?: 0.0
            val carb = etCarb.text.toString().toDoubleOrNull() ?: 0.0
            val protein = etProtein.text.toString().toDoubleOrNull() ?: 0.0
            val serving = etServing.text.toString().ifEmpty { "1 serving" }

            if (name.isNotEmpty()) {
                val newFood = FoodResult(name, cal, serving, protein, fat, carb)
                allFoodResults.add(0, newFood)
                
                // Also save to Supabase foods table
                lifecycleScope.launch {
                    SupabaseManager.addNewFood(FoodItem(
                        name = name, calories = cal, fat = fat.toInt(), carb = carb.toInt(), protein = protein.toInt(), serving_size = serving
                    ))
                }
                
                filterAndDisplay("", rv, view)
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "Please enter food name", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun saveAllSelectedFood() {
        val currentUserId = SupabaseManager.client.auth.currentUserOrNull()?.id ?: "anonymous"

        lifecycleScope.launch {
            allFoodResults.filter { it.selectedQuantity > 0 }.forEach { food ->
                val dietLog = DietLog(
                    userId = currentUserId,
                    foodName = if (food.selectedQuantity > 1) "${food.name} (x${food.selectedQuantity})" else food.name,
                    mealType = mealType,
                    calories = food.calories * food.selectedQuantity,
                    protein = (food.protein * food.selectedQuantity).toInt(),
                    fat = (food.fat * food.selectedQuantity).toInt(),
                    carb = (food.carbs * food.selectedQuantity).toInt(),
                    dateLogged = selectedDateMillis
                )
                // Save to Local Room
                db.dietDao().insertDietLog(dietLog)

                // Sync to Cloud (Supabase)
                SupabaseManager.syncDietToCloud(dietLog)
            }
            parentFragmentManager.popBackStack()
        }
    }
}

data class FoodResult(
    val name: String,
    val calories: Int,
    val serving: String,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    var selectedQuantity: Int = 0
)
