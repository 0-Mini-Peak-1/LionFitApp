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
import android.app.DatePickerDialog
import java.util.Calendar
import java.util.Locale
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView

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
            val calendar = Calendar.getInstance()
            val currentDobText = etBirthDateEdit.text.toString().trim()

            // Check if they already have a date saved
            if (currentDobText.isNotEmpty() && currentDobText.contains("-")) {
                try {
                    // Split "1995-08-24" into [1995, 08, 24]
                    val parts = currentDobText.split("-")
                    if (parts.size == 3) {
                        val parsedYear = parts[0].toInt()
                        val parsedMonth = parts[1].toInt() - 1 // Calendar months are 0-indexed (Jan = 0)
                        val parsedDay = parts[2].toInt()

                        // Set the calendar to the user's saved date
                        calendar.set(parsedYear, parsedMonth, parsedDay)
                    }
                } catch (e: Exception) {
                    // If the text was weirdly formatted, just fallback to 20 years ago
                    calendar.add(Calendar.YEAR, -20)
                }
            } else {
                // If the field is totally empty, default to 20 years ago
                calendar.add(Calendar.YEAR, -20)
            }

            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            val datePickerDialog = DatePickerDialog(
                requireContext(),
                { _, selectedYear, selectedMonth, selectedDay ->
                    // Keep the PostgreSQL standard format
                    val formattedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay)
                    etBirthDateEdit.setText(formattedDate)
                },
                year, month, day
            )

            datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
            datePickerDialog.show()
        }
    }

    private fun toggleEditingMode(editing: Boolean) {
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
            SupabaseManager.logOutUser() // Sign out of the session

            // Navigate the user back to the Auth screen by killing MainActivity and starting clean
            val intent = Intent(requireActivity(), MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish() // Kill the current MainActivity instance
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