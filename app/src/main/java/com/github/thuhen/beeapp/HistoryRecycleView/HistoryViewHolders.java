package com.github.thuhen.beeapp.HistoryRecycleView;
import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.github.thuhen.beeapp.R;

public class HistoryViewHolders extends RecyclerView.ViewHolder implements View.OnClickListener {
    public TextView rideId;
    //Constructor
    public HistoryViewHolders(View itemView){
        super(itemView);
        itemView.setOnClickListener(this);
        rideId= itemView.findViewById(R.id.rideId);
    }
    @Override
    public void onClick(View view) {
    }
}