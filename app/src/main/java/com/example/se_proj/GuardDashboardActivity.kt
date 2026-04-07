package com.example.se_proj

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
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

    companion object {
        const val EXTRA_GATE_MODE = "gate_mode"
        const val MODE_IN_GATE = "in_gate"
        const val MODE_OUT_GATE = "out_gate"
    }

    private lateinit var binding: ActivityGuardDashboardBinding
    private val db = Firebase.firestore
    private var currentRequest: VisitorRequest? = null
    private var searchListener: ListenerRegistration? = null
    private var gateMode: String = MODE_IN_GATE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGuardDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        gateMode = intent.getStringExtra(EXTRA_GATE_MODE) ?: MODE_IN_GATE

        setupToolbar()
        setupDrawer()
        setupBottomNavigation()
        setupScreenMode()

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

        binding.btnAction.setOnClickListener {
            currentRequest?.let { request ->
                val action = if (shouldCheckOut(request)) "Check-Out" else "Check-In"
                AlertDialog.Builder(this)
                    .setTitle("Confirm Action")
                    .setMessage("Are you sure you want to $action ${request.guestName}?")
                    .setPositiveButton("Yes") { _, _ ->
                        if (shouldCheckOut(request)) checkOut(request) else checkIn(request)
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

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    private fun setupDrawer() {
        binding.drawerNavigation.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.drawer_in_gate -> {
                    openGateMode(MODE_IN_GATE)
                    true
                }
                R.id.drawer_out_gate -> {
                    openGateMode(MODE_OUT_GATE)
                    true
                }
                R.id.drawer_main_parking -> {
                    startActivity(Intent(this, MainParkingActivity::class.java))
                    finish()
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
                    if (gateMode != MODE_IN_GATE) {
                        openGateMode(MODE_IN_GATE)
                    }
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

    private fun setupScreenMode() {
        val isOutGate = gateMode == MODE_OUT_GATE
        binding.toolbar.title = if (isOutGate) "Out-Gate - Live Dashboard" else "In-Gate - Live Dashboard"
        binding.drawerNavigation.setCheckedItem(
            if (isOutGate) R.id.drawer_out_gate else R.id.drawer_in_gate
        )
        binding.btnAction.text = if (isOutGate) "Check-Out" else "Check-In"
    }

    private fun openGateMode(mode: String) {
        if (gateMode == mode) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            return
        }
        startActivity(
            Intent(this, GuardDashboardActivity::class.java)
                .putExtra(EXTRA_GATE_MODE, mode)
        )
        finish()
    }

    private fun shouldCheckOut(request: VisitorRequest): Boolean {
        return gateMode == MODE_OUT_GATE || request.onCampus
    }

    private fun searchVisitorByCnic(cnic: String) {
        searchListener?.remove()
        val query = db.collection("visitor_requests")
            .whereEqualTo("guestCNIC", cnic)
            .let {
                if (gateMode == MODE_OUT_GATE) {
                    it.whereEqualTo("onCampus", true)
                } else {
                    it.whereEqualTo("status", "approved")
                }
            }
        searchListener = query
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener
                if (snapshots == null || snapshots.isEmpty) {
                    binding.cvResult.visibility = View.GONE
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.tvEmptyState.text = if (gateMode == MODE_OUT_GATE) {
                        "No checked-in guest found for this CNIC."
                    } else {
                        "No approved request found for this CNIC."
                    }
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

        if (gateMode == MODE_OUT_GATE) {
            binding.btnOverride.visibility = View.GONE
            setChipStatus("INSIDE", R.color.status_approved_bg, R.color.status_approved_text)
            binding.tvStatus.text = "Currently On Campus"
            binding.btnAction.text = "Check-Out"
            binding.btnAction.isEnabled = true
            return
        }

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
