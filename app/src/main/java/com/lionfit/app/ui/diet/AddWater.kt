package com.lionfit.app.ui.diet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.lionfit.app.R

class AddWaterBottomSheet(
    private val onWaterAdded: (Int) -> Unit
) : BottomSheetDialogFragment() {

    private var currentAmount = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.add_water, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvAmount = view.findViewById<TextView>(R.id.tvWaterAmount)
        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupWater)
        val btnAdd = view.findViewById<Button>(R.id.btnAddWaterConfirm)

        fun updateDisplay() {
            tvAmount.text = getString(R.string.format_ml, currentAmount)
        }

        // Preset chips
        val presets = listOf(200, 250, 300, 350, 500, 700, 1000)
        presets.forEach { ml ->
            val chip = Chip(requireContext()).apply {
                text = getString(R.string.format_ml, ml)
                isCheckable = true
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        currentAmount = ml
                        updateDisplay()
                    }
                }
            }
            chipGroup.addView(chip)
        }

        // Number pad
        val numPadIds = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3,
            R.id.btn4, R.id.btn5, R.id.btn6, R.id.btn7,
            R.id.btn8, R.id.btn9
        )
        numPadIds.forEachIndexed { index, id ->
            view.findViewById<Button>(id).setOnClickListener {
                chipGroup.clearCheck()
                currentAmount = (currentAmount.toString() + index.toString()).toIntOrNull() ?: 0
                updateDisplay()
            }
        }

        view.findViewById<Button>(R.id.btnBackspace).setOnClickListener {
            val str = currentAmount.toString()
            currentAmount = if (str.length > 1) str.dropLast(1).toInt() else 0
            updateDisplay()
        }

        btnAdd.setOnClickListener {
            if (currentAmount > 0) {
                onWaterAdded(currentAmount)
                dismiss()
            }
        }

        updateDisplay()
    }
}