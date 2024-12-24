package com.github.thuhen.beeapp.HistoryRecycleView;
public class HistoryObject {
    private String rideId;
    private String time;

    public HistoryObject(String rideId, String time) {
        this.rideId = rideId;
        this.time= time;// Lưu giá trị String
    }

    public String getRideId() {
        return rideId; // Trả về giá trị String
    }
    public void setRideId(String rideId) {
        this.rideId = rideId;
    }

    public String getTime() {
        return time; // Trả về giá trị String
    }
    public void setTime(String time) {
        this.time = this.time;
    }
}
