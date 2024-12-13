package com.github.thuhen.beeapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Firebase;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.FirebaseDatabaseKtxRegistrar;

import java.util.Objects;

public class DriverLoginActivity extends AppCompatActivity {
    private EditText editTextMail , editTextPassword;
    private Button mLogin, mRegistration;

    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener firebaseAuthListener;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_driver_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        editTextMail = findViewById(R.id.txt_email);
        editTextPassword = findViewById(R.id.txt_password);
        mLogin = findViewById(R.id.btn_login);
        mRegistration = findViewById(R.id.btn_registration);

        mAuth = FirebaseAuth.getInstance();
///dang nhap tu dong
        firebaseAuthListener= new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if(user!=null){
                    Intent intent = new Intent(DriverLoginActivity.this,DriverMapActivity.class);
                    startActivity(intent);
                    finish();
                    //return;
                }
            }

        };

        mRegistration.setOnClickListener(v -> {
            final String email = editTextMail.getText().toString();
            final String password = editTextPassword.getText().toString();
            mAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener
                    (DriverLoginActivity.this, task -> {
                        if (!task.isSuccessful()) {
                            Toast.makeText(DriverLoginActivity.this, R.string.sign_up_error,
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            String user_id = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();
                            DatabaseReference current_user_db = FirebaseDatabase.getInstance()
                                    .getReference().child("Users").child("Drivers")
                                    .child(user_id);
                            current_user_db.setValue(true);
                            Toast.makeText(DriverLoginActivity.this, R.string.sign_up_sucessful,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });

        });

        mLogin.setOnClickListener(view -> {
            final String email = editTextMail.getText().toString();
            final String password = editTextPassword.getText().toString();
            mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener
                    (DriverLoginActivity.this, new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete(@NonNull Task<AuthResult> task) {
                            if (!task.isSuccessful()) {
                                Toast.makeText(DriverLoginActivity.this, R.string.sign_in_error,
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(DriverLoginActivity.this, R.string.sign_in_sucessful,
                                        Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(DriverLoginActivity.this, DriverMapActivity.class);
                                startActivity(intent);
                                finish();
                            }
                        }
                    });
        });
    }
    @Override
    protected void onStart(){
        super.onStart();
        //mAuth.addAuthStateListener(firebaseAuthListener);
    }
    @Override
    protected void onStop(){
        super.onStop();
        mAuth.removeAuthStateListener(firebaseAuthListener);
    }
}
