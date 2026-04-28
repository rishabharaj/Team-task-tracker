package com.example.tasktracker;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class CreateTeamActivity extends AppCompatActivity {

    private EditText etTeamName, etJoinCode;
    private Button btnCreateTeam, btnJoinTeam, btnBack;

    private FirebaseAuth mAuth;
    private DatabaseReference rootRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_team);

        mAuth = FirebaseAuth.getInstance();
        rootRef = FirebaseDatabase.getInstance().getReference("tasktracker");

        etTeamName = findViewById(R.id.etTeamName);
        etJoinCode = findViewById(R.id.etJoinCode);
        btnCreateTeam = findViewById(R.id.btnCreateTeam);
        btnJoinTeam = findViewById(R.id.btnJoinTeam);
        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        // ---- Create Team ----
        btnCreateTeam.setOnClickListener(v -> {
            String teamName = etTeamName.getText().toString().trim();
            if (teamName.isEmpty()) {
                Toast.makeText(this, "Please enter a team name", Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseUser user = mAuth.getCurrentUser();
            if (user == null) return;

            // Generate 6-digit team code
            int randomCode = 100000 + new Random().nextInt(900000);
            String teamCode = String.valueOf(randomCode);

            // Create team in Firebase
            DatabaseReference teamRef = rootRef.child("teams").child(teamCode);

            Map<String, Object> teamData = new HashMap<>();
            teamData.put("name", teamName);
            teamData.put("createdBy", user.getUid());
            teamData.put("createdAt", System.currentTimeMillis());

            Map<String, Object> memberData = new HashMap<>();
            memberData.put("name", user.getDisplayName());
            memberData.put("email", user.getEmail());
            memberData.put("role", "admin");
            if (user.getPhotoUrl() != null) {
                memberData.put("photoUrl", user.getPhotoUrl().toString());
            }

            teamRef.updateChildren(teamData).addOnCompleteListener(task -> {
                teamRef.child("members").child(user.getUid()).setValue(memberData);

                // Save user's team association
                rootRef.child("users").child(user.getUid()).child("teams").child(teamCode).setValue(true);

                // Save to SharedPreferences
                SharedPreferences prefs = getSharedPreferences("TaskTrackerPrefs", MODE_PRIVATE);
                prefs.edit()
                        .putString("teamCode", teamCode)
                        .putString("teamName", teamName)
                        .apply();

                // Copy code to clipboard
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Team Code", teamCode);
                clipboard.setPrimaryClip(clip);

                Toast.makeText(this,
                        "Team created! Code: " + teamCode + " (Copied!)",
                        Toast.LENGTH_LONG).show();

                Intent intent = new Intent(this, DashboardActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        });

        // ---- Join Team ----
        btnJoinTeam.setOnClickListener(v -> {
            String teamCode = etJoinCode.getText().toString().trim();
            if (teamCode.isEmpty()) {
                Toast.makeText(this, "Please enter team code", Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseUser user = mAuth.getCurrentUser();
            if (user == null) return;

            // Verify team exists
            DatabaseReference teamRef = rootRef.child("teams").child(teamCode);
            teamRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String teamName = snapshot.child("name").getValue(String.class);
                        if (teamName == null) teamName = "Team " + teamCode;

                        // Add user as member
                        Map<String, Object> memberData = new HashMap<>();
                        memberData.put("name", user.getDisplayName());
                        memberData.put("email", user.getEmail());
                        memberData.put("role", "member");
                        if (user.getPhotoUrl() != null) {
                            memberData.put("photoUrl", user.getPhotoUrl().toString());
                        }

                        teamRef.child("members").child(user.getUid()).setValue(memberData);

                        // Save user's team association
                        rootRef.child("users").child(user.getUid()).child("teams").child(teamCode).setValue(true);

                        // Save to SharedPreferences
                        SharedPreferences prefs = getSharedPreferences("TaskTrackerPrefs", MODE_PRIVATE);
                        prefs.edit()
                                .putString("teamCode", teamCode)
                                .putString("teamName", teamName)
                                .apply();

                        Toast.makeText(CreateTeamActivity.this,
                                "Joined team: " + teamName, Toast.LENGTH_LONG).show();

                        Intent intent = new Intent(CreateTeamActivity.this, DashboardActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(CreateTeamActivity.this,
                                "Team not found! Check the code.", Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(CreateTeamActivity.this,
                            "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
