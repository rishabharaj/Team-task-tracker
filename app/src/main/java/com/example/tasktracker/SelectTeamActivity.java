package com.example.tasktracker;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SelectTeamActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private DatabaseReference rootRef;

    private ListView lvTeams;
    private ProgressBar pbLoadingTeams;
    private Button btnCreateNewTeam, btnJoinNewTeam, btnCancel;

    private List<Map<String, String>> teamsList;
    private List<String> displayNames;
    private ArrayAdapter<String> adapter;
    
    private boolean isSwitching = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_team);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();

        if (user == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        rootRef = FirebaseDatabase.getInstance().getReference("tasktracker");
        
        isSwitching = getIntent().getBooleanExtra("isSwitching", false);

        lvTeams = findViewById(R.id.lvTeams);
        pbLoadingTeams = findViewById(R.id.pbLoadingTeams);
        btnCreateNewTeam = findViewById(R.id.btnCreateNewTeam);
        btnJoinNewTeam = findViewById(R.id.btnJoinNewTeam);
        btnCancel = findViewById(R.id.btnCancel);

        teamsList = new ArrayList<>();
        displayNames = new ArrayList<>();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, displayNames);
        lvTeams.setAdapter(adapter);
        
        if (isSwitching) {
            btnCancel.setVisibility(View.VISIBLE);
        }
        
        btnCancel.setOnClickListener(v -> finish());

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

        lvTeams.setOnItemClickListener((parent, view, position, id) -> {
            Map<String, String> team = teamsList.get(position);
            selectTeam(team.get("code"), team.get("name"));
        });

        loadUserTeams(user.getUid());
    }

    private void loadUserTeams(String uid) {
        rootRef.child("users").child(uid).child("teams").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists() || !snapshot.hasChildren()) {
                    pbLoadingTeams.setVisibility(View.GONE);
                    // No teams found
                    return;
                }

                int totalTeams = (int) snapshot.getChildrenCount();
                int[] loadedTeams = {0};

                for (DataSnapshot teamChild : snapshot.getChildren()) {
                    String teamCode = teamChild.getKey();
                    if (teamCode != null) {
                        rootRef.child("teams").child(teamCode).child("name").addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot nameSnapshot) {
                                String teamName = nameSnapshot.getValue(String.class);
                                if (teamName == null) teamName = "Unknown Team";

                                Map<String, String> teamMap = new HashMap<>();
                                teamMap.put("code", teamCode);
                                teamMap.put("name", teamName);
                                teamsList.add(teamMap);
                                displayNames.add(teamName + " (" + teamCode + ")");

                                loadedTeams[0]++;
                                if (loadedTeams[0] == totalTeams) {
                                    pbLoadingTeams.setVisibility(View.GONE);
                                    adapter.notifyDataSetChanged();
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                loadedTeams[0]++;
                            }
                        });
                    } else {
                        loadedTeams[0]++;
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                pbLoadingTeams.setVisibility(View.GONE);
                Toast.makeText(SelectTeamActivity.this, "Failed to load teams", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void selectTeam(String code, String name) {
        SharedPreferences prefs = getSharedPreferences("TaskTrackerPrefs", MODE_PRIVATE);
        prefs.edit()
                .putString("teamCode", code)
                .putString("teamName", name)
                .apply();

        Toast.makeText(this, "Switched to " + name, Toast.LENGTH_SHORT).show();
        
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
