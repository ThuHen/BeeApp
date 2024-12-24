package com.github.thuhen.beeapp.HistoryRecycleView;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.github.thuhen.beeapp.R;
import java.util.List;
public class HistoryAdapter extends RecyclerView.Adapter<HistoryViewHolders> {
    private List<HistoryObject> itemList;
    private Context context;
    public HistoryAdapter(List<HistoryObject> itemList, Context context){
        this.itemList=itemList;
        this.context= context;
    }
    @NonNull
    @Override
    public HistoryViewHolders onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View layoutView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history,null,false);
        RecyclerView.LayoutParams layoutParams= new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutView.setLayoutParams(layoutParams);
        HistoryViewHolders rcv = new HistoryViewHolders(layoutView);
        return rcv;
    }
    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolders holder, int position) {
        holder.rideId.setText(itemList.get(position).getRideId());
    }
    @Override
    public int getItemCount() {
        return itemList.size();
    }

    public static class HistoryObject {
        public HistoryObject(String string) {
        }

        public int getRideId() {
            return 0;
        }
    }
}