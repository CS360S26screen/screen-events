package com.example.se_proj

import android.util.Log
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.content.ContextCompat
import com.example.se_proj.databinding.ActivityGuardDashboardBinding
import com.example.se_proj.models.AuditLog
import com.example.se_proj.models.VisitorRequest
import com.example.se_proj.rules.ParkingOccupancyUtils
import com.example.se_proj.rules.RequestStatus
import com.example.se_proj.rules.RequestValidationUtils
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
    private var currentRequest: VisitorRequest? = null

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

        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.drawerNavigation.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.drawer_in_gate -> binding.toolbar.title = "In-Gate - Live Dashboard"
                R.id.drawer_out_gate -> binding.toolbar.title = "Out-Gate - Live Dashboard"
                R.id.drawer_main_parking -> {
                    startActivity(Intent(this, MainParkingActivity::class.java))
                }
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_logs -> {
                    startActivity(Intent(this, AdminAuditActivity::class.java))
                    true
                }
                R.id.nav_adhoc -> {
                    startActivity(Intent(this, WalkInRegistrationActivity::class.java))
                    true
                }
                R.id.nav_settings -> {
                    Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }

        binding.btnSearchCnic.setOnClickListener {
            val cnic = RequestValidationUtils.normalizeCnic(binding.etSearchCnic.text.toString())
            if (cnic.isNotEmpty()) {
                searchVisitorByCnic(cnic)
            } else {
                Toast.makeText(this, "Please enter CNIC", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSearchRoll.setOnClickListener {
            val roll = binding.etSearchRoll.text.toString().trim()
            if (roll.isNotEmpty()) {
                searchCurrentVisitorsByHost(roll)
            } else {
                Toast.makeText(this, "Please enter Host Roll Number", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnAction.setOnClickListener {
            currentRequest?.let { request ->
                val action = if (request.onCampus) "Check-Out" else "Check-In"
                AlertDialog.Builder(this)
                    .setTitle("Confirm Action")
                    .setMessage("Are you sure you want to $action ${request.guestName}?")
                    .setPositiveButton("Yes") { _, _ ->
                        if (request.onCampus) checkOut(request) else checkIn(request)
                    }
                    .setNegativeButton("No", null)
                    .show()
            }
        }

        binding.btnOverride.setOnClickListener {
            currentRequest?.let { request ->
                AlertDialog.Builder(this)
                    .setTitle("Supervisor Override")
                    .setMessage("Manually override entry restrictions for ${request.guestName}?")
                    .setPositiveButton("Confirm Override") { _, _ ->
                        manualOverride(request)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun searchVisitorByCnic(cnic: String) {
        db.collection("visitor_requests")
            .whereEqualTo("guestCNIC", cnic)
            .whereEqualTo("status", RequestStatus.APPROVED)
            .get()
            .addOnSuccessListener { snapshots ->
                if (snapshots == null || snapshots.isEmpty) {
                    currentRequest = null
                    binding.cvResult.visibility = View.GONE
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.tvEmptyState.text = "No approved request found for this CNIC."
                } else {
                    binding.tvEmptyState.visibility = View.GONE
                    val doc = snapshots.documents[0]
                    val request = doc.toObject(VisitorRequest::class.java) ?: return@addOnSuccessListener
                    currentRequest = if (request.requestId.isEmpty()) request.copy(requestId = doc.id) else request
                    displayResult(currentRequest!!)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Unable to search visitor right now", Toast.LENGTH_SHORT).show()
            }
    }

    private fun searchCurrentVisitorsByHost(hostId: String) {
        db.collection("visitor_requests")
            .whereEqualTo("hostId", hostId)
            .whereEqualTo("onCampus", true)
            .get()
            .addOnSuccessListener { snapshots ->
                if (snapshots == null || snapshots.isEmpty) {
                    currentRequest = null
                    binding.cvResult.visibility = View.GONE
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.tvEmptyState.text = "No guests currently on campus for this Host ID."
                } else {
                    binding.tvEmptyState.visibility = View.GONE
                    val doc = snapshots.documents[0]
                    val request = doc.toObject(VisitorRequest::class.java) ?: return@addOnSuccessListener
                    currentRequest = if (request.requestId.isEmpty()) request.copy(requestId = doc.id) else request
                    displayResult(currentRequest!!)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Unable to search host records right now", Toast.LENGTH_SHORT).show()
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

    private fun displayResult(request: VisitorRequest) {
        binding.cvResult.visibility = View.VISIBLE
        binding.tvGuestName.text = request.guestName
        binding.tvHostInfo.text = "Host ID: ${request.hostId} (${request.hostType})"
        binding.tvTimeWindow.text = "${request.visitDate} | ${request.startTime} - ${request.endTime}"

        val decision = VisitWindowEvaluator.evaluate(request, LocalDate.now(), LocalTime.now())
        binding.tvStatus.text = decision.message
        binding.btnAction.text = decision.actionText
        binding.btnAction.isEnabled = decision.isActionEnabled
        binding.btnOverride.visibility = if (decision.isOverrideVisible) View.VISIBLE else View.GONE

        when (decision.state) {
            VisitWindowEvaluator.VisitWindowState.INSIDE,
            VisitWindowEvaluator.VisitWindowState.AUTHORIZED -> {
                setChipStatus(decision.label, R.color.status_approved_bg, R.color.status_approved_text)
            }
            else -> {
                setChipStatus(decision.label, R.color.status_denied_bg, R.color.status_denied_text)
            }
        }

        if (decision.shouldLogDeniedAccess()) {
            logAudit(request, "Denied", decision.deniedReason)
        }
    }

    private fun setChipStatus(text: String, bgColorRes: Int, textColorRes: Int) {
        binding.chipStatus.text = text
        binding.chipStatus.chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(this, bgColorRes))
        binding.chipStatus.setTextColor(ContextCompat.getColor(this, textColorRes))
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
