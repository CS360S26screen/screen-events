package com.example.se_proj

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import com.example.se_proj.databinding.ActivityMainParkingBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore

class MainParkingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainParkingBinding
    private val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainParkingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupDrawer()
        setupBottomNavigation()
        setupParkingCounter()

        binding.btnParkingPlus.setOnClickListener { updateParking(1) }
        binding.btnParkingMinus.setOnClickListener { updateParking(-1) }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    private fun setupDrawer() {
        binding.drawerNavigation.setCheckedItem(R.id.drawer_main_parking)
        binding.drawerNavigation.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.drawer_in_gate -> {
                    startActivity(Intent(this, GuardDashboardActivity::class.java))
                    finish()
                    true
                }
                R.id.drawer_out_gate -> {
                    startActivity(
                        Intent(this, GuardDashboardActivity::class.java)
                            .putExtra(GuardDashboardActivity.EXTRA_GATE_MODE, GuardDashboardActivity.MODE_OUT_GATE)
                    )
                    finish()
                    true
                }
                R.id.drawer_main_parking -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_home
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, GuardDashboardActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_logs -> {
                    startActivity(Intent(this, AdminAuditActivity::class.java))
                    true
                }
                R.id.nav_adhoc -> {
                    startActivity(Intent(this, WalkInRegistrationActivity::class.java))
                    true
                }
                R.id.nav_settings -> {
                    Toast.makeText(this, "Settings not available yet", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupParkingCounter() {
        db.collection("system_metadata").document("parking_status")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val occupancy = snapshot.getLong("currentOccupancy") ?: 0
                val capacity = snapshot.getLong("maxCapacity") ?: 200
                binding.tvParkingCounter.text = "$occupancy / $capacity"

                binding.pbParking.max = capacity.toInt()
                binding.pbParking.progress = occupancy.toInt()

                val ratio = occupancy.toFloat() / capacity
                val color = when {
                    ratio > 0.9f -> R.color.status_denied_text
                    ratio > 0.7f -> android.R.color.holo_orange_dark
                    else -> R.color.primary_purple
                }
                binding.pbParking.setIndicatorColor(ContextCompat.getColor(this, color))
            }
    }

    private fun updateParking(delta: Long) {
        db.collection("system_metadata").document("parking_status")
            .update("currentOccupancy", FieldValue.increment(delta))
    }
}
