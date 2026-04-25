// ProfileFragment.kt
package com.lionfit.app.ui.profile

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.lionfit.app.R
import com.lionfit.app.data.database.SupabaseManager
import com.lionfit.app.data.model.UserProfile
import com.lionfit.app.MainActivity
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.InputStream
import io.github.jan.supabase.gotrue.auth
import androidx.fragment.app.activityViewModels
import coil.load
import com.lionfit.app.ui.shared.SharedViewModel
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private var isEditing = false
    private var currentProfile: UserProfile? = null
    private var newProfilePictureBytes: ByteArray? = null

    // Layout views
    private lateinit var llDisplayMode: LinearLayout
    private lateinit var llEditMode: LinearLayout
    private lateinit var ivProfilePicture: ImageView

    // Display fields
    private lateinit var tvNameDisplay: TextView
    private lateinit var tvEmailDisplay: TextView
    private lateinit var tvPhoneDisplay: TextView
    private lateinit var tvBirthDateDisplay: TextView
    private lateinit var tvGenderDisplay: TextView
    private lateinit var etGenderEdit: AutoCompleteTextView
    private lateinit var tvHeightDisplay: TextView
    private lateinit var tvWeightDisplay: TextView
    private lateinit var btnEditOrSave: Button
    private lateinit var btnCancelOrLogout: Button

    // Edit fields
    private lateinit var etNameEdit: EditText
    private lateinit var etPhoneEdit: EditText
    private lateinit var etBirthDateEdit: EditText
    private lateinit var etHeightEdit: EditText
    private lateinit var etWeightEdit: EditText
    private lateinit var btnChangePic: Button
    private lateinit var btnWeightUp: ImageButton
    private lateinit var btnWeightDown: ImageButton

    // ViewModel
    private val sharedViewModel: SharedViewModel by activityViewModels()

    // Contract to pick an image from the user's gallery
    private val pickImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val imageUri: Uri? = result.data?.data
            if (imageUri != null) {
                // 1. Visually update the ImageView so the user sees their new picture
                ivProfilePicture.setImageURI(imageUri)

                // 2. Prepare the data for upload by compressing it into a ByteArray
                lifecycleScope.launch {
                    newProfilePictureBytes = compressUriToByteArray(imageUri)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- View Binding & Initialization ---
        initializeViews(view)

        // --- Fetch initial data from Supabase ---
        lifecycleScope.launch {
            val currentUserId = SupabaseManager.client.auth.currentUserOrNull()?.id ?: return@launch
            currentProfile = SupabaseManager.getProfile(currentUserId)

            if (currentProfile != null) {
                bindProfileData(currentProfile!!)
            } else {
                Toast.makeText(requireContext(), "Failed to load profile", Toast.LENGTH_SHORT).show()
            }
        }

        // --- Button Click Listeners ---
        setupButtonListeners()
    }

    private fun initializeViews(view: View) {
        llDisplayMode = view.findViewById(R.id.llDisplayMode)
        llEditMode = view.findViewById(R.id.llEditMode)
        ivProfilePicture = view.findViewById(R.id.ivProfilePicture)

        tvNameDisplay = view.findViewById(R.id.tvNameDisplay)
        tvEmailDisplay = view.findViewById(R.id.tvEmailDisplay)
        tvPhoneDisplay = view.findViewById(R.id.tvPhoneDisplay)
        tvBirthDateDisplay = view.findViewById(R.id.tvBirthDateDisplay)
        tvGenderDisplay = view.findViewById(R.id.tvGenderDisplay)
        tvHeightDisplay = view.findViewById(R.id.tvHeightDisplay)
        tvWeightDisplay = view.findViewById(R.id.tvWeightDisplay)

        etNameEdit = view.findViewById(R.id.etNameEdit)
        etPhoneEdit = view.findViewById(R.id.etPhoneEdit)
        etBirthDateEdit = view.findViewById(R.id.et_dob)
        etGenderEdit = view.findViewById(R.id.et_gender)
        etHeightEdit = view.findViewById(R.id.etHeightEdit)
        etWeightEdit = view.findViewById(R.id.etWeightEdit)

        btnChangePic = view.findViewById(R.id.btnChangePic)
        btnEditOrSave = view.findViewById(R.id.btnEditOrSave)
        btnCancelOrLogout = view.findViewById(R.id.btnCancelOrLogout)

        btnWeightUp = view.findViewById(R.id.btnWeightUp)
        btnWeightDown = view.findViewById(R.id.btnWeightDown)

        val genderOptions = arrayOf("Male", "Female", "Other", "Prefer not to say")
        val dropdownAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            genderOptions
        )
        etGenderEdit.setAdapter(dropdownAdapter)
    }

    private fun setupButtonListeners() {
        // Weight up / down button
        btnWeightUp.setOnClickListener { adjustWeight(0.5) }
        btnWeightDown.setOnClickListener { adjustWeight(-0.5) }
        ivProfilePicture.setOnClickListener {
            if (!isEditing) {
                toggleEditingMode(true)
                val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                pickImage.launch(intent)
            }
        }

        // Master button (toggles between "Edit Profile" and "Save")
        btnEditOrSave.setOnClickListener {
            if (!isEditing) {
                toggleEditingMode(true)
            } else {
                saveProfileChanges()
            }
        }

        // Secondary button (toggles between "Log Out" and "Cancel")
        btnCancelOrLogout.setOnClickListener {
            if (isEditing) {
                // Cancel: Toggle back to display mode without saving
                toggleEditingMode(false)
                newProfilePictureBytes = null // Reset the pending image
                currentProfile?.let { bindProfileData(it) } // Re-bind the original data
            } else {
                // Log Out
                handleLogOut()
            }
        }

        // Select picture button (only visible in editing mode)
        btnChangePic.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImage.launch(intent)
        }

        etBirthDateEdit.setOnClickListener {
            // Prevent them from picking a birthdate in the future
            val constraintsBuilder = CalendarConstraints.Builder()
                .setValidator(DateValidatorPointBackward.now())

            // Build the LionFit Branded Picker
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Date of Birth")
                .setTheme(R.style.Theme_LionFit_DatePicker)
                .setCalendarConstraints(constraintsBuilder.build())
                .build()

            // Handle the result
            datePicker.addOnPositiveButtonClickListener { selection ->
                // Convert UTC selection to the PostgreSQL format (YYYY-MM-DD)
                val utcCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                utcCal.timeInMillis = selection

                val formattedDate = String.format(
                    java.util.Locale.getDefault(),
                    "%04d-%02d-%02d",
                    utcCal.get(java.util.Calendar.YEAR),
                    utcCal.get(java.util.Calendar.MONTH) + 1, // Calendar months are 0-indexed
                    utcCal.get(java.util.Calendar.DAY_OF_MONTH)
                )
                etBirthDateEdit.setText(formattedDate)
            }

            datePicker.show(parentFragmentManager, "BIRTHDATE_PICKER")
        }
    }

    private fun toggleEditingMode(editing: Boolean) {
        android.transition.TransitionManager.beginDelayedTransition(requireView() as android.view.ViewGroup, android.transition.AutoTransition())
        isEditing = editing
        if (editing) {
            // Switch to Edit Mode UI
            llDisplayMode.visibility = View.GONE
            llEditMode.visibility = View.VISIBLE
            btnChangePic.visibility = View.VISIBLE
            btnEditOrSave.text = "Save"
            btnCancelOrLogout.text = "Cancel"

            // Populate the Edit fields with current data
            etNameEdit.setText(currentProfile?.fullName ?: "")
            etPhoneEdit.setText(currentProfile?.phoneNumber ?: "")
            etBirthDateEdit.setText(currentProfile?.birthDate ?: "")
            etGenderEdit.setText(currentProfile?.gender ?: "", false)
            etHeightEdit.setText(currentProfile?.heightCm?.toString() ?: "0.0")
            etWeightEdit.setText(currentProfile?.weightKg?.toString() ?: "0.0")

        } else {
            // Switch to Display Mode UI
            llDisplayMode.visibility = View.VISIBLE
            llEditMode.visibility = View.GONE
            btnChangePic.visibility = View.GONE
            btnEditOrSave.text = "Edit Profile"
            btnCancelOrLogout.text = "Log Out"
        }
    }

    private fun bindProfileData(profile: UserProfile) {
        tvNameDisplay.text = profile.fullName
        tvEmailDisplay.text = profile.email ?: "Email not provided"
        tvPhoneDisplay.text = "Phone Number: ${profile.phoneNumber ?: "---"}"
        tvBirthDateDisplay.text = "Birth of date: ${profile.birthDate ?: "---"}"
        tvGenderDisplay.text = "Gender: ${profile.gender ?: "---"}"
        tvHeightDisplay.text = "${profile.heightCm} cm"
        tvWeightDisplay.text = "${profile.weightKg} kg"

        if (profile.profilePicUrl != null) {
            ivProfilePicture.load(profile.profilePicUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_profile_placeholder)
                error(R.drawable.ic_profile_placeholder)
            }
        } else {
            ivProfilePicture.setImageResource(R.drawable.ic_profile_placeholder)
        }
    }

    private fun saveProfileChanges() {
        if (currentProfile == null) return

        btnEditOrSave.isEnabled = false // Disable to prevent double-clicks

        lifecycleScope.launch {
            try {
                var finalPicUrl = currentProfile!!.profilePicUrl

                // If the user selected a new picture, upload it first
                if (newProfilePictureBytes != null) {
                    val rawUrl = SupabaseManager.uploadProfilePicture(currentProfile!!.id, newProfilePictureBytes!!)
                    finalPicUrl = "$rawUrl?t=${System.currentTimeMillis()}"
                }

                // Build the final UserProfile object with the new UI data
                val updatedProfile = currentProfile!!.copy(
                    fullName = etNameEdit.text.toString().trim(),
                    phoneNumber = etPhoneEdit.text.toString().trim(),
                    birthDate = etBirthDateEdit.text.toString().trim(),
                    gender = etGenderEdit.text.toString().trim(),
                    heightCm = etHeightEdit.text.toString().toDoubleOrNull() ?: 0.0,
                    weightKg = etWeightEdit.text.toString().toDoubleOrNull() ?: 0.0,
                    profilePicUrl = finalPicUrl
                )

                // Upload to Supabase
                SupabaseManager.updateProfile(updatedProfile)

                // Success
                currentProfile = updatedProfile
                bindProfileData(currentProfile!!)
                toggleEditingMode(false) // Toggle back to display mode
                newProfilePictureBytes = null // Reset the pending image
                sharedViewModel.updatedProfilePicUrl.value = finalPicUrl
                sharedViewModel.profileUpdatedSignal.postValue(System.currentTimeMillis())

                Toast.makeText(requireContext(), "Profile Updated!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to save: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                btnEditOrSave.isEnabled = true // Re-enable button
            }
        }
    }

    /**
     * Instantly updates the UI and silently syncs the new weight to Supabase.
     */
    private fun adjustWeight(delta: Double) {
        // Safety check: Make sure we actually have a profile loaded
        val profile = currentProfile ?: return

        // Calculate new weight
        val newWeight = Math.round((profile.weightKg + delta) * 10.0) / 10.0

        // Prevent negative or zero weight
        if (newWeight <= 0) return

        // Optimistic UI Update
        currentProfile = profile.copy(weightKg = newWeight)
        tvWeightDisplay.text = "$newWeight kg"
        etWeightEdit.setText(newWeight.toString()) // Keep the edit form in sync just in case they click Edit later

        // Background Cloud Sync
        lifecycleScope.launch {
            try {
                // Instantly save it to Supabase
                SupabaseManager.updateProfile(currentProfile!!)
                // Tell the rest of the app that weight is officially saved
                sharedViewModel.profileUpdatedSignal.postValue(System.currentTimeMillis())

            } catch (e: Exception) {
                // If the internet fails, revert the UI back to the original safe weight
                currentProfile = profile
                tvWeightDisplay.text = "${profile.weightKg} kg"
                etWeightEdit.setText(profile.weightKg.toString())
                Toast.makeText(requireContext(), "No internet. Weight not saved.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleLogOut() {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Nuke the local database
                    val db = com.lionfit.app.data.database.AppDatabase.getDatabase(requireContext())
                    db.clearAllTables()

                    // Sign out of the cloud session
                    SupabaseManager.logOutUser()
                }

                // Back on the Main UI Thread automatically for screen navigation
                val intent = Intent(requireActivity(), MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)

            } catch (e: Exception) {
                e.printStackTrace()
                // We are already back on the Main thread here if it crashes, so Toast is safe!
                android.widget.Toast.makeText(requireContext(), "Logout failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Helper function to turn a URI into a ByteArray for efficient network upload
    private fun compressUriToByteArray(uri: Uri): ByteArray {
        val inputStream: InputStream? = requireContext().contentResolver.openInputStream(uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        val outputStream = ByteArrayOutputStream()
        originalBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream) // Compress to 70% quality for network efficiency
        return outputStream.toByteArray()
    }
}