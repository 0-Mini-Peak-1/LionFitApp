package com.lionfit.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.lionfit.app.data.database.SupabaseManager
import io.github.jan.supabase.gotrue.auth
import androidx.appcompat.app.AppCompatDelegate
import android.widget.ImageView
import androidx.activity.viewModels
import coil.load
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.lionfit.app.ui.shared.SharedViewModel

// Import the actual fragments
import com.lionfit.app.ui.running.RunningFragment
import com.lionfit.app.ui.running.SaveActivityFragment
import com.lionfit.app.ui.history.RunHistoryFragment
import com.lionfit.app.ui.auth.AuthFragment
import com.lionfit.app.ui.dashboard.DashboardFragment
import com.lionfit.app.ui.profile.ProfileFragment
import com.lionfit.app.ui.diet.DietFragment
import com.lionfit.app.ui.sleep.SleepFragment

class MainActivity : AppCompatActivity() {

    private val sharedViewModel: SharedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO) // Force light mode
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = true // Force black status bar icons

        val topProfileBtn = findViewById<View>(R.id.card_top_profile)
        val topBar = findViewById<View>(R.id.top_bar)
        val topProfileImageView = findViewById<ImageView>(R.id.iv_top_profile_pic)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        sharedViewModel.updatedProfilePicUrl.observe(this) { newUrl ->
            if (newUrl != null) {
                topProfileImageView.load(newUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_profile_placeholder)
                    error(R.drawable.ic_profile_placeholder)
                }
            } else {
                topProfileImageView.setImageResource(R.drawable.ic_profile_placeholder)
            }
        }

        lifecycleScope.launch {
            SupabaseManager.client.auth.awaitInitialization()
            val currentUser = SupabaseManager.client.auth.currentUserOrNull()

            if (currentUser != null) {
                // User is logged in
                bottomNav.visibility = View.VISIBLE
                topBar?.visibility = View.VISIBLE
                refreshTopBarProfile()
                switchFragment("dashboard")
            } else {
                // User not logged in
                bottomNav.visibility = View.GONE
                topBar?.visibility = View.GONE
                switchFragment("auth")
            }
        }

        // Listen for user taps on the bottom navigation menu
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> switchFragment("dashboard")
                R.id.nav_diet -> switchFragment("diet")
                R.id.nav_running -> switchFragment("running")
                R.id.nav_sleep -> switchFragment("sleep")
                R.id.nav_profile -> switchFragment("profile")
            }
            true
        }

        topProfileBtn.setOnClickListener {
            switchFragment("profile")
            bottomNav.selectedItemId = R.id.nav_profile
        }
    }

    // A public tool to force the top bar to update its picture
    fun refreshTopBarProfile() {
        val currentUser = SupabaseManager.client.auth.currentUserOrNull()
        if (currentUser != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val profile = SupabaseManager.getProfile(currentUser.id)
                    withContext(Dispatchers.Main) {
                        if (profile != null && !profile.profilePicUrl.isNullOrEmpty()) {
                            sharedViewModel.updatedProfilePicUrl.value = profile.profilePicUrl
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

   // Helper Functions
   fun switchFragment(targetTag: String) {
       val transaction = supportFragmentManager.beginTransaction()
       val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
       val currentVisibleTag = supportFragmentManager.fragments.firstOrNull { it.isVisible }?.tag

       // Run History Animation
       if (targetTag == "run_history") {
           transaction.setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.fade_out)
       } else if (currentVisibleTag == "run_history" && targetTag == "running") {
           transaction.setCustomAnimations(android.R.anim.fade_in, android.R.anim.slide_out_right)
       }

       supportFragmentManager.fragments.forEach { fragment ->
           transaction.hide(fragment)
       }

       var targetFragment = supportFragmentManager.findFragmentByTag(targetTag)

       if (targetFragment != null) {
           transaction.show(targetFragment)
       } else {
           targetFragment = when (targetTag) {
               "dashboard" -> DashboardFragment()
               "diet" -> DietFragment()
               "running" -> RunningFragment()
               "sleep" -> SleepFragment()
               "profile" -> ProfileFragment()
               "save_activity" -> SaveActivityFragment()
               "auth" -> AuthFragment()
               "run_history" -> RunHistoryFragment()
               else -> DashboardFragment()
           }
           transaction.add(R.id.fragment_container, targetFragment, targetTag)
       }

       when (targetTag) {
           "dashboard" -> bottomNavigationView.menu.findItem(R.id.nav_dashboard)?.isChecked = true
           "diet" -> bottomNavigationView.menu.findItem(R.id.nav_diet)?.isChecked = true
           "running" -> bottomNavigationView.menu.findItem(R.id.nav_running)?.isChecked = true
           "sleep" -> bottomNavigationView.menu.findItem(R.id.nav_sleep)?.isChecked = true
           "profile" -> bottomNavigationView.menu.findItem(R.id.nav_profile)?.isChecked = true
       }

       transaction.commit()
   }
}

// --- PLACEHOLDER FRAGMENTS ---
//class SleepFragment : Fragment(R.layout.fragment_placeholder)