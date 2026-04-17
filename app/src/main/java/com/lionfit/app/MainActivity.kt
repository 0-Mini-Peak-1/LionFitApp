package com.lionfit.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.lionfit.app.data.database.SupabaseManager
import io.github.jan.supabase.gotrue.auth

// Import the actual fragments
import com.lionfit.app.ui.running.RunningFragment
import com.lionfit.app.ui.running.SaveActivityFragment
import com.lionfit.app.ui.auth.AuthFragment
import com.lionfit.app.ui.profile.ProfileFragment
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = true // Force black status bar icons

        val topProfileBtn = findViewById<View>(R.id.card_top_profile)
        val topBar = findViewById<View>(R.id.top_bar)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        lifecycleScope.launch {
            SupabaseManager.client.auth.awaitInitialization()
            val currentUser = SupabaseManager.client.auth.currentUserOrNull()

            if (currentUser != null) {
                // User is logged in
                bottomNav.visibility = View.VISIBLE
                topBar?.visibility = View.VISIBLE
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

   // Helper Functions
   fun switchFragment(targetTag: String) {
       val transaction = supportFragmentManager.beginTransaction()
       val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)

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
class DashboardFragment : Fragment(R.layout.fragment_placeholder)
class DietFragment : Fragment(R.layout.fragment_placeholder)
class SleepFragment : Fragment(R.layout.fragment_placeholder)