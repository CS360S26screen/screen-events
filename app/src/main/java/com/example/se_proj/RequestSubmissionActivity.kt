package com.example.se_proj

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.se_proj.databinding.ActivityRequestSubmissionBinding
import com.example.se_proj.models.VisitorRequest
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class RequestSubmissionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRequestSubmissionBinding
    private val db = Firebase.firestore
    private var adHocListener: ListenerRegistration? = null
    private val facultyId = "FAC-123" // Replace with actual logged-in user ID

    private var selectedDate: String = ""
    private var selectedStartTime: String = ""
    private var selectedEndTime: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRequestSubmissionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.etVisitDate.setOnClickListener { showDatePicker() }
        binding.etStartTime.setOnClickListener { showTimePicker { time -> 
            selectedStartTime = time
            binding.etStartTime.setText(time)
        }}
        binding.etEndTime.setOnClickListener { showTimePicker { time -> 
            selectedEndTime = time
            binding.etEndTime.setText(time)
        }}

        binding.btnSubmit.setOnClickListener { submitRequest() }
        
        binding.btnViewRequests.setOnClickListener {
            startActivity(Intent(this, FacultyRequestsActivity::class.java))
        }

        startAdHocListener()
        checkForOverstayingGuests()
    }

    private fun startAdHocListener() {
        adHocListener = db.collection("visitor_requests")
            .whereEqualTo("hostId", facultyId)
            .whereEqualTo("status", "pending_adhoc")
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener
                
                for (doc in snapshots!!.documentChanges) {
                    if (doc.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val request = doc.document.toObject(VisitorRequest::class.java)
                        showAdHocDialog(doc.document.id, request)
                    }
                }
            }
    }

    private fun checkForOverstayingGuests() {
        val currentTime = Calendar.getInstance()
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        
        db.collection("visitor_requests")
            .whereEqualTo("hostId", facultyId)
            .whereEqualTo("onCampus", true)
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    val request = doc.toObject(VisitorRequest::class.java)
                    try {
                        val endTime = sdf.parse(request.endTime)
                        val now = sdf.parse(sdf.format(Date()))
                        
                        if (endTime != null && now != null) {
                            val diff = endTime.time - now.time
                            val minutesLeft = diff / (1000 * 60)
                            
                            if (minutesLeft in 0..15) {
                                showOverstayAlert(request.guestName, request.endTime)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("Reminder", "Error parsing time", e)
                    }
                }
            }
    }

    private fun showOverstayAlert(guestName: String, endTime: String) {
        AlertDialog.Builder(this)
            .setTitle("Upcoming Exit Reminder")
            .setMessage("Your guest $guestName is scheduled to leave at $endTime (within 15 minutes).")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showAdHocDialog(requestId: String, request: VisitorRequest) {
        AlertDialog.Builder(this)
            .setTitle("Walk-in Approval Request")
            .setMessage("Guest ${request.guestName} is at the gate for: ${request.purpose}. Approve entry?")
            .setPositiveButton("Approve") { _, _ ->
                updateRequestStatus(requestId, "approved")
            }
            .setNegativeButton("Deny") { _, _ ->
                updateRequestStatus(requestId, "denied")
            }
            .setCancelable(false)
            .show()
    }

    private fun updateRequestStatus(requestId: String, newStatus: String) {
        db.collection("visitor_requests").document(requestId)
            .update("status", newStatus)
            .addOnSuccessListener {
                Toast.makeText(this, "Request $newStatus", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, dayOfMonth ->
            selectedDate = String.format(Locale.getDefault(), "%02d/%02d/%d", dayOfMonth, month + 1, year)
            binding.etVisitDate.setText(selectedDate)
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showTimePicker(onTimeSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(this, { _, hourOfDay, minute ->
            val time = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
            onTimeSelected(time)
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
    }

    private fun submitRequest() {
        val name = binding.etGuestName.text.toString().trim()
        val cnic = binding.etCnic.text.toString().trim()
        val purpose = binding.etPurpose.text.toString().trim()

        if (name.isEmpty() || cnic.isEmpty() || purpose.isEmpty() || selectedDate.isEmpty() || selectedStartTime.isEmpty() || selectedEndTime.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val request = VisitorRequest(
            guestName = name,
            guestCNIC = cnic,
            purpose = purpose,
            visitDate = selectedDate,
            startTime = selectedStartTime,
            endTime = selectedEndTime,
            hostId = facultyId,
            hostType = "faculty",
            status = "approved" // Pre-approved by faculty
        )

        db.collection("visitor_requests")
            .add(request)
            .addOnSuccessListener {
                Toast.makeText(this, "Request Submitted Successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Log.e("FirestoreError", "Error adding document", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        adHocListener?.remove()
    }
}
