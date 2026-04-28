package com.example.tasktracker;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CreateTaskActivity extends AppCompatActivity {

    private EditText etTaskTitle, etTaskDesc;
    private Button btnPriorityHigh, btnPriorityMedium, btnPriorityLow;
    private Button btnCreateTask, btnBack;
    private TextView tvDueDate;
    private Spinner spinnerAssignee;

    private String selectedPriority = "medium";
    private String selectedDate = "";
    private String teamCode;

    private DatabaseReference rootRef;
    private FirebaseAuth mAuth;

    // Members for spinner
    private ArrayList<String> memberNames;
    private ArrayList<String> memberIds;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_task);

        teamCode = getIntent().getStringExtra("teamCode");
        if (teamCode == null) {
            Toast.makeText(this, "Error: No team selected", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mAuth = FirebaseAuth.getInstance();
        rootRef = FirebaseDatabase.getInstance().getReference("tasktracker");

        // Bind views
        etTaskTitle = findViewById(R.id.etTaskTitle);
        etTaskDesc = findViewById(R.id.etTaskDesc);
        btnPriorityHigh = findViewById(R.id.btnPriorityHigh);
        btnPriorityMedium = findViewById(R.id.btnPriorityMedium);
        btnPriorityLow = findViewById(R.id.btnPriorityLow);
        btnCreateTask = findViewById(R.id.btnCreateTask);
        btnBack = findViewById(R.id.btnBack);
        tvDueDate = findViewById(R.id.tvDueDate);
        spinnerAssignee = findViewById(R.id.spinnerAssignee);

        memberNames = new ArrayList<>();
        memberIds = new ArrayList<>();

        // Load team members for assignee spinner
        loadTeamMembers();

        // Back button
        btnBack.setOnClickListener(v -> finish());

        // Priority selection
        updatePriorityUI();

        btnPriorityHigh.setOnClickListener(v -> {
            selectedPriority = "high";
            updatePriorityUI();
        });

        btnPriorityMedium.setOnClickListener(v -> {
            selectedPriority = "medium";
            updatePriorityUI();
        });

        btnPriorityLow.setOnClickListener(v -> {
            selectedPriority = "low";
            updatePriorityUI();
        });

        // Date picker
        findViewById(R.id.btnPickDate).setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            DatePickerDialog datePicker = new DatePickerDialog(this,
                    (view, year, month, dayOfMonth) -> {
                        Calendar selected = Calendar.getInstance();
                        selected.set(year, month, dayOfMonth);
                        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.US);
                        selectedDate = sdf.format(selected.getTime());
                        tvDueDate.setText(selectedDate);
                        tvDueDate.setTextColor(getResources().getColor(R.color.text_primary));
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH));
            datePicker.show();
        });

        // Create Task
        btnCreateTask.setOnClickListener(v -> createTask());
    }

    private void loadTeamMembers() {
        memberNames.add("Unassigned");
        memberIds.add("");

        rootRef.child("teams").child(teamCode).child("members")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot member : snapshot.getChildren()) {
                            String name = member.child("name").getValue(String.class);
                            String id = member.getKey();
                            if (name != null) {
                                memberNames.add(name);
                                memberIds.add(id);
                            }
                        }

                        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                                CreateTaskActivity.this,
                                android.R.layout.simple_spinner_item,
                                memberNames);
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        spinnerAssignee.setAdapter(adapter);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(CreateTaskActivity.this,
                                "Failed to load members", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updatePriorityUI() {
        // Reset all to light backgrounds
        btnPriorityHigh.setAlpha("high".equals(selectedPriority) ? 1.0f : 0.5f);
        btnPriorityMedium.setAlpha("medium".equals(selectedPriority) ? 1.0f : 0.5f);
        btnPriorityLow.setAlpha("low".equals(selectedPriority) ? 1.0f : 0.5f);
    }

    private void createTask() {
        String title = etTaskTitle.getText().toString().trim();
        String desc = etTaskDesc.getText().toString().trim();

        if (title.isEmpty()) {
            Toast.makeText(this, "Please enter a task title", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        DatabaseReference tasksRef = rootRef.child("teams").child(teamCode).child("tasks");
        DatabaseReference newTask = tasksRef.push();

        Map<String, Object> taskData = new HashMap<>();
        taskData.put("title", title);
        taskData.put("description", desc);
        taskData.put("priority", selectedPriority);
        taskData.put("status", "todo");
        taskData.put("createdBy", user.getUid());
        taskData.put("createdByName", user.getDisplayName());
        taskData.put("createdAt", System.currentTimeMillis());

        if (!selectedDate.isEmpty()) {
            taskData.put("dueDate", selectedDate);
        }

        // Assignee
        int selectedPosition = spinnerAssignee.getSelectedItemPosition();
        if (selectedPosition > 0 && selectedPosition < memberIds.size()) {
            taskData.put("assignee", memberIds.get(selectedPosition));
            taskData.put("assigneeName", memberNames.get(selectedPosition));
        }

        newTask.setValue(taskData).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(this, "Task created! ✅", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "Failed to create task", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
