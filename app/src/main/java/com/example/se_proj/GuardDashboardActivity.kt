package com.example.se_proj

import android.util.Log
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.se_proj.adapters.VisitorRequestAdapter
import com.example.se_proj.databinding.ActivityGuardDashboardBinding
import com.example.se_proj.models.AuditLog
import com.example.se_proj.models.VisitorRequest
import com.example.se_proj.rules.ParkingOccupancyUtils
import com.example.se_proj.rules.RequestStatus
import com.example.se_proj.rules.VisitWindowEvaluator
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.FirebaseFirestoreException
import java.time.LocalDate
import java.time.LocalTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gate-operations dashboard used by guards to search visitors, evaluate entry windows,
 * perform check-in/check-out actions, and maintain parking occupancy.
 *
 * Design note: applies the strategy result from `VisitWindowEvaluator` to drive UI state,
 * and uses transactional updates for occupancy changes.
 *
 * Outstanding issues: denied-access logging is triggered during result rendering and may be
 * duplicated on repeated searches; idempotent logging guards should be added.
 */
class GuardDashboardActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GATE_MODE = "gate_mode"
        const val MODE_IN_GATE = "in_gate"
        const val MODE_OUT_GATE = "out_gate"
    }

    private lateinit var binding: ActivityGuardDashboardBinding
    private val db = Firebase.firestore
    private lateinit var eventsAdapter: VisitorRequestAdapter
    private var currentTab: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGuardDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_logout -> {
                    FirebaseAuth.getInstance().signOut()
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                    true
                }
                else -> false
            }
        }

        ensureParkingDocument()
        setupParkingCounter()
        setupEventsRecycler()
        setupTabs()
        fetchEventsForCurrentTab()

        binding.fabAddWalkIn.setOnClickListener {
            startActivity(Intent(this, WalkInRegistrationActivity::class.java))
        }

        binding.btnCheckIn.setOnClickListener { updateParking(1) }
        binding.btnCheckOut.setOnClickListener { updateParking(-1) }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                fetchEventsForCurrentTab()
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) = Unit
        })
    }

    private fun setupEventsRecycler() {
        eventsAdapter = VisitorRequestAdapter(
            requests = emptyList(),
            onApproveClick = { request ->
                if (request.onCampus) {
                    checkOut(request)
                } else {
                    handleGateDecision(request)
                }
            },
            onRejectClick = { request ->
                if (!request.onCampus) {
                    AlertDialog.Builder(this)
                        .setTitle("Supervisor Override")
                        .setMessage("Manually override entry restrictions for ${request.guestName}?")
                        .setPositiveButton("Confirm Override") { _, _ ->
                            manualOverride(request)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    Toast.makeText(this, "Guest already on campus", Toast.LENGTH_SHORT).show()
                }
            }
        )

        binding.rvEvents.layoutManager = LinearLayoutManager(this)
        binding.rvEvents.adapter = eventsAdapter
    }

    private fun fetchEventsForCurrentTab() {
        val query = when (currentTab) {
            0 -> db.collection("visitor_requests").whereEqualTo("status", RequestStatus.APPROVED)
            1 -> db.collection("visitor_requests").whereEqualTo("onCampus", true)
            else -> db.collection("visitor_requests").whereEqualTo("status", RequestStatus.DENIED)
        }

        query.get()
            .addOnSuccessListener { snapshots ->
                val list = snapshots.documents.mapNotNull { doc ->
                    doc.toObject(VisitorRequest::class.java)?.let { request ->
                        if (request.requestId.isEmpty()) request.copy(requestId = doc.id) else request
                    }
                }
                eventsAdapter.updateData(list)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Unable to load events", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupParkingCounter() {
        val docRef = db.collection("system_metadata").document("parking_status")

        docRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.e("GuardDashboard", "Listen to parking failed", e)
                return@addSnapshotListener
            }
            
            val occupancy: Long
            val capacity: Long
            
            if (snapshot == null || !snapshot.exists()) {
                occupancy = 0L
                capacity = 200L
                binding.tvParkingCount.text = "0"
            } else {
                occupancy = snapshot.getLong("currentOccupancy") ?: 0L
                capacity = snapshot.getLong("maxCapacity") ?: 200L
                binding.tvParkingCount.text = occupancy.toString()
            }

            binding.pbParking.max = capacity.toInt()
            binding.pbParking.progress = occupancy.toInt()

            val ratio = ParkingOccupancyUtils.occupancyRatio(occupancy, capacity)
            val color = when {
                ratio > 0.9 -> R.color.status_denied_text
                ratio > 0.7 -> android.R.color.holo_orange_dark
                else -> R.color.primary_purple
            }
            binding.pbParking.setIndicatorColor(ContextCompat.getColor(this, color))
        }
    }

    private fun ensureParkingDocument() {
        val docRef = db.collection("system_metadata").document("parking_status")
        docRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                docRef.set(mapOf("currentOccupancy" to 0L, "maxCapacity" to 200L))
                    .addOnFailureListener { e ->
                        Log.e("GuardDashboard", "Failed to initialize parking document", e)
                    }
            }
        }
    }

    private fun handleGateDecision(request: VisitorRequest) {
        val decision = VisitWindowEvaluator.evaluate(request, LocalDate.now(), LocalTime.now())

        if (decision.isActionEnabled) {
            checkIn(request)
            Toast.makeText(this, decision.message, Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, decision.message, Toast.LENGTH_SHORT).show()

        if (decision.shouldLogDeniedAccess()) {
            logAudit(request, "Denied", decision.deniedReason)
        }
    }

    private fun checkIn(request: VisitorRequest) {
        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        db.collection("visitor_requests").document(request.requestId)
            .update(mapOf("entryTime" to currentTime, "onCampus" to true))
            .addOnSuccessListener {
                logAudit(request, "Entry")
                updateParking(1)
                Toast.makeText(this, "Checked In", Toast.LENGTH_SHORT).show()
            }
    }

    private fun checkOut(request: VisitorRequest) {
        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        db.collection("visitor_requests").document(request.requestId)
            .update(mapOf("exitTime" to currentTime, "onCampus" to false))
            .addOnSuccessListener {
                logAudit(request, "Exit")
                updateParking(-1)
                Toast.makeText(this, "Checked Out", Toast.LENGTH_SHORT).show()
            }
    }

    private fun manualOverride(request: VisitorRequest) {
        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        db.collection("visitor_requests").document(request.requestId)
            .update(mapOf("entryTime" to currentTime, "onCampus" to true))
            .addOnSuccessListener {
                logAudit(request, "Entry", "Supervisor Override")
                updateParking(1)
                Toast.makeText(this, "Override successful", Toast.LENGTH_SHORT).show()
            }
    }

    private fun logAudit(request: VisitorRequest, action: String, reason: String = "") {
        val log = AuditLog(
            visitorName = request.guestName,
            visitorCNIC = request.guestCNIC,
            hostId = request.hostId,
            action = action,
            reason = reason,
            creatorId = FirebaseAuth.getInstance().currentUser?.uid ?: "",
            timestamp = Timestamp.now()
        )
        db.collection("access_logs").add(log)
            .addOnFailureListener { e ->
                Log.e("GuardDashboard", "Audit logging failed: ${e.message}")
            }
    }

    private fun updateParking(delta: Long) {
        val documentRef = db.collection("system_metadata").document("parking_status")
        
        // Using a transaction to ensure atomic update and clamping
        db.runTransaction { transaction ->
            val snapshot = transaction.get(documentRef)
            
            val currentOccupancy = if (snapshot.exists()) snapshot.getLong("currentOccupancy") ?: 0L else 0L
            val maxCapacity = if (snapshot.exists()) snapshot.getLong("maxCapacity") ?: 200L else 200L
            
            val updatedOccupancy = ParkingOccupancyUtils.clampOccupancy(currentOccupancy, delta, maxCapacity)
            
            if (!snapshot.exists()) {
                transaction.set(documentRef, mapOf(
                    "currentOccupancy" to updatedOccupancy,
                    "maxCapacity" to maxCapacity
                ))
            } else {
                transaction.update(documentRef, "currentOccupancy", updatedOccupancy)
            }
            null
        }.addOnSuccessListener {
            Log.d("GuardDashboard", "Parking updated successfully")
        }.addOnFailureListener { e ->
            Log.e("GuardDashboard", "Parking update failed", e)
            val errorMsg = if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                "Permission Denied: Only Admins can update parking settings."
            } else {
                "Unable to update parking: ${e.message}"
            }
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
        }
    }
}
