package com.github.thuhen.beeapp;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

import com.github.thuhen.beeapp.HistoryRecycleView.HistoryAdapter;
import com.github.thuhen.beeapp.HistoryRecycleView.HistoryObject;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class HistoryActivity extends AppCompatActivity {
    private String customerOrDriver, userId;
    private RecyclerView mHistoryRecyclerView;
    private RecyclerView.Adapter mHistoryAdapter;
    private RecyclerView.LayoutManager mHistoryLayoutManager;

    private ArrayList<HistoryObject> resultHistory = new ArrayList<>();

    @SuppressLint({"WrongViewCast", "NotifyDataSetChanged"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_history);


        // Khởi tạo RecyclerView
        mHistoryRecyclerView = findViewById(R.id.history_scroll);
        // mHistoryRecyclerView.setNestedScrollingEnabled(true);
        mHistoryRecyclerView.setHasFixedSize(true);

        mHistoryLayoutManager = new LinearLayoutManager(HistoryActivity.this);
        // Đặt LayoutManager cho RecyclerView
        mHistoryRecyclerView.setLayoutManager(mHistoryLayoutManager);

        mHistoryAdapter = new HistoryAdapter(getDataSetHistory(), HistoryActivity.this);
        mHistoryRecyclerView.setAdapter(mHistoryAdapter);

//        for (int i = 0; i < 100; i++) {
//            HistoryObject obj = new HistoryObject(Integer.toString(i));
//            resultHistory.add(obj);
//        }
//        mHistoryAdapter.notifyDataSetChanged();

        // Nhận dữ liệu từ Intent
        customerOrDriver = getIntent().getExtras() != null ? getIntent().getExtras().getString("customerOrDriver") : null;
        Log.d("HistoryActivity", "customerOrDriver: " + customerOrDriver);
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Lấy dữ liệu lịch sử
        getUserHistoryIds();
    }

    private void getUserHistoryIds() {
        DatabaseReference userHistoryDatabase = FirebaseDatabase.getInstance().getReference()
                .child("Users").child(customerOrDriver).child(userId).child("history");

        userHistoryDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Log.d("HistoryActivity", "History keys found: " + snapshot.getValue());
                    for (DataSnapshot history : snapshot.getChildren()) {
                        FetchRideInformation(history.getKey());
                    }
                } else {
                    Log.d("HistoryActivity", "No history data found for user.");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("HistoryActivity", "Database error: " + error.getMessage());
            }
        });
    }

    private void FetchRideInformation(String rideKey) {
        DatabaseReference historyDatabase = FirebaseDatabase.getInstance().getReference()
                .child("history").child(rideKey);
        historyDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Log.d("HistoryActivity", "Ride found: " + snapshot.getValue());
                    String rideId = snapshot.getKey();
                    Long timestamp = 0L;
                    for (DataSnapshot child : snapshot.getChildren()) {
                        if (child.getKey().equals("timestamp")) {
                            timestamp = Long.valueOf(child.getValue().toString());
                        }
                    }
                    HistoryObject obj = new HistoryObject(rideId, getDate(timestamp));
                    resultHistory.add(obj);
                    mHistoryAdapter.notifyDataSetChanged();
                } else {
                    Log.d("HistoryActivity", "Ride data not found for key: " + rideKey);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("HistoryActivity", "Database error: " + error.getMessage());
            }
        });
    }

    private String getDate(Long timestamp) {
        Calendar cal = Calendar.getInstance(Locale.getDefault());
        cal.setTimeInMillis(timestamp * 1000);
        String date = DateFormat.format("dd-MM-yyyy hh:mm", cal).toString();
        return date;
    }

    private ArrayList<HistoryObject> getDataSetHistory() {
        return resultHistory;
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d("HistoryActivity", "onStart: Activity started.");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("HistoryActivity", "onResume: Activity resumed.");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d("HistoryActivity", "onPause: Activity paused.");
        // Dừng cập nhật giao diện hoặc listener nếu cần
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d("HistoryActivity", "onStop: Activity stopped.");
        // Giải phóng tài nguyên nếu cần
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("HistoryActivity", "onDestroy: Activity destroyed.");
        // Xóa các listener Firebase nếu cần để tránh rò rỉ bộ nhớ
    }


}
