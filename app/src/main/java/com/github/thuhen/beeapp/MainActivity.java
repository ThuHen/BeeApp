package com.github.thuhen.beeapp;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {
private Button mDriver, mCustomer;
    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        mDriver= findViewById(R.id.driver);
        mCustomer=findViewById(R.id.customer);

        mDriver.setOnClickListener(view -> {
            Intent intent = new Intent (MainActivity.this, DriverLoginActivity.class);
            startActivity(intent);
            finish();
           // return;
        });
        mCustomer.setOnClickListener(view -> {
            Intent intent = new Intent (MainActivity.this, CustomerLoginActivity.class);
            startActivity(intent);
            finish();
            //return;
        });
    }
}