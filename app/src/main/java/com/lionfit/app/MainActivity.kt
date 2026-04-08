package com.lionfit.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

// Import the actual fragments
import com.lionfit.app.ui.running.RunningFragment
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This links the Kotlin file to the XML layout
        setContentView(R.layout.activity_main)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = true // Force black status bar icons

        val topProfileBtn = findViewById<View>(R.id.card_top_profile)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        // Load the Dashboard as the default startup screen
        if (savedInstanceState == null) {
            switchFragment("dashboard")
        }

        topProfileBtn.setOnClickListener {
            switchFragment("profile")
            bottomNav.selectedItemId = R.id.nav_profile
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
    }

   // Helper Functions
   private fun switchFragment(targetTag: String) {
       val transaction = supportFragmentManager.beginTransaction()

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
               else -> DashboardFragment()
           }
           transaction.add(R.id.fragment_container, targetFragment, targetTag)
       }

       transaction.commit()
   }
}

// --- PLACEHOLDER FRAGMENTS ---
class DashboardFragment : Fragment(R.layout.fragment_placeholder)
class DietFragment : Fragment(R.layout.fragment_placeholder)
class ProfileFragment : Fragment(R.layout.fragment_placeholder)
class SleepFragment : Fragment(R.layout.fragment_placeholder)