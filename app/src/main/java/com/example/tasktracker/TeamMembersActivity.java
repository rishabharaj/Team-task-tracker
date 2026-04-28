package com.example.tasktracker;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class TeamMembersActivity extends AppCompatActivity {

    private ListView lvMembers;
    private TextView tvTeamInfo, tvCopyCode, tvEmpty;
    private Button btnBack;

    private String teamCode, teamName;
    private DatabaseReference membersRef;
    private ValueEventListener membersListener;

    private ArrayList<Map<String, String>> membersList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_team_members);

        teamCode = getIntent().getStringExtra("teamCode");
        teamName = getIntent().getStringExtra("teamName");

        if (teamCode == null) {
            finish();
            return;
        }

        lvMembers = findViewById(R.id.lvMembers);
        tvTeamInfo = findViewById(R.id.tvTeamInfo);
        tvCopyCode = findViewById(R.id.tvCopyCode);
        tvEmpty = findViewById(R.id.tvEmpty);
        btnBack = findViewById(R.id.btnBack);

        tvTeamInfo.setText("Team: " + (teamName != null ? teamName : teamCode) + " • Code: " + teamCode);
        tvCopyCode.setText(teamCode);

        // Copy code on tap
        tvCopyCode.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Team Code", teamCode);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Team code copied! 📋", Toast.LENGTH_SHORT).show();
        });

        btnBack.setOnClickListener(v -> finish());

        membersList = new ArrayList<>();
        loadMembers();
    }

    private void loadMembers() {
        membersRef = FirebaseDatabase.getInstance()
                .getReference("tasktracker")
                .child("teams")
                .child(teamCode)
                .child("members");

        membersListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                membersList.clear();

                for (DataSnapshot child : snapshot.getChildren()) {
                    Map<String, String> member = new HashMap<>();
                    member.put("id", child.getKey());
                    member.put("name", getStr(child, "name"));
                    member.put("email", getStr(child, "email"));
                    member.put("role", getStr(child, "role"));
                    member.put("photoUrl", getStr(child, "photoUrl"));
                    membersList.add(member);
                }

                if (membersList.isEmpty()) {
                    lvMembers.setVisibility(View.GONE);
                    tvEmpty.setVisibility(View.VISIBLE);
                } else {
                    lvMembers.setVisibility(View.VISIBLE);
                    tvEmpty.setVisibility(View.GONE);
                    MemberAdapter adapter = new MemberAdapter(TeamMembersActivity.this, membersList);
                    lvMembers.setAdapter(adapter);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TeamMembersActivity.this,
                        "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };

        membersRef.addValueEventListener(membersListener);
    }

    private String getStr(DataSnapshot snap, String key) {
        Object val = snap.child(key).getValue();
        return val != null ? val.toString() : "";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (membersListener != null && membersRef != null) {
            membersRef.removeEventListener(membersListener);
        }
    }

    // ========================
    // Custom Member Adapter
    // ========================
    private static class MemberAdapter extends ArrayAdapter<Map<String, String>> {
        private final Context context;
        private final ArrayList<Map<String, String>> members;

        // Colors for avatar backgrounds
        private static final int[] AVATAR_COLORS = {
            0xFF1A73E8, // Blue
            0xFF00BCD4, // Cyan
            0xFF009688, // Teal
            0xFFF44336, // Red
            0xFFFF9800, // Orange
            0xFF4CAF50, // Green
            0xFF9C27B0, // Purple
            0xFFE91E63, // Pink
        };

        public MemberAdapter(Context context, ArrayList<Map<String, String>> members) {
            super(context, R.layout.item_member, members);
            this.context = context;
            this.members = members;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_member, parent, false);
            }

            Map<String, String> member = members.get(position);

            TextView tvAvatar = convertView.findViewById(R.id.tvAvatar);
            de.hdodenhof.circleimageview.CircleImageView ivMemberPic = convertView.findViewById(R.id.ivMemberPic);
            TextView tvName = convertView.findViewById(R.id.tvMemberName);
            TextView tvEmail = convertView.findViewById(R.id.tvMemberEmail);
            TextView tvRole = convertView.findViewById(R.id.tvRole);

            String name = member.get("name");
            if (name != null && !name.isEmpty()) {
                tvAvatar.setText(String.valueOf(name.charAt(0)).toUpperCase());
                tvName.setText(name);
            } else {
                tvAvatar.setText("?");
                tvName.setText("Unknown");
            }

            // Set avatar color based on position
            int colorIndex = position % AVATAR_COLORS.length;
            tvAvatar.getBackground().setTint(AVATAR_COLORS[colorIndex]);
            
            // Load photo if available
            String photoUrl = member.get("photoUrl");
            if (photoUrl != null && !photoUrl.isEmpty() && photoUrl.startsWith("http")) {
                ivMemberPic.setVisibility(View.VISIBLE);
                tvAvatar.setVisibility(View.GONE);
                com.bumptech.glide.Glide.with(context).load(photoUrl).into(ivMemberPic);
            } else {
                ivMemberPic.setVisibility(View.GONE);
                tvAvatar.setVisibility(View.VISIBLE);
            }

            tvEmail.setText(member.get("email"));

            String role = member.get("role");
            if ("admin".equals(role)) {
                tvRole.setText("Admin");
                tvRole.setTextColor(context.getResources().getColor(R.color.primary));
            } else {
                tvRole.setText("Member");
                tvRole.setTextColor(context.getResources().getColor(R.color.text_secondary));
            }

            return convertView;
        }
    }
}
