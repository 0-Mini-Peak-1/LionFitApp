package com.lionfit.app.ui.diet

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.lionfit.app.R
import com.lionfit.app.data.model.DietLog

class DietAdapter(private var dietList: List<DietLog>) : RecyclerView.Adapter<DietAdapter.DietViewHolder>() {

    class DietViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvFoodName: TextView = view.findViewById(R.id.tvFoodName)
        val tvMealType: TextView = view.findViewById(R.id.tvMealType)
        val tvCalories: TextView = view.findViewById(R.id.tvCalories)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DietViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_diet_log, parent, false)
        return DietViewHolder(view)
    }

    override fun onBindViewHolder(holder: DietViewHolder, position: Int) {
        val log = dietList[position]
        holder.tvFoodName.text = log.foodName
        holder.tvMealType.text = log.mealType
        // ใช้ string resource แทน hardcoded string
        holder.tvCalories.text = holder.itemView.context.getString(R.string.format_kcal, log.calories)
    }

    override fun getItemCount() = dietList.size

    fun updateData(newList: List<DietLog>) {
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = dietList.size
            override fun getNewListSize() = newList.size

            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                return dietList[oldPos].id == newList[newPos].id
            }

            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                return dietList[oldPos] == newList[newPos]
            }
        })
        dietList = newList
        diffResult.dispatchUpdatesTo(this)
    }
}