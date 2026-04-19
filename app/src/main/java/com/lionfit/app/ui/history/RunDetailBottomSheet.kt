package com.lionfit.app.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.load
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lionfit.app.R
import com.lionfit.app.data.model.RunSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RunDetailBottomSheet(
    private val runSession: RunSession,
    private val onEditClicked: (RunSession) -> Unit,
    private val onShareClicked: (RunSession) -> Unit,
    private val onDeleteClicked: (RunSession) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_run_detail_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Grab the views
        val ivMap = view.findViewById<ImageView>(R.id.iv_sheet_map)
        val tvTitle = view.findViewById<TextView>(R.id.tv_sheet_title)
        val tvDate = view.findViewById<TextView>(R.id.tv_sheet_date)

        val btnEdit = view.findViewById<LinearLayout>(R.id.btn_sheet_edit)
        val btnShare = view.findViewById<LinearLayout>(R.id.btn_sheet_share)
        val btnDelete = view.findViewById<LinearLayout>(R.id.btn_sheet_delete)

        // Populate the data
        tvTitle.text = runSession.title

        val sdf = SimpleDateFormat("EEEE, MMM d, yyyy 'at' hh:mm a", Locale.getDefault())
        tvDate.text = sdf.format(Date(runSession.timestamp))

        // Load the map screenshot using Coil
        if (runSession.mapSnapshotUrl != null) {
            ivMap.load(runSession.mapSnapshotUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_image_placeholder)
                error(R.drawable.ic_image_placeholder)
            }
        } else {
            ivMap.setImageResource(R.drawable.ic_image_placeholder)
        }

        // Wire up the buttons to talk back to the History Fragment
        btnEdit.setOnClickListener {
            onEditClicked(runSession)
            dismiss() // Close the bottom sheet after clicking
        }

        btnShare.setOnClickListener {
            onShareClicked(runSession)
            dismiss()
        }

        btnDelete.setOnClickListener {
            onDeleteClicked(runSession)
            dismiss()
        }
    }
}