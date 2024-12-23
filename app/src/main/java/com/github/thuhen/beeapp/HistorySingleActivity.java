package com.github.thuhen.beeapp;


import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;


import java.util.Calendar;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class HistorySingleActivity extends AppCompatActivity implements OnMapReadyCallback {
    private String TAG = "HistorySingleActivity";
    private String rideId, currentUserId, customerId, driverId, userDriverOrCustomer;

    private TextView rideLocation;
    private TextView rideDistance;
    private TextView rideDate;
    private TextView userName;
    private TextView userPhone;

    private ImageView userImage;

    private DatabaseReference historyRideInfoDb;
    private LatLng destinationLatLng, pickupLatLng;
    private GoogleMap mMap;
    private SupportMapFragment mMapFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_single);

        rideId = getIntent().getExtras().getString("rideId");

        mMapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);

        mMapFragment.getMapAsync(this);

        rideLocation = (TextView) findViewById(R.id.rideLocation);
        rideDistance = (TextView) findViewById(R.id.rideDistance);
        rideDate = (TextView) findViewById(R.id.rideDate);
        userName = (TextView) findViewById(R.id.userName);
        userPhone = (TextView) findViewById(R.id.userPhone);

        userImage = (ImageView) findViewById(R.id.userImage);

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        historyRideInfoDb = FirebaseDatabase.getInstance().getReference().child("history").child(rideId);

        getRideInformation();
    }

    private void getRideInformation() {
        historyRideInfoDb.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        //get customerId
                        if (child.getKey().equals("customerId")) {
                            Log.d(TAG, "onDataChange: customerId: ");
                            customerId = child.getValue().toString();
                            if (customerId.equals(currentUserId)) {
                                userDriverOrCustomer = "Customers";
                                getUserInformation(userDriverOrCustomer, customerId);

                            }

                        }
                        //get driverId
                        if (child.getKey().equals("driverId")) {
                            Log.d(TAG, "onDataChange: driverId: ");
                            driverId = child.getValue().toString();
                            if (driverId.equals(currentUserId)) {
                                userDriverOrCustomer = "Drivers";
                                getUserInformation(userDriverOrCustomer, driverId);
                            }

                        }

                        if (child.getKey().equals("timestamp")) {
                            rideDate.setText(getDate(Long.valueOf(child.getValue().toString())));
                        }
                        if (child.getKey().equals("destination")) {
                            String latitude = child.child("latitude").getValue().toString();
                            String longitude = child.child("longitude").getValue().toString();

                            // Kết hợp latitude và longitude thành chuỗi tọa độ
                            String coordinates = "Latitude: " + latitude + "\n" + "Longitude: " + longitude;
                            rideLocation.setText(coordinates); // Hiển thị tọa độ lên TextView
                        }


                        if (child.getKey().equals("location")) {
                            pickupLatLng = new LatLng(Double.valueOf(child.child("from")
                                    .child("lat").getValue().toString()), Double.valueOf(child.child("from").child("long").getValue().toString()));
                            destinationLatLng = new LatLng(Double.valueOf(child.child("to")
                                    .child("lat").getValue().toString()), Double.valueOf(child.child("to").child("long").getValue().toString()));

                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void getUserInformation(String otherUserDriverOrCustomer, String otherUserId) {
        DatabaseReference mOtherUserDB = FirebaseDatabase.getInstance().getReference()
                .child("Users").child(otherUserDriverOrCustomer).child(otherUserId);
        mOtherUserDB.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Map<String, Object> map = (Map<String, Object>) snapshot.getValue();
                    assert map != null;
                    if (map.get("name") != null) {
                        userName.setText(map.get("name").toString());
                    }
                    if (map.get("phone") != null) {
                        userName.setText(map.get("phone").toString());
                    }
                    if (otherUserDriverOrCustomer.equals("Drivers"))
                        userImage.setImageResource(R.mipmap.icon_default_driver);
                    if (otherUserDriverOrCustomer.equals("Customers"))
                        userImage.setImageResource(R.mipmap.icon_default_user);

                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private String getDate(Long timestamp) {
        Calendar cal = Calendar.getInstance(Locale.getDefault());
        cal.setTimeInMillis(timestamp * 1000);
        String date = DateFormat.format("dd-MM-yyyy hh:mm", cal).toString();
        return date;
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
    }

}