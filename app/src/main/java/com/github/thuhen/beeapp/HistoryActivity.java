package com.github.thuhen.beeapp;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import com.github.thuhen.beeapp.HistoryRecycleView.HistoryAdapter;

public class HistoryActivity extends AppCompatActivity {
    private RecyclerView mHistoryRecyclerView;
    private RecyclerView.Adapter mHistoryAdapter;
    private RecyclerView.LayoutManager mHistoryLayoutManager;
    @SuppressLint("WrongViewCast")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_history);

        mHistoryRecyclerView= (RecyclerView) findViewById(R.id.history_scroll);
        mHistoryRecyclerView.setNestedScrollingEnabled(true);
        mHistoryRecyclerView.setHasFixedSize(true);
        mHistoryLayoutManager = new LinearLayoutManager(HistoryActivity.this);
        mHistoryRecyclerView.setLayoutManager(mHistoryLayoutManager);
        mHistoryAdapter= new HistoryAdapter(getDataSetHistory(),HistoryActivity.this);
        mHistoryRecyclerView.setAdapter(mHistoryAdapter);

        for (int i=0;i<100;i++){
            HistoryAdapter.HistoryObject obj= new HistoryAdapter.HistoryObject(Integer.toString(i));
            resultHistory.add(obj);
        }
        mHistoryAdapter.notifyDataSetChanged();
    }

    private ArrayList resultHistory= new ArrayList<HistoryAdapter.HistoryObject>();
    private ArrayList<HistoryAdapter.HistoryObject> getDataSetHistory(){
        return resultHistory;
    }
}