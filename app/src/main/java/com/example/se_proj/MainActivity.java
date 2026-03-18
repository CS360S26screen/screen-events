package com.example.se_proj;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.se_proj.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        binding.btnSubmitRequest.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, RequestSubmissionActivity.class);
            startActivity(intent);
        });

        binding.btnStudentRequest.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, StudentRequestActivity.class);
            startActivity(intent);
        });

        binding.btnAdminDashboard.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AdminDashboardActivity.class);
            startActivity(intent);
        });

        binding.btnGuardDashboard.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, GuardDashboardActivity.class);
            startActivity(intent);
        });
    }
}