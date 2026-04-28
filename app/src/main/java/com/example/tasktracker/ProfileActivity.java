package com.example.tasktracker;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import de.hdodenhof.circleimageview.CircleImageView;

public class ProfileActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private DatabaseReference rootRef;

    private CircleImageView ivProfilePic;
    private TextView tvUserName, tvUserEmail;
    private View btnEditName;
    private LinearLayout llTeamsList;
    private Button btnCreateNewTeam, btnJoinNewTeam, btnLeaveTeam, btnSignOut, btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            goToLogin();
            return;
        }

        rootRef = FirebaseDatabase.getInstance().getReference("tasktracker");

        // Bind Views
        ivProfilePic = findViewById(R.id.ivProfilePic);
        tvUserName = findViewById(R.id.tvUserName);
        tvUserEmail = findViewById(R.id.tvUserEmail);
        btnEditName = findViewById(R.id.btnEditName);
        llTeamsList = findViewById(R.id.llTeamsList);
        btnCreateNewTeam = findViewById(R.id.btnCreateNewTeam);
        btnJoinNewTeam = findViewById(R.id.btnJoinNewTeam);
        btnLeaveTeam = findViewById(R.id.btnLeaveTeam);
        btnSignOut = findViewById(R.id.btnSignOut);
        btnBack = findViewById(R.id.btnBack);

        loadUserProfile();

        // Edit Name
        btnEditName.setOnClickListener(v -> showEditNameDialog());

        // Create / Join Team buttons
        btnCreateNewTeam.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateTeamActivity.class);
            intent.putExtra("mode", "create");
            startActivity(intent);
        });
        
        btnJoinNewTeam.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateTeamActivity.class);
            intent.putExtra("mode", "join");
            startActivity(intent);
        });

        // Load all User Teams
        loadAllUserTeams(currentUser.getUid());

        SharedPreferences prefs = getSharedPreferences("TaskTrackerPrefs", MODE_PRIVATE);
        String currentTeamCode = prefs.getString("teamCode", null);
        if (currentTeamCode != null) {
            btnLeaveTeam.setOnClickListener(v -> showLeaveTeamDialog(currentTeamCode));
        } else {
            btnLeaveTeam.setVisibility(View.GONE);
        }

        btnSignOut.setOnClickListener(v -> showSignOutDialog());
        btnBack.setOnClickListener(v -> finish());
    }

    private void loadAllUserTeams(String uid) {
        rootRef.child("users").child(uid).child("teams").addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(@androidx.annotation.NonNull com.google.firebase.database.DataSnapshot snapshot) {
                llTeamsList.removeAllViews();
                if (!snapshot.exists() || !snapshot.hasChildren()) {
                    TextView tvNoTeams = new TextView(ProfileActivity.this);
                    tvNoTeams.setText("You are not part of any team.");
                    tvNoTeams.setPadding(0, 16, 0, 16);
                    llTeamsList.addView(tvNoTeams);
                    return;
                }

                SharedPreferences prefs = getSharedPreferences("TaskTrackerPrefs", MODE_PRIVATE);
                String activeTeamCode = prefs.getString("teamCode", null);

                for (com.google.firebase.database.DataSnapshot teamChild : snapshot.getChildren()) {
                    String teamCode = teamChild.getKey();
                    if (teamCode != null) {
                        rootRef.child("teams").child(teamCode).child("name").addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                            @Override
                            public void onDataChange(@androidx.annotation.NonNull com.google.firebase.database.DataSnapshot nameSnapshot) {
                                String teamName = nameSnapshot.getValue(String.class);
                                if (teamName == null) teamName = "Unknown Team";
                                addTeamToView(teamCode, teamName, teamCode.equals(activeTeamCode));
                            }
                            @Override
                            public void onCancelled(@androidx.annotation.NonNull com.google.firebase.database.DatabaseError error) {}
                        });
                    }
                }
            }
            @Override
            public void onCancelled(@androidx.annotation.NonNull com.google.firebase.database.DatabaseError error) {}
        });
    }

    private void addTeamToView(String code, String name, boolean isActive) {
        LinearLayout teamRow = new LinearLayout(this);
        teamRow.setOrientation(LinearLayout.VERTICAL);
        teamRow.setBackgroundResource(R.drawable.bg_card_rounded);
        teamRow.setPadding(32, 24, 32, 24);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 16);
        teamRow.setLayoutParams(params);

        TextView tvName = new TextView(this);
        tvName.setText(name + (isActive ? " (Active)" : ""));
        tvName.setTextSize(18);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setTextColor(getResources().getColor(isActive ? R.color.primary : R.color.text_primary));
        
        TextView tvCode = new TextView(this);
        tvCode.setText("Code: " + code);
        tvCode.setTextSize(14);
        tvCode.setTextColor(getResources().getColor(R.color.text_secondary));
        tvCode.setPadding(0, 8, 0, 0);

        teamRow.addView(tvName);
        teamRow.addView(tvCode);

        // Click to switch
        teamRow.setOnClickListener(v -> {
            if (!isActive) {
                SharedPreferences prefs = getSharedPreferences("TaskTrackerPrefs", MODE_PRIVATE);
                prefs.edit().putString("teamCode", code).putString("teamName", name).apply();
                Toast.makeText(this, "Switched to " + name, Toast.LENGTH_SHORT).show();
                
                Intent intent = new Intent(this, DashboardActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, "Copying active team code", Toast.LENGTH_SHORT).show();
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Team Code", code);
                clipboard.setPrimaryClip(clip);
            }
        });

        llTeamsList.addView(teamRow);
    }

    private void loadUserProfile() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            tvUserName.setText(user.getDisplayName() != null ? user.getDisplayName() : "No Name");
            tvUserEmail.setText(user.getEmail() != null ? user.getEmail() : "No Email");
            if (user.getPhotoUrl() != null) {
                Glide.with(this).load(user.getPhotoUrl()).into(ivProfilePic);
            }
        }
    }

    private void showEditNameDialog() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Name");

        final EditText input = new EditText(this);
        input.setText(user.getDisplayName());
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                        .setDisplayName(newName)
                        .build();

                user.updateProfile(profileUpdates).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Name updated successfully", Toast.LENGTH_SHORT).show();
                        loadUserProfile();
                        // Update in database as well
                        rootRef.child("users").child(user.getUid()).child("name").setValue(newName);
                        
                        // Propagate change to current team
                        SharedPreferences prefs = getSharedPreferences("TaskTrackerPrefs", MODE_PRIVATE);
                        String currentTeamCode = prefs.getString("teamCode", null);
                        
                        if (currentTeamCode != null) {
                            // Update team members list
                            rootRef.child("teams").child(currentTeamCode).child("members")
                                    .child(user.getUid()).child("name").setValue(newName);
                                    
                            // Update assignee names in tasks
                            rootRef.child("teams").child(currentTeamCode).child("tasks")
                                .orderByChild("assignee").equalTo(user.getUid())
                                .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                                    @Override
                                    public void onDataChange(@androidx.annotation.NonNull com.google.firebase.database.DataSnapshot snapshot) {
                                        for (com.google.firebase.database.DataSnapshot task : snapshot.getChildren()) {
                                            task.getRef().child("assigneeName").setValue(newName);
                                        }
                                    }
                                    @Override
                                    public void onCancelled(@androidx.annotation.NonNull com.google.firebase.database.DatabaseError error) {}
                                });
                                
                            // Update creator names in tasks
                            rootRef.child("teams").child(currentTeamCode).child("tasks")
                                .orderByChild("createdBy").equalTo(user.getUid())
                                .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                                    @Override
                                    public void onDataChange(@androidx.annotation.NonNull com.google.firebase.database.DataSnapshot snapshot) {
                                        for (com.google.firebase.database.DataSnapshot task : snapshot.getChildren()) {
                                            task.getRef().child("createdByName").setValue(newName);
                                        }
                                    }
                                    @Override
                                    public void onCancelled(@androidx.annotation.NonNull com.google.firebase.database.DatabaseError error) {}
                                });
                        }
                    } else {
                        Toast.makeText(this, "Failed to update name", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showLeaveTeamDialog(String teamCode) {
        new AlertDialog.Builder(this)
                .setTitle("Leave Team")
                .setMessage("Are you sure you want to leave this team? You will need the team code to join again.")
                .setPositiveButton("Leave", (dialog, which) -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        // Remove from members
                        rootRef.child("teams").child(teamCode).child("members").child(user.getUid()).removeValue();
                        // Remove from user's teams
                        rootRef.child("users").child(user.getUid()).child("teams").child(teamCode).removeValue();
                        
                        // Clear SharedPreferences
                        getSharedPreferences("TaskTrackerPrefs", MODE_PRIVATE).edit()
                                .remove("teamCode")
                                .remove("teamName")
                                .apply();
                        
                        Toast.makeText(this, "You have left the team", Toast.LENGTH_SHORT).show();
                        finish(); // Go back to dashboard
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSignOutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Sign Out", (dialog, which) -> {
                    mAuth.signOut();
                    GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestIdToken(getString(R.string.default_web_client_id))
                            .requestEmail()
                            .build();
                    GoogleSignInClient googleClient = GoogleSignIn.getClient(this, gso);
                    googleClient.signOut().addOnCompleteListener(task -> {
                        getSharedPreferences("TaskTrackerPrefs", MODE_PRIVATE).edit().clear().apply();
                        goToLogin();
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
