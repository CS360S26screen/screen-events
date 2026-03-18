package com.example.se_proj

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.se_proj.databinding.ActivityGuardDashboardBinding
import com.example.se_proj.models.AuditLog
import com.example.se_proj.models.VisitorRequest
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GuardDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGuardDashboardBinding
    private val db = Firebase.firestore
    private var currentRequest: VisitorRequest? = null

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
    }

    private fun setupParkingCounter() {
        db.collection("system_metadata").document("parking_status")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val occupancy = snapshot.getLong("currentOccupancy") ?: 0
                val capacity = snapshot.getLong("maxCapacity") ?: 200
                binding.tvParkingCounter.text = "Parking: $occupancy / $capacity"
                
                binding.pbParking.max = capacity.toInt()
                binding.pbParking.progress = occupancy.toInt()
                
                // Color logic
                val ratio = occupancy.toFloat() / capacity
                val color = when {
                    ratio > 0.9 -> android.graphics.Color.RED
                    ratio > 0.7 -> android.graphics.Color.parseColor("#FFA500") // Orange
                    else -> android.graphics.Color.GREEN
                }
                binding.pbParking.progressTintList = android.content.res.ColorStateList.valueOf(color)
            }
    }

    private fun searchVisitorByCnic(cnic: String) {
        db.collection("visitor_requests")
            .whereEqualTo("guestCNIC", cnic)
            .whereEqualTo("status", "approved")
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    binding.cvResult.visibility = View.GONE
                    binding.tvEmptyState.visibility = View.VISIBLE
                } else {
                    binding.tvEmptyState.visibility = View.GONE
                    val doc = documents.documents[0]
                    val request = doc.toObject(VisitorRequest::class.java)
                    if (request != null) {
                        currentRequest = request
                        displayResult(request)
                    }
                }
            }
    }

    private fun searchCurrentVisitorsByHost(hostId: String) {
        db.collection("visitor_requests")
            .whereEqualTo("hostId", hostId)
            .whereEqualTo("onCampus", true)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    binding.cvResult.visibility = View.GONE
                    binding.tvEmptyState.visibility = View.VISIBLE
                } else {
                    binding.tvEmptyState.visibility = View.GONE
                    val doc = documents.documents[0]
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
        binding.tvTimeWindow.text = "${request.startTime} - ${request.endTime}"

        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        binding.btnOverride.visibility = View.GONE

        if (request.onCampus) {
            binding.tvStatus.text = "Currently On Campus"
            binding.btnAction.text = "Check-Out"
            binding.btnAction.isEnabled = true
        } else {
            if (currentTime < request.startTime) {
                binding.tvStatus.text = "Early Arrival - Blocked"
                binding.btnAction.text = "Check-In"
                binding.btnAction.isEnabled = false
                binding.btnOverride.visibility = View.VISIBLE
                logAudit(request, "Denied", "Early Arrival")
            } else if (currentTime > request.endTime) {
                binding.tvStatus.text = "Visit Window Expired"
                binding.btnAction.text = "Check-In"
                binding.btnAction.isEnabled = false
                binding.btnOverride.visibility = View.VISIBLE
                logAudit(request, "Denied", "Expired Window")
            } else {
                binding.tvStatus.text = "Authorized for Entry"
                binding.btnAction.text = "Check-In"
                binding.btnAction.isEnabled = true
            }
        }
    }

    private fun checkIn(request: VisitorRequest) {
        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val updates = mapOf(
            "entryTime" to currentTime,
            "onCampus" to true
        )

        db.collection("visitor_requests").document(request.requestId)
            .update(updates)
            .addOnSuccessListener {
                logAudit(request, "Entry")
                updateParking(1)
                Toast.makeText(this, "Checked In successfully", Toast.LENGTH_SHORT).show()
                searchVisitorByCnic(request.guestCNIC)
            }
    }

    private fun checkOut(request: VisitorRequest) {
        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val updates = mapOf(
            "exitTime" to currentTime,
            "onCampus" to false
        )

        db.collection("visitor_requests").document(request.requestId)
            .update(updates)
            .addOnSuccessListener {
                logAudit(request, "Exit")
                updateParking(-1)
                Toast.makeText(this, "Checked Out successfully", Toast.LENGTH_SHORT).show()
                searchVisitorByCnic(request.guestCNIC)
            }
    }

    private fun manualOverride(request: VisitorRequest) {
        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val updates = mapOf(
            "entryTime" to currentTime,
            "onCampus" to true
        )

        db.collection("visitor_requests").document(request.requestId)
            .update(updates)
            .addOnSuccessListener {
                logAudit(request, "Entry", "Special Permission (Supervisor Override)")
                updateParking(1)
                Toast.makeText(this, "Manual Override: Checked In", Toast.LENGTH_SHORT).show()
                searchVisitorByCnic(request.guestCNIC)
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
}
