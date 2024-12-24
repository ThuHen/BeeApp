package com.github.thuhen.beeapp;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import com.github.thuhen.beeapp.HistoryRecycleView.HistoryAdapter;
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
    @SuppressLint("WrongViewCast")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);// mà em làm sao h nó hiện cái tuần trc h sao
        setContentView(R.layout.activity_history);

        mHistoryRecyclerView= findViewById(R.id.history_scroll);
        mHistoryRecyclerView.setNestedScrollingEnabled(false);
        mHistoryRecyclerView.setHasFixedSize(true);
        mHistoryLayoutManager = new LinearLayoutManager(HistoryActivity.this);
        mHistoryRecyclerView.setLayoutManager(mHistoryLayoutManager);
        mHistoryAdapter= new HistoryAdapter(getDataSetHistory(),HistoryActivity.this);
        mHistoryRecyclerView.setAdapter(mHistoryAdapter);


        customerOrDriver = getIntent().getExtras() != null ? getIntent().getExtras().getString("customerOrDriver") : null;
        Log.d("HistoryActivity", "customerOrDriver: " + customerOrDriver);
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        getUserHistoryIds();


//        for (int i=0;i<100;i++){
//            HistoryAdapter.HistoryObject obj= new HistoryAdapter.HistoryObject(Integer.toString(i));
//            resultHistory.add(obj);
//        }
//        mHistoryAdapter.notifyDataSetChanged();
    }

    private void getUserHistoryIds() {
        DatabaseReference userHistoryDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child(customerOrDriver).child(userId).child("history");
        userHistoryDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()){
                    Log.d("HistoryActivity", "History keys found: " + snapshot.getValue());
                    for(DataSnapshot history : snapshot.getChildren()){
                        FetchRideInformation(history.getKey());
                    }
                }else {
                    // Không có dữ liệu, log để kiểm tra
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
        DatabaseReference historyDatabase = FirebaseDatabase.getInstance().getReference().child("history").child(rideKey);
        historyDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()){
                    Log.d("HistoryActivity", "Ride found: " + snapshot.getValue());
                    String rideId = snapshot.getKey();
                    HistoryAdapter.HistoryObject obj= new HistoryAdapter.HistoryObject(rideId);
                    resultHistory.add(obj);
                    mHistoryAdapter.notifyDataSetChanged();
                }else {
                    Log.d("HistoryActivity", "Ride data not found for key: " + rideKey);
                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("HistoryActivity", "Database error: " + error.getMessage());
            }
        });
    }

    private ArrayList resultHistory= new ArrayList<HistoryAdapter.HistoryObject>();
    private ArrayList<HistoryAdapter.HistoryObject> getDataSetHistory(){
        return resultHistory;
    }
}