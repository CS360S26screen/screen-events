package com.example.se_proj

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.se_proj.databinding.ActivityStudentRequestBinding
import com.example.se_proj.models.VisitorRequest
import com.example.se_proj.rules.RequestStatus
import com.example.se_proj.rules.RequestValidationUtils
import com.example.se_proj.rules.UserProfileUtils
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import java.util.Calendar
import java.util.Locale

class StudentRequestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentRequestBinding
    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()
    private var studentRollNo: String = ""

    private var selectedDate: String = ""
    private var selectedStartTime: String = ""
    private var selectedEndTime: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentRequestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_logout -> {
                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                    val intent = android.content.Intent(this, LoginActivity::class.java)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                    true
                }
                else -> false
            }
        }

        binding.btnSubmit.isEnabled = false
        loadUserProfile()

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

    private fun loadUserProfile() {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrEmpty()) {
            Toast.makeText(this, "Please log in again to submit a guest pass", Toast.LENGTH_LONG).show()
            return
        }

        db.collection("Users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    setupProfileData(doc.id, doc.data)
                } else {
                    val email = auth.currentUser?.email
                    if (email != null) {
                        db.collection("Users").whereEqualTo("email", email).get()
                            .addOnSuccessListener { query ->
                                if (!query.isEmpty) {
                                    val d = query.documents[0]
                                    setupProfileData(d.id, d.data)
                                } else {
                                    Toast.makeText(this, "User profile not found", Toast.LENGTH_LONG).show()
                                }
                            }
                    }
                }
            }
            .addOnFailureListener { error ->
                Log.e("StudentRequest", "Failed to load user profile", error)
                Toast.makeText(this, "Unable to load your profile", Toast.LENGTH_LONG).show()
            }
    }

    private fun setupProfileData(docId: String, data: Map<String, Any>?) {
        studentRollNo = UserProfileUtils.resolveHostId(
            data?.get("rollNumber")?.toString(),
            data?.get("studentId")?.toString(),
            docId
        )
        binding.btnSubmit.isEnabled = studentRollNo.isNotEmpty()
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
        val cnic = RequestValidationUtils.normalizeCnic(binding.etCnic.text.toString())

        if (studentRollNo.isEmpty()) {
            Toast.makeText(this, "Your profile is still loading", Toast.LENGTH_SHORT).show()
            return
        }

        val validation = RequestValidationUtils.validateStudentGuestRequest(
            name,
            cnic,
            selectedDate,
            selectedStartTime,
            selectedEndTime,
            java.time.LocalDate.now(),
            java.time.LocalTime.now()
        )
        if (!validation.isValid) {
            Toast.makeText(this, validation.message, Toast.LENGTH_SHORT).show()
            return
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_logout -> {
                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                    val intent = android.content.Intent(this, LoginActivity::class.java)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                    true
                }
                else -> false
            }
        }

        binding.btnSubmit.isEnabled = false
        val uid = auth.currentUser?.uid ?: ""
        
        db.collection("visitor_requests")
            .whereEqualTo("creatorId", uid)
            .get()
            .addOnSuccessListener { documents ->
                val existingRequests = documents.toObjects(VisitorRequest::class.java)
                if (RequestValidationUtils.hasBlockingStudentPass(existingRequests)) {
                    binding.btnSubmit.isEnabled = true
                    Toast.makeText(this, "Limit reached: you already have an active or pending guest pass.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val hasDuplicate = existingRequests.any {
                    RequestValidationUtils.matchesDuplicateCandidate(
                        it,
                        studentRollNo,
                        cnic,
                        selectedDate
                    )
                }
                if (hasDuplicate) {
                    binding.btnSubmit.isEnabled = true
                    Toast.makeText(this, "A similar guest pass already exists for this visitor and date.", Toast.LENGTH_LONG).show()
                } else {
                    saveVisitorRequest(name, cnic, studentRollNo)
                }
            }
            .addOnFailureListener { e ->
                binding.btnSubmit.isEnabled = true
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
            creatorId = auth.currentUser?.uid ?: "",
            hostType = "student",
            status = RequestStatus.PENDING,
            onCampus = false
        )

        db.collection("visitor_requests")
            .add(request)
            .addOnSuccessListener {
                Toast.makeText(this, "Request Submitted for Approval", Toast.LENGTH_SHORT).show()
                val intent = android.content.Intent(this, MainActivity::class.java)
                intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                binding.btnSubmit.isEnabled = true
                Log.e("FirestoreError", "Error adding document", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
