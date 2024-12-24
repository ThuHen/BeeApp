package com.github.thuhen.beeapp.HistoryRecycleView;
public class HistoryObject {
    private String rideId;

    public HistoryObject(String rideId) {
        this.rideId = rideId; // Lưu giá trị String
    }

    public String getRideId() {
        return rideId; // Trả về giá trị String
    }

    public void setRideId(String rideId) {
        this.rideId = rideId;
    }
}
