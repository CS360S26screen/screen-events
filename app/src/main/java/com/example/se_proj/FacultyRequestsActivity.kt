package com.example.se_proj

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.se_proj.adapters.FacultyRequestAdapter
import com.example.se_proj.databinding.ActivityFacultyRequestsBinding
import com.example.se_proj.models.VisitorRequest
import com.example.se_proj.rules.RequestStatus
import com.example.se_proj.rules.UserProfileUtils
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import java.util.Calendar
import java.util.Locale

class FacultyRequestsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFacultyRequestsBinding
    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()
    private lateinit var adapter: FacultyRequestAdapter
    private var facultyId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFacultyRequestsBinding.inflate(layoutInflater)
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

        setupRecyclerView()
        loadUserProfile()
    }

    private fun setupRecyclerView() {
        adapter = FacultyRequestAdapter(
            requests = emptyList(),
            onEditClick = { request ->
                if (RequestStatus.canEdit(request.status)) {
                    showEditOptions(request)
                } else {
                    Toast.makeText(this, "Cannot edit processed requests", Toast.LENGTH_SHORT).show()
                }
            },
            onCancelClick = { request ->
                if (RequestStatus.canCancel(request.status, request.onCampus)) {
                    showCancelConfirmation(request)
                } else {
                    Toast.makeText(this, "Only active off-campus requests can be cancelled", Toast.LENGTH_SHORT).show()
                }
            }
        )
        binding.rvRequests.layoutManager = LinearLayoutManager(this)
        binding.rvRequests.adapter = adapter
    }

    private fun loadUserProfile() {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrEmpty()) {
            Toast.makeText(this, "Please log in again to view your requests", Toast.LENGTH_LONG).show()
            return
        }

        db.collection("Users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "User profile not found", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                facultyId = UserProfileUtils.resolveHostId(
                    doc.getString("rollNumber"),
                    doc.getString("facultyId"),
                    doc.id
                )
                fetchMyRequests()
            }
    }

    private fun fetchMyRequests() {
        if (facultyId.isEmpty()) return
        db.collection("visitor_requests")
            .whereEqualTo("hostId", facultyId)
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener
                val list = snapshots?.documents?.mapNotNull { doc ->
                    doc.toObject(VisitorRequest::class.java)?.let { request ->
                        if (request.requestId.isEmpty()) request.copy(requestId = doc.id) else request
                    }
                } ?: emptyList()
                adapter.updateData(list)
            }
    }

    private fun showEditOptions(request: VisitorRequest) {
        val options = arrayOf("Change Visit Date", "Change Start Time")
        AlertDialog.Builder(this)
            .setTitle("Edit Request")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showDatePicker(request)
                    1 -> showTimePicker(request)
                }
            }
            .show()
    }

    private fun showDatePicker(request: VisitorRequest) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, dayOfMonth ->
            val newDate = String.format(Locale.getDefault(), "%02d/%02d/%d", dayOfMonth, month + 1, year)
            updateRequestField(request.requestId, "visitDate", newDate)
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showTimePicker(request: VisitorRequest) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(this, { _, hourOfDay, minute ->
            val newStartTime = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
            updateRequestField(request.requestId, "startTime", newStartTime)
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
    }

    private fun updateRequestField(requestId: String, field: String, value: String) {
        if (requestId.isEmpty()) {
            Toast.makeText(this, "Cannot update: request ID missing", Toast.LENGTH_SHORT).show()
            return
        }
        db.collection("visitor_requests").document(requestId)
            .update(field, value)
            .addOnSuccessListener {
                Toast.makeText(this, "$field updated", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showCancelConfirmation(request: VisitorRequest) {
        if (request.requestId.isEmpty()) {
            Toast.makeText(this, "Cannot cancel: request ID missing", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Cancel Request")
            .setMessage("Are you sure you want to cancel the request for ${request.guestName}?")
            .setPositiveButton("Yes, Cancel") { _, _ ->
                db.collection("visitor_requests").document(request.requestId)
                    .update("status", RequestStatus.CANCELLED)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Request cancelled", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("No", null)
            .show()
    }
}
