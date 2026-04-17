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

        // Toggle UI logic
        tvToggle.setOnClickListener {
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

        btnSubmit.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Email and Password required", Toast.LENGTH_SHORT).show()
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

                        if (name.isEmpty() || weight == 0.0 || height == 0.0) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(requireContext(), "Please fill in all profile fields correctly", Toast.LENGTH_SHORT).show()
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
                        // Replace "R.id.top_bar" with your actual top bar ID from activity_main.xml
                        mainActivity.findViewById<View>(R.id.top_bar)?.visibility = View.VISIBLE

                        mainActivity.switchFragment("dashboard")
                    }

                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), e.localizedMessage ?: "Action Failed", Toast.LENGTH_LONG).show()
                        btnSubmit.isEnabled = true
                    }
                }
            }
        }
    }
}