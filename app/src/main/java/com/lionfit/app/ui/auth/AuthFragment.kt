package com.lionfit.app.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.lionfit.app.R
import com.lionfit.app.data.database.SupabaseManager
import com.lionfit.app.data.model.UserProfile
import com.lionfit.app.MainActivity
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.app.DatePickerDialog
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import java.util.Calendar
import java.util.Locale

class AuthFragment : Fragment(R.layout.fragment_auth) {

    private var isLoginMode = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etEmail = view.findViewById<EditText>(R.id.etEmail)
        val etPassword = view.findViewById<EditText>(R.id.etPassword)
        val btnSubmit = view.findViewById<MaterialButton>(R.id.btnSubmitAuth)
        val tvToggle = view.findViewById<TextView>(R.id.tvToggleMode)
        val tvTitle = view.findViewById<TextView>(R.id.tvAuthTitle)

        // The new UI elements
        val llSignUpFields = view.findViewById<LinearLayout>(R.id.llSignUpFields)
        val etFullName = view.findViewById<EditText>(R.id.etFullName)
        val etWeight = view.findViewById<EditText>(R.id.etWeight)
        val etHeight = view.findViewById<EditText>(R.id.etHeight)
        val etSignupDob = view.findViewById<EditText>(R.id.et_signup_dob)

        // Toggle UI logic
        tvToggle.setOnClickListener {
            // The Animation
            android.transition.TransitionManager.beginDelayedTransition(view as android.view.ViewGroup, android.transition.AutoTransition())

            isLoginMode = !isLoginMode

            if (isLoginMode) {
                tvTitle.text = "Sign In"
                btnSubmit.text = "Sign In"
                tvToggle.text = "Don't have an account? Sign Up"
                llSignUpFields.visibility = View.GONE // Hide extra fields
            } else {
                tvTitle.text = "Create an account"
                btnSubmit.text = "Sign Up"
                tvToggle.text = "Already have an account? Sign In"
                llSignUpFields.visibility = View.VISIBLE // Show extra fields
            }
        }

        // Date of birth field
        etSignupDob.setOnClickListener {
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
                etSignupDob.setText(formattedDate)
            }

            datePicker.show(parentFragmentManager, "BIRTHDATE_PICKER")
        }

        btnSubmit.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // 1. Check if empty
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Email and Password required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 2. Strict Email Format Check
            val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[a-zA-Z]{2,6}$".toRegex()
            if (!email.matches(emailRegex)) {
                Toast.makeText(requireContext(), "Please enter a valid email format (e.g., name@email.com)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Password Length Check (Supabase requires 6+ chars)
            if (password.length < 6) {
                Toast.makeText(requireContext(), "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSubmit.isEnabled = false

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val auth = SupabaseManager.client.auth

                    if (isLoginMode) {
                        // --- STEP 1: LOG IN ---
                        auth.signInWith(Email) {
                            this.email = email
                            this.password = password
                        }
                    } else {
                        // --- STEP 1: SIGN UP ---
                        val name = etFullName.text.toString().trim()
                        val weight = etWeight.text.toString().toDoubleOrNull() ?: 0.0
                        val height = etHeight.text.toString().toDoubleOrNull() ?: 0.0
                        val dob = etSignupDob.text.toString()

                        // Check if Name or DOB is missing
                        if (name.isEmpty() || dob.isEmpty()) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(requireContext(), "Please provide your full name and date of birth", Toast.LENGTH_SHORT).show()
                                btnSubmit.isEnabled = true
                            }
                            return@launch
                        }

                        // Realistic biometrics check for accurate BMR/TDEE
                        if (weight !in 20.0..400.0 || height !in 50.0..300.0) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(requireContext(), "Please enter realistic weight and height measurements", Toast.LENGTH_LONG).show()
                                btnSubmit.isEnabled = true
                            }
                            return@launch
                        }

                        // Create the secure auth account
                        auth.signUpWith(Email) {
                            this.email = email
                            this.password = password
                        }

                        // Get the newly generated secure ID
                        val newUserId = auth.currentUserOrNull()?.id ?: throw Exception("Failed to get User ID")

                        // --- STEP 2: SAVE TO PROFILES TABLE ---
                        val userProfile = UserProfile(
                            id = newUserId,
                            fullName = name,
                            birthDate = dob,
                            email = email,
                            weightKg = weight,
                            heightCm = height
                        )

                        SupabaseManager.client.postgrest["profiles"].insert(userProfile)
                    }

                    // Success
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Welcome!", Toast.LENGTH_SHORT).show()
                        val mainActivity = requireActivity() as MainActivity

                        mainActivity.findViewById<BottomNavigationView>(R.id.bottom_navigation).visibility = View.VISIBLE
                        mainActivity.findViewById<View>(R.id.top_bar)?.visibility = View.VISIBLE
                        (requireActivity() as MainActivity).refreshTopBarProfile()
                        (requireActivity() as MainActivity).switchFragment("dashboard")
                    }

                } catch (e: Exception) {
                    // Log the real error to the console ONLY if we are in Debug mode
                    if (com.lionfit.app.BuildConfig.DEBUG) {
                        android.util.Log.e("AuthFragment", "Supabase Login Error: ${e.localizedMessage}")
                    }

                    // Show a safe, friendly message to the user
                    withContext(Dispatchers.Main) {
                        val safeErrorMessage = if (isLoginMode) {
                            "Login failed. Please check your email and password."
                        } else {
                            "Sign up failed. This email might already be in use."
                        }

                        Toast.makeText(requireContext(), safeErrorMessage, Toast.LENGTH_LONG).show()
                        btnSubmit.isEnabled = true
                    }
                }
            }
        }
    }
}