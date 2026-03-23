package com.example.se_proj

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.se_proj.databinding.ActivityGuardDashboardBinding
import com.example.se_proj.models.AuditLog
import com.example.se_proj.models.VisitorRequest
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GuardDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGuardDashboardBinding
    private val db = Firebase.firestore
    private var currentRequest: VisitorRequest? = null
    private var searchListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGuardDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupParkingCounter()

        binding.btnSearchCnic.setOnClickListener {
            val cnic = binding.etSearchCnic.text.toString().trim()
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

        binding.btnWalkIn.setOnClickListener {
            startActivity(Intent(this, WalkInRegistrationActivity::class.java))
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

        binding.btnParkingPlus.setOnClickListener { updateParking(1) }
        binding.btnParkingMinus.setOnClickListener { updateParking(-1) }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_logs -> {
                    startActivity(Intent(this, AdminAuditActivity::class.java))
                    false
                }
                else -> true
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
                    ratio > 0.9 -> R.color.status_denied_text
                    ratio > 0.7 -> android.R.color.holo_orange_dark
                    else -> R.color.primary_purple
                }
                binding.pbParking.setIndicatorColor(ContextCompat.getColor(this, color))
            }
    }

    private fun searchVisitorByCnic(cnic: String) {
        searchListener?.remove()
        searchListener = db.collection("visitor_requests")
            .whereEqualTo("guestCNIC", cnic)
            .whereEqualTo("status", "approved")
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener
                if (snapshots == null || snapshots.isEmpty) {
                    binding.cvResult.visibility = View.GONE
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.tvEmptyState.text = "No approved request found for this CNIC."
                } else {
                    binding.tvEmptyState.visibility = View.GONE
                    val doc = snapshots.documents[0]
                    val request = doc.toObject(VisitorRequest::class.java)
                    if (request != null) {
                        currentRequest = request
                        displayResult(request)
                    }
                }
            }
    }

    private fun searchCurrentVisitorsByHost(hostId: String) {
        searchListener?.remove()
        searchListener = db.collection("visitor_requests")
            .whereEqualTo("hostId", hostId)
            .whereEqualTo("onCampus", true)
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener
                if (snapshots == null || snapshots.isEmpty) {
                    binding.cvResult.visibility = View.GONE
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.tvEmptyState.text = "No guests currently on campus for this Host ID."
                } else {
                    binding.tvEmptyState.visibility = View.GONE
                    val doc = snapshots.documents[0]
                    val request = doc.toObject(VisitorRequest::class.java)
                    if (request != null) {
                        currentRequest = request
                        displayResult(request)
                    }
                }
            }
    }

    private fun displayResult(request: VisitorRequest) {
        binding.cvResult.visibility = View.VISIBLE
        binding.tvGuestName.text = request.guestName
        binding.tvHostInfo.text = "Host ID: ${request.hostId} (${request.hostType})"
        binding.tvTimeWindow.text = "${request.visitDate} | ${request.startTime} - ${request.endTime}"

        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val currentDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

        binding.btnOverride.visibility = View.GONE

        if (request.onCampus) {
            setChipStatus("INSIDE", R.color.status_approved_bg, R.color.status_approved_text)
            binding.tvStatus.text = "Currently On Campus"
            binding.btnAction.text = "Check-Out"
            binding.btnAction.isEnabled = true
        } else {
            if (request.visitDate != currentDate) {
                setChipStatus("WRONG DATE", R.color.status_denied_bg, R.color.status_denied_text)
                binding.tvStatus.text = "Visit scheduled for ${request.visitDate}"
                binding.btnAction.text = "Check-In"
                binding.btnAction.isEnabled = false
                binding.btnOverride.visibility = View.VISIBLE
                logAudit(request, "Denied", "Wrong Date")
            } else if (currentTime < request.startTime) {
                setChipStatus("TOO EARLY", R.color.status_denied_bg, R.color.status_denied_text)
                binding.tvStatus.text = "Entry allowed after ${request.startTime}"
                binding.btnAction.text = "Check-In"
                binding.btnAction.isEnabled = false
                binding.btnOverride.visibility = View.VISIBLE
                logAudit(request, "Denied", "Early Arrival")
            } else if (currentTime > request.endTime) {
                setChipStatus("EXPIRED", R.color.status_denied_bg, R.color.status_denied_text)
                binding.tvStatus.text = "Visit window expired at ${request.endTime}"
                binding.btnAction.text = "Check-In"
                binding.btnAction.isEnabled = false
                binding.btnOverride.visibility = View.VISIBLE
                logAudit(request, "Denied", "Expired Window")
            } else {
                setChipStatus("AUTHORIZED", R.color.status_approved_bg, R.color.status_approved_text)
                binding.tvStatus.text = "Authorized for Entry"
                binding.btnAction.text = "Check-In"
                binding.btnAction.isEnabled = true
            }
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
            timestamp = Timestamp.now()
        )
        db.collection("access_logs").add(log)
    }

    private fun updateParking(delta: Long) {
        db.collection("system_metadata").document("parking_status")
            .update("currentOccupancy", FieldValue.increment(delta))
    }

    override fun onDestroy() {
        super.onDestroy()
        searchListener?.remove()
    }
}
