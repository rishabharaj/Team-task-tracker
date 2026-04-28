package com.example.tasktracker;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class TaskDetailActivity extends AppCompatActivity {

    private String teamCode;
    private String taskKey;

    private TextView tvTitle, tvDesc, tvPriority, tvStatus;
    private TextView tvAssignee, tvDueDate, tvCreatedBy;
    private Button btnStatusTodo, btnStatusProgress, btnStatusDone, btnBack;

    private DatabaseReference taskRef;
    private ValueEventListener taskListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_detail);

        teamCode = getIntent().getStringExtra("teamCode");
        taskKey = getIntent().getStringExtra("taskKey");

        if (teamCode == null || taskKey == null) {
            Toast.makeText(this, "Error loading task", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        taskRef = FirebaseDatabase.getInstance().getReference("tasktracker")
                .child("teams").child(teamCode).child("tasks").child(taskKey);

        tvTitle = findViewById(R.id.tvDetailTitle);
        tvDesc = findViewById(R.id.tvDetailDesc);
        tvPriority = findViewById(R.id.tvDetailPriority);
        tvStatus = findViewById(R.id.tvDetailStatus);
        tvAssignee = findViewById(R.id.tvDetailAssignee);
        tvDueDate = findViewById(R.id.tvDetailDueDate);
        tvCreatedBy = findViewById(R.id.tvDetailCreatedBy);

        btnStatusTodo = findViewById(R.id.btnStatusTodo);
        btnStatusProgress = findViewById(R.id.btnStatusProgress);
        btnStatusDone = findViewById(R.id.btnStatusDone);
        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        btnStatusTodo.setOnClickListener(v -> updateStatus("todo"));
        btnStatusProgress.setOnClickListener(v -> updateStatus("in_progress"));
        btnStatusDone.setOnClickListener(v -> updateStatus("completed"));

        loadTaskDetails();
    }

    private void loadTaskDetails() {
        taskListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Toast.makeText(TaskDetailActivity.this, "Task deleted", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                String title = getStringValue(snapshot, "title");
                String desc = getStringValue(snapshot, "description");
                String priority = getStringValue(snapshot, "priority");
                String status = getStringValue(snapshot, "status");
                String assignee = getStringValue(snapshot, "assigneeName");
                String dueDate = getStringValue(snapshot, "dueDate");
                String createdBy = getStringValue(snapshot, "createdByName");

                tvTitle.setText(title);

                if (desc.isEmpty()) {
                    tvDesc.setText("No description provided.");
                    tvDesc.setTextColor(getResources().getColor(R.color.text_hint));
                } else {
                    tvDesc.setText(desc);
                    tvDesc.setTextColor(getResources().getColor(R.color.text_primary));
                }

                tvAssignee.setText(assignee.isEmpty() ? "Unassigned" : assignee);
                tvDueDate.setText(dueDate.isEmpty() ? "No Date" : dueDate);
                tvCreatedBy.setText(createdBy.isEmpty() ? "Unknown" : createdBy);

                // Priority UI
                tvPriority.setBackgroundResource(R.drawable.bg_status_chip);
                if ("high".equals(priority)) {
                    tvPriority.setText("🔴 High");
                    tvPriority.getBackground().setTint(getResources().getColor(R.color.priority_high_bg));
                    tvPriority.setTextColor(getResources().getColor(R.color.priority_high));
                } else if ("medium".equals(priority)) {
                    tvPriority.setText("🟡 Medium");
                    tvPriority.getBackground().setTint(getResources().getColor(R.color.priority_medium_bg));
                    tvPriority.setTextColor(getResources().getColor(R.color.priority_medium));
                } else {
                    tvPriority.setText("🟢 Low");
                    tvPriority.getBackground().setTint(getResources().getColor(R.color.priority_low_bg));
                    tvPriority.setTextColor(getResources().getColor(R.color.priority_low));
                }

                // Status UI
                tvStatus.setBackgroundResource(R.drawable.bg_status_chip);
                btnStatusTodo.setAlpha(0.5f);
                btnStatusProgress.setAlpha(0.5f);
                btnStatusDone.setAlpha(0.5f);

                if ("in_progress".equals(status)) {
                    tvStatus.setText("In Progress");
                    tvStatus.getBackground().setTint(getResources().getColor(R.color.status_in_progress));
                    tvStatus.setTextColor(getResources().getColor(R.color.white));
                    btnStatusProgress.setAlpha(1.0f);
                } else if ("completed".equals(status)) {
                    tvStatus.setText("Completed");
                    tvStatus.getBackground().setTint(getResources().getColor(R.color.status_completed));
                    tvStatus.setTextColor(getResources().getColor(R.color.white));
                    btnStatusDone.setAlpha(1.0f);
                } else {
                    tvStatus.setText("To Do");
                    tvStatus.getBackground().setTint(getResources().getColor(R.color.status_todo));
                    tvStatus.setTextColor(getResources().getColor(R.color.white));
                    btnStatusTodo.setAlpha(1.0f);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TaskDetailActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };
        taskRef.addValueEventListener(taskListener);
    }

    private void updateStatus(String newStatus) {
        if (taskRef != null) {
            taskRef.child("status").setValue(newStatus).addOnSuccessListener(aVoid -> {
                Toast.makeText(this, "Status updated", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private String getStringValue(DataSnapshot snapshot, String key) {
        Object val = snapshot.child(key).getValue();
        return val != null ? val.toString() : "";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (taskRef != null && taskListener != null) {
            taskRef.removeEventListener(taskListener);
        }
    }
}
