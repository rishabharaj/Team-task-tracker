package com.example.tasktracker;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DashboardActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private DatabaseReference rootRef;

    // UI - Header
    private TextView tvWelcome, tvTeamName, tvTeamCode;
    private TextView tvTotalTasks, tvInProgress, tvCompleted;
    private LinearLayout teamInfoRow, statsRow, filterRow, noTeamState, emptyTaskState, bottomBar;
    
    // UI - Actions
    private Button btnCreateTeam, btnJoinTeam, btnSignOutFromNoTeam;
    private Button btnFilterAll, btnFilterTodo, btnFilterProgress, btnFilterDone;
    
    // UI - Bottom Nav
    private LinearLayout tabTasks, tabMembers, tabAdd, tabProfile;
    
    // UI - List
    private ListView lvTasks;

    // Data
    private String currentTeamCode;
    private String currentTeamName;
    private ArrayList<Map<String, String>> taskDataList;
    private ArrayList<String> taskKeyList;
    private String currentFilter = "all";

    // Firebase listeners
    private ValueEventListener tasksListener;
    private DatabaseReference tasksRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            goToLogin();
            return;
        }

        rootRef = FirebaseDatabase.getInstance().getReference("tasktracker");

        // Bind Views
        tvWelcome = findViewById(R.id.tvWelcome);
        tvTeamName = findViewById(R.id.tvTeamName);
        tvTeamCode = findViewById(R.id.tvTeamCode);
        tvTotalTasks = findViewById(R.id.tvTotalTasks);
        tvInProgress = findViewById(R.id.tvInProgress);
        tvCompleted = findViewById(R.id.tvCompleted);
        
        btnCreateTeam = findViewById(R.id.btnCreateTeam);
        btnJoinTeam = findViewById(R.id.btnJoinTeam);
        btnSignOutFromNoTeam = findViewById(R.id.btnSignOutFromNoTeam);
        
        btnFilterAll = findViewById(R.id.btnFilterAll);
        btnFilterTodo = findViewById(R.id.btnFilterTodo);
        btnFilterProgress = findViewById(R.id.btnFilterProgress);
        btnFilterDone = findViewById(R.id.btnFilterDone);
        
        tabTasks = findViewById(R.id.tabTasks);
        tabMembers = findViewById(R.id.tabMembers);
        tabAdd = findViewById(R.id.tabAdd);
        tabProfile = findViewById(R.id.tabProfile);
        
        teamInfoRow = findViewById(R.id.teamInfoRow);
        statsRow = findViewById(R.id.statsRow);
        filterRow = findViewById(R.id.filterRow);
        noTeamState = findViewById(R.id.noTeamState);
        emptyTaskState = findViewById(R.id.emptyTaskState);
        bottomBar = findViewById(R.id.bottomBar);
        lvTasks = findViewById(R.id.lvTasks);

        // Init data
        taskDataList = new ArrayList<>();
        taskKeyList = new ArrayList<>();

        // Setup welcome message
        String name = currentUser.getDisplayName();
        if (name != null && !name.isEmpty()) {
            tvWelcome.setText("Welcome, " + name.split(" ")[0] + "!");
        }

        // Sign Out from No Team State
        btnSignOutFromNoTeam.setOnClickListener(v -> signOut());

        // Create/Join Team (when no team exists)
        btnCreateTeam.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateTeamActivity.class);
            intent.putExtra("mode", "create");
            startActivity(intent);
        });

        btnJoinTeam.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateTeamActivity.class);
            intent.putExtra("mode", "join");
            startActivity(intent);
        });

        // Filter buttons
        btnFilterAll.setOnClickListener(v -> { currentFilter = "all"; filterTasks(); });
        btnFilterTodo.setOnClickListener(v -> { currentFilter = "todo"; filterTasks(); });
        btnFilterProgress.setOnClickListener(v -> { currentFilter = "in_progress"; filterTasks(); });
        btnFilterDone.setOnClickListener(v -> { currentFilter = "completed"; filterTasks(); });

        // Bottom Navigation
        tabTasks.setOnClickListener(v -> {
            // Already on tasks, maybe refresh
            Toast.makeText(this, "Tasks view", Toast.LENGTH_SHORT).show();
        });

        tabMembers.setOnClickListener(v -> {
            if (currentTeamCode != null) {
                Intent intent = new Intent(this, TeamMembersActivity.class);
                intent.putExtra("teamCode", currentTeamCode);
                intent.putExtra("teamName", currentTeamName);
                startActivity(intent);
            }
        });

        tabAdd.setOnClickListener(v -> {
            if (currentTeamCode != null) {
                Intent intent = new Intent(this, CreateTaskActivity.class);
                intent.putExtra("teamCode", currentTeamCode);
                startActivity(intent);
            }
        });

        tabProfile.setOnClickListener(v -> {
            startActivity(new Intent(this, ProfileActivity.class));
        });

        // Task Item Click Actions
        lvTasks.setOnItemClickListener((parent, view, position, id) -> {
            if (position < taskKeyList.size() && currentTeamCode != null) {
                String taskKey = taskKeyList.get(position);
                Intent intent = new Intent(this, TaskDetailActivity.class);
                intent.putExtra("teamCode", currentTeamCode);
                intent.putExtra("taskKey", taskKey);
                startActivity(intent);
            }
        });

        lvTasks.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < taskKeyList.size()) {
                showDeleteDialog(position);
            }
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Reload team info from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("TaskTrackerPrefs", MODE_PRIVATE);
        currentTeamCode = prefs.getString("teamCode", null);
        currentTeamName = prefs.getString("teamName", null);

        // Refresh User Name and Sync to Team
        if (currentUser != null) {
            // Need to reload the current user from auth to get latest profile updates
            currentUser = mAuth.getCurrentUser();
            String name = currentUser.getDisplayName();
            if (name != null && !name.isEmpty()) {
                tvWelcome.setText("Welcome, " + name.split(" ")[0] + "!");
            }

            // Automatically sync the user's latest name and photo to their team record
            if (currentTeamCode != null) {
                if (name != null) {
                    rootRef.child("teams").child(currentTeamCode).child("members")
                            .child(currentUser.getUid()).child("name").setValue(name);
                }
                if (currentUser.getPhotoUrl() != null) {
                    rootRef.child("teams").child(currentTeamCode).child("members")
                            .child(currentUser.getUid()).child("photoUrl").setValue(currentUser.getPhotoUrl().toString());
                }
            }
        }

        if (currentTeamCode != null) {
            showTeamView();
        } else {
            // Force user to select or create a team
            Intent intent = new Intent(this, SelectTeamActivity.class);
            startActivity(intent);
        }
    }

    private void showNoTeamView() {
        noTeamState.setVisibility(View.VISIBLE);
        teamInfoRow.setVisibility(View.GONE);
        statsRow.setVisibility(View.GONE);
        filterRow.setVisibility(View.GONE);
        lvTasks.setVisibility(View.GONE);
        emptyTaskState.setVisibility(View.GONE);
        bottomBar.setVisibility(View.GONE); // Hide bottom nav when no team

        // Remove previous listeners
        if (tasksListener != null && tasksRef != null) {
            tasksRef.removeEventListener(tasksListener);
        }
    }

    private void showTeamView() {
        noTeamState.setVisibility(View.GONE);
        teamInfoRow.setVisibility(View.VISIBLE);
        statsRow.setVisibility(View.VISIBLE);
        filterRow.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);

        tvTeamName.setText("🏢 " + (currentTeamName != null ? currentTeamName : "Team"));
        tvTeamCode.setText("Code: " + currentTeamCode);

        // Copy team code on tap
        tvTeamCode.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Team Code", currentTeamCode);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Team code copied!", Toast.LENGTH_SHORT).show();
        });

        loadTasks();
    }

    private void loadTasks() {
        if (tasksListener != null && tasksRef != null) {
            tasksRef.removeEventListener(tasksListener);
        }

        tasksRef = rootRef.child("teams").child(currentTeamCode).child("tasks");

        tasksListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                taskDataList.clear();
                taskKeyList.clear();

                int total = 0, inProgress = 0, completed = 0;

                for (DataSnapshot child : snapshot.getChildren()) {
                    Map<String, String> task = new HashMap<>();
                    task.put("key", child.getKey());
                    task.put("title", getStringValue(child, "title"));
                    task.put("description", getStringValue(child, "description"));
                    task.put("priority", getStringValue(child, "priority"));
                    task.put("status", getStringValue(child, "status"));
                    task.put("assigneeName", getStringValue(child, "assigneeName"));
                    task.put("dueDate", getStringValue(child, "dueDate"));
                    task.put("createdBy", getStringValue(child, "createdBy"));

                    taskDataList.add(task);
                    taskKeyList.add(child.getKey());

                    total++;
                    String status = task.get("status");
                    if ("in_progress".equals(status)) inProgress++;
                    else if ("completed".equals(status)) completed++;
                }

                tvTotalTasks.setText(String.valueOf(total));
                tvInProgress.setText(String.valueOf(inProgress));
                tvCompleted.setText(String.valueOf(completed));

                filterTasks();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(DashboardActivity.this, "Error loading tasks: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };

        tasksRef.addValueEventListener(tasksListener);
    }

    private void filterTasks() {
        ArrayList<Map<String, String>> filtered = new ArrayList<>();
        ArrayList<String> filteredKeys = new ArrayList<>();

        for (int i = 0; i < taskDataList.size(); i++) {
            Map<String, String> task = taskDataList.get(i);
            String status = task.get("status");

            if ("all".equals(currentFilter) ||
                (currentFilter.equals(status)) ||
                ("todo".equals(currentFilter) && ("todo".equals(status) || status == null || status.isEmpty()))) {
                filtered.add(task);
                filteredKeys.add(taskKeyList.get(i));
            }
        }

        if (filtered.isEmpty()) {
            lvTasks.setVisibility(View.GONE);
            emptyTaskState.setVisibility(View.VISIBLE);
        } else {
            lvTasks.setVisibility(View.VISIBLE);
            emptyTaskState.setVisibility(View.GONE);
        }

        TaskAdapter adapter = new TaskAdapter(this, filtered);
        lvTasks.setAdapter(adapter);
    }

    private void showStatusDialog(int position) {
        String key = taskKeyList.get(position);
        String[] statuses = {"To Do", "In Progress", "Completed"};
        String[] statusValues = {"todo", "in_progress", "completed"};

        new AlertDialog.Builder(this)
                .setTitle("Change Status")
                .setItems(statuses, (dialog, which) -> {
                    tasksRef.child(key).child("status").setValue(statusValues[which]);
                    Toast.makeText(this, "Status updated!", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showDeleteDialog(int position) {
        String key = taskKeyList.get(position);
        Map<String, String> task = taskDataList.get(position);

        new AlertDialog.Builder(this)
                .setTitle("Delete Task")
                .setMessage("Delete \"" + task.get("title") + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    tasksRef.child(key).removeValue();
                    Toast.makeText(this, "Task deleted!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String getStringValue(DataSnapshot snapshot, String key) {
        Object val = snapshot.child(key).getValue();
        return val != null ? val.toString() : "";
    }
    
    private void signOut() {
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
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tasksListener != null && tasksRef != null) {
            tasksRef.removeEventListener(tasksListener);
        }
    }

    // Custom Task Adapter
    private static class TaskAdapter extends ArrayAdapter<Map<String, String>> {
        private final Context context;
        private final ArrayList<Map<String, String>> tasks;

        public TaskAdapter(Context context, ArrayList<Map<String, String>> tasks) {
            super(context, R.layout.item_task, tasks);
            this.context = context;
            this.tasks = tasks;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_task, parent, false);
            }

            Map<String, String> task = tasks.get(position);

            TextView tvTitle = convertView.findViewById(R.id.tvTaskTitle);
            TextView tvDesc = convertView.findViewById(R.id.tvTaskDesc);
            TextView tvPriority = convertView.findViewById(R.id.tvPriority);
            TextView tvAssignee = convertView.findViewById(R.id.tvAssignee);
            TextView tvDueDate = convertView.findViewById(R.id.tvDueDate);
            TextView tvStatus = convertView.findViewById(R.id.tvStatus);

            tvTitle.setText(task.get("title"));

            String desc = task.get("description");
            if (desc != null && !desc.isEmpty()) {
                tvDesc.setVisibility(View.VISIBLE);
                tvDesc.setText(desc);
            } else {
                tvDesc.setVisibility(View.GONE);
            }

            String priority = task.get("priority");
            if ("high".equals(priority)) {
                tvPriority.setText("🔴 High");
                tvPriority.setBackgroundResource(R.drawable.bg_priority_high);
                tvPriority.setTextColor(context.getResources().getColor(R.color.priority_high));
            } else if ("medium".equals(priority)) {
                tvPriority.setText("🟡 Medium");
                tvPriority.setBackgroundResource(R.drawable.bg_priority_medium);
                tvPriority.setTextColor(context.getResources().getColor(R.color.priority_medium));
            } else {
                tvPriority.setText("🟢 Low");
                tvPriority.setBackgroundResource(R.drawable.bg_priority_low);
                tvPriority.setTextColor(context.getResources().getColor(R.color.priority_low));
            }

            String assignee = task.get("assigneeName");
            if (assignee != null && !assignee.isEmpty()) {
                tvAssignee.setText("👤 " + assignee);
            } else {
                tvAssignee.setText("👤 Unassigned");
            }

            String dueDate = task.get("dueDate");
            if (dueDate != null && !dueDate.isEmpty()) {
                tvDueDate.setText("📅 " + dueDate);
            } else {
                tvDueDate.setText("📅 No date");
            }

            String status = task.get("status");
            if ("in_progress".equals(status)) {
                tvStatus.setText("In Progress");
                tvStatus.setBackgroundResource(R.drawable.bg_status_chip);
                tvStatus.getBackground().setTint(context.getResources().getColor(R.color.status_in_progress));
            } else if ("completed".equals(status)) {
                tvStatus.setText("Completed");
                tvStatus.setBackgroundResource(R.drawable.bg_status_chip);
                tvStatus.getBackground().setTint(context.getResources().getColor(R.color.status_completed));
            } else {
                tvStatus.setText("To Do");
                tvStatus.setBackgroundResource(R.drawable.bg_status_chip);
                tvStatus.getBackground().setTint(context.getResources().getColor(R.color.status_todo));
            }

            return convertView;
        }
    }
}
