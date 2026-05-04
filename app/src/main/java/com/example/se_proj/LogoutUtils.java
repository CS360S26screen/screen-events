package com.example.se_proj;

import android.app.Activity;
import android.content.Intent;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.google.firebase.auth.FirebaseAuth;

/**
 * Shared logout confirmation flow for dashboard toolbar actions.
 */
public final class LogoutUtils {

    private LogoutUtils() {
    }

    public static void attachLogoutConfirmation(Activity activity, View logoutButton) {
        if (logoutButton == null) {
            return;
        }
        logoutButton.setOnClickListener(v -> showLogoutConfirmation(activity));
    }

    public static void showLogoutConfirmation(Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    FirebaseAuth.getInstance().signOut();
                    Intent intent = new Intent(activity, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    activity.startActivity(intent);
                    activity.finish();
                })
                .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                .show();
    }
}
