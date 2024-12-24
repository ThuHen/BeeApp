package com.github.thuhen.beeapp.HistoryRecycleView;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;

import com.github.thuhen.beeapp.HistorySingleActivity;
import com.github.thuhen.beeapp.R;

public class HistoryViewHolders extends RecyclerView.ViewHolder implements View.OnClickListener {
    public TextView rideId;
    public TextView time;

    //Constructor
    public HistoryViewHolders(View itemView){
        super(itemView);
        itemView.setOnClickListener(this);
        rideId= itemView.findViewById(R.id.rideId);
        time= itemView.findViewById(R.id.time);
    }
    @Override
    public void onClick(View view) {
        Intent intent = new Intent(view.getContext(), HistorySingleActivity.class);
        Bundle b = new Bundle();
        b.putString("rideId", rideId.getText().toString());
        intent.putExtras(b);
        view.getContext().startActivity(intent);
    }
}