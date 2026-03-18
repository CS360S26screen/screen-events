package com.example.se_proj

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.se_proj.adapters.FacultyRequestAdapter
import com.example.se_proj.databinding.ActivityFacultyRequestsBinding
import com.example.se_proj.models.VisitorRequest
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import java.util.Calendar
import java.util.Locale

class FacultyRequestsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFacultyRequestsBinding
    private val db = Firebase.firestore
    private lateinit var adapter: FacultyRequestAdapter
    private val facultyId = "FAC-123" // Should come from session/auth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFacultyRequestsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        fetchMyRequests()
    }

    private fun setupRecyclerView() {
        adapter = FacultyRequestAdapter(
            requests = emptyList(),
            onEditClick = { request -> 
                if (request.status == "pending" || request.status == "approved") {
                    showEditOptions(request)
                } else {
                    Toast.makeText(this, "Cannot edit processed requests", Toast.LENGTH_SHORT).show()
                }
            },
            onCancelClick = { request -> 
                if (!request.onCampus) {
                    showCancelConfirmation(request)
                } else {
                    Toast.makeText(this, "Guest is already on campus", Toast.LENGTH_SHORT).show()
                }
            }
        )
        binding.rvRequests.layoutManager = LinearLayoutManager(this)
        binding.rvRequests.adapter = adapter
    }

    private fun fetchMyRequests() {
        db.collection("visitor_requests")
            .whereEqualTo("hostId", facultyId)
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener
                val list = snapshots?.toObjects(VisitorRequest::class.java) ?: emptyList()
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
        db.collection("visitor_requests").document(requestId)
            .update(field, value)
            .addOnSuccessListener {
                Toast.makeText(this, "$field updated", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showCancelConfirmation(request: VisitorRequest) {
        AlertDialog.Builder(this)
            .setTitle("Cancel Request")
            .setMessage("Are you sure you want to cancel the request for ${request.guestName}?")
            .setPositiveButton("Yes, Cancel") { _, _ ->
                db.collection("visitor_requests").document(request.requestId)
                    .update("status", "Cancelled")
                    .addOnSuccessListener {
                        Toast.makeText(this, "Request cancelled", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("No", null)
            .show()
    }
}
