package com.github.thuhen.beeapp;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.util.Log; // Import lớp Log
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentTransaction;




public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity"; // Đặt tag cho Log
    private Button mDriver, mCustomer;


    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called"); // Log khi onCreate được gọi

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "setContentView success"); // Log khi giao diện được thiết lập thành công

        // Xử lý window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            Log.d(TAG, "WindowInsets applied"); // Log khi window insets được áp dụng
            return insets;
        });

        // Gắn kết các nút
        mDriver = findViewById(R.id.driver);
        mCustomer = findViewById(R.id.customer);

        if (mDriver != null && mCustomer != null) {
            Log.d(TAG, "Buttons initialized successfully"); // Log khi các nút được khởi tạo

            mDriver.setOnClickListener(view -> {
                Log.d(TAG, "Driver button clicked"); // Log khi nút Driver được nhấn
                Intent intent = new Intent(MainActivity.this, DriverLoginActivity.class);
                startActivity(intent);
                //finish();
            });

            mCustomer.setOnClickListener(view -> {
                Log.d(TAG, "Customer button clicked"); // Log khi nút Customer được nhấn
                Intent intent = new Intent(MainActivity.this, CustomerLoginActivity.class);
                startActivity(intent);
                //finish();
            });
        } else {
            Log.e(TAG, "Buttons not found! Check your layout file."); // Log lỗi nếu các nút không được tìm thấy
        }

    }
}
