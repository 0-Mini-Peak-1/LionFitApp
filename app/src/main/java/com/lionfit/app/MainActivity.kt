package com.lionfit.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

// Import the actual RunningFragment your team is building
import com.lionfit.app.ui.running.RunningFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This links the Kotlin file to the XML layout
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // Load the Dashboard as the default startup screen
        if (savedInstanceState == null) {
            replaceFragment(DashboardFragment())
        }

        // Listen for user taps on the bottom navigation menu
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> replaceFragment(DashboardFragment())
                R.id.nav_diet -> replaceFragment(DietFragment())
                R.id.nav_running -> replaceFragment(RunningFragment())
                R.id.nav_sleep -> replaceFragment(SleepFragment())
                R.id.nav_profile -> replaceFragment(ProfileFragment())
            }
            // Return true to indicate the tap was handled successfully
            true
        }
    }

    /**
     * A helper function that swaps the current screen with a new one
     * without launching a completely new Activity.
     */
    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}

// --- PLACEHOLDER FRAGMENTS ---
// Notice RunningFragment is gone from here!
// Keep these remaining three until your team creates their actual files.
class DashboardFragment : Fragment(R.layout.fragment_placeholder)
class DietFragment : Fragment(R.layout.fragment_placeholder)
class ProfileFragment : Fragment(R.layout.fragment_placeholder)
class SleepFragment : Fragment(R.layout.fragment_placeholder)