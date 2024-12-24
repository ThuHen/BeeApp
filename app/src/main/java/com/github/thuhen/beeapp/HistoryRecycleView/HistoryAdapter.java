package com.github.thuhen.beeapp.HistoryRecycleView;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.thuhen.beeapp.R;
import com.github.thuhen.beeapp.HistoryRecycleView.HistoryViewHolders;
import com.github.thuhen.beeapp.HistoryRecycleView.HistoryObject;

import java.util.ArrayList;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryViewHolders> {
    private ArrayList<HistoryObject> itemList;
    private Context context;

    //Constructor
    public HistoryAdapter(ArrayList<HistoryObject> itemList, Context context) {
        this.itemList = itemList;
        this.context = context;
    }

    @NonNull
    @Override
    // Tạo ViewHolder
    public HistoryViewHolders onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Gắn layout cho View Holder
        View layoutView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        // Tạo ViewHolder
        RecyclerView.LayoutParams layoutParams = new RecyclerView.LayoutParams(ViewGroup
                .LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        // Đặt chiều cao cho item
        layoutView.setLayoutParams(layoutParams);
        // Gắn layout cho View Holder
        HistoryViewHolders rcv = new HistoryViewHolders(layoutView);
        // Trả về ViewHolder
        return rcv;
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolders holder, int position) {
        // Gắn dữ liệu cho View Holder
        holder.rideId.setText(itemList.get(position).getRideId());
        holder.time.setText(itemList.get(position).getTime());
    }

    @Override
    public int getItemCount() {
        // Số lượng item
        return itemList.size();

    }
//    // ViewHolder
//    public static class HistoryObject {
//        // Thuộc tính
//        public HistoryObject(String string) {
//        }
//        // Getter
//        public int getRideId() {
//            return 0;
//        }
//    }
}