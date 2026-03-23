package com.example.se_proj

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.se_proj.databinding.ActivityStudentRequestBinding
import com.example.se_proj.models.VisitorRequest
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import java.util.Calendar
import java.util.Locale

class StudentRequestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentRequestBinding
    private val db = Firebase.firestore

    private var selectedDate: String = ""
    private var selectedStartTime: String = ""
    private var selectedEndTime: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentRequestBinding.inflate(layoutInflater)
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

        binding.btnSubmit.setOnClickListener { checkLimitAndSubmit() }
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

    private fun checkLimitAndSubmit() {
        val name = binding.etGuestName.text.toString().trim()
        val cnic = binding.etCnic.text.toString().trim()

        if (name.isEmpty() || cnic.isEmpty() || selectedDate.isEmpty() || selectedStartTime.isEmpty() || selectedEndTime.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val studentRollNo = "27100xxx" // Get from logged-in user context in a real app

        db.collection("visitor_requests")
            .whereEqualTo("hostId", studentRollNo)
            .whereEqualTo("onCampus", true)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.size() >= 1) {
                    Toast.makeText(this, "Limit reached: You already have an active guest on campus.", Toast.LENGTH_LONG).show()
                } else {
                    saveVisitorRequest(name, cnic, studentRollNo)
                }
            }
            .addOnFailureListener { e ->
                Log.e("FirestoreError", "Error checking limits", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveVisitorRequest(name: String, cnic: String, studentRollNo: String) {
        val request = VisitorRequest(
            guestName = name,
            guestCNIC = cnic,
            purpose = "Student Guest Visit",
            visitDate = selectedDate,
            startTime = selectedStartTime,
            endTime = selectedEndTime,
            hostId = studentRollNo,
            hostType = "student",
            status = "pending", // Changed to pending for Admin Hub
            onCampus = false
        )

        db.collection("visitor_requests")
            .add(request)
            .addOnSuccessListener {
                Toast.makeText(this, "Request Submitted for Approval", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Log.e("FirestoreError", "Error adding document", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
