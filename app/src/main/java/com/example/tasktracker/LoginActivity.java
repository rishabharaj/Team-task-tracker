package com.example.tasktracker;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final int RC_SIGN_IN = 9001;

    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseAuth mAuth;
    private ProgressBar progressLogin;
    private View btnGoogleSignIn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseAuth.getInstance();

        // Check if user is already signed in
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            goToDashboard();
            return;
        }

        setContentView(R.layout.activity_login);

        progressLogin = findViewById(R.id.progressLogin);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);

        // Configure Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        btnGoogleSignIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signInWithGoogle();
            }
        });
    }

    private void signInWithGoogle() {
        progressLogin.setVisibility(View.VISIBLE);
        btnGoogleSignIn.setEnabled(false);

        // Sign out from any previous Google session first to always show account picker
        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                Log.d(TAG, "Google Sign-In success, email: " + account.getEmail());
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                progressLogin.setVisibility(View.GONE);
                btnGoogleSignIn.setEnabled(true);

                String errorMsg;
                switch (e.getStatusCode()) {
                    case CommonStatusCodes.SIGN_IN_REQUIRED:
                        errorMsg = "Sign in required. Please try again.";
                        break;
                    case CommonStatusCodes.NETWORK_ERROR:
                        errorMsg = "Network error. Check your internet connection.";
                        break;
                    case CommonStatusCodes.CANCELED:
                        errorMsg = "Sign-in cancelled.";
                        break;
                    case 12500:
                        errorMsg = "Sign-In failed: SHA-1 fingerprint not configured in Firebase. Please update Firebase Console.";
                        break;
                    case 10:
                        errorMsg = "Developer error: Check SHA-1 fingerprint & google-services.json configuration.";
                        break;
                    default:
                        errorMsg = "Sign-In failed (code " + e.getStatusCode() + "): " + e.getMessage();
                        break;
                }
                Log.e(TAG, "Google Sign-In failed with code: " + e.getStatusCode(), e);
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "Firebase auth success");
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                saveUserToDatabase(user);
                            }
                        } else {
                            progressLogin.setVisibility(View.GONE);
                            btnGoogleSignIn.setEnabled(true);
                            String errorMsg = task.getException() != null ?
                                    task.getException().getMessage() : "Unknown error";
                            Log.e(TAG, "Firebase auth failed: " + errorMsg, task.getException());
                            Toast.makeText(LoginActivity.this,
                                    "Authentication failed: " + errorMsg, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void saveUserToDatabase(FirebaseUser user) {
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("tasktracker")
                .child("users")
                .child(user.getUid());

        Map<String, Object> userData = new HashMap<>();
        userData.put("name", user.getDisplayName());
        userData.put("email", user.getEmail());
        if (user.getPhotoUrl() != null) {
            userData.put("photoUrl", user.getPhotoUrl().toString());
        }

        userRef.updateChildren(userData).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                progressLogin.setVisibility(View.GONE);
                goToDashboard();
            }
        });
    }

    private void goToDashboard() {
        Intent intent = new Intent(this, SelectTeamActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
