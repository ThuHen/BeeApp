package com.github.thuhen.beeapp;

import static com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.github.thuhen.beeapp.databinding.ActivityDriverMapBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DriverMapActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private ActivityDriverMapBinding binding;

    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;
    private Button mLogout;
    private String customerId = "";

    private static final int LOCATION_REQUEST_CODE = 100;
    private static final String TAG = "DriverMapActivity"; // Tag dùng trong Logcat

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityDriverMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Log.d(TAG, "onCreate: DriverMapActivity started");

        // Load map fragment
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.id_map);
        if (mapFragment != null) {
            Log.d(TAG, "onCreate: Map fragment found");
            mapFragment.getMapAsync(this);
        } else {
            Log.e(TAG, "onCreate: Map fragment is null");
        }

        // Initialize location services
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        checkLocationPermission();

        mLogout = (Button) findViewById(R.id.logout);
        mLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(DriverMapActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
                return;

            }
        });
        getAssignedCustomer();
    }


    private void getAssignedCustomer(){
        String driverId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference assignedCustomerRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverId);
        assignedCustomerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists()){
                    Map<String, Object> map = (Map<String, Object>) snapshot.getValue();
                    if(map.get("customerRideId") != null){
                        customerId = map.get("customerRideId").toString();
                        getAssignedCustomerPickupLocation();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

    }

    private void getAssignedCustomerPickupLocation(){
        DatabaseReference assignedCustomerPickupLocationRef = FirebaseDatabase.getInstance().getReference().child("customerRequest").child(customerId).child("i");
        assignedCustomerPickupLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    try {
                        // Ép kiểu thành Map để trích xuất thông tin
                        List<Object> map = (List<Object>) snapshot.getValue();
                        if (map != null) {
                            double locationLat = 0;
                            double locationLng = 0;

                            // Kiểm tra và lấy tọa độ
                            if (map.get(0) != null) {
                                locationLat = Double.parseDouble(map.get(0).toString());
                            } else {
                                Log.e(TAG, "onDataChange: Latitude is null");
                            }

                            if (map.get(1) != null) { // Chỉnh `map.get(1)` thay vì `map.get(0)` để lấy longitude
                                locationLng = Double.parseDouble(map.get(1).toString());
                            } else {
                                Log.e(TAG, "onDataChange: Longitude is null");
                            }

                            // Tạo marker chỉ khi tọa độ hợp lệ
                            if (locationLat != 0 && locationLng != 0) {
                                LatLng driverLatLng = new LatLng(locationLat, locationLng);
                                mMap.addMarker(new MarkerOptions()
                                        .position(driverLatLng)
                                        .title("Pickup Location"));
                                Log.d(TAG, "onDataChange: Added marker at Lat: " + locationLat + ", Lng: " + locationLng);
                            } else {
                                Log.w(TAG, "onDataChange: Invalid location coordinates");
                            }
                        } else {
                            Log.e(TAG, "onDataChange: Data map is null");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "onDataChange: Error parsing location data", e);
                    }
                } else {
                    Log.w(TAG, "onDataChange: Snapshot does not exist");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void checkLocationPermission() {
        Log.d(TAG, "checkLocationPermission: Checking permissions");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "checkLocationPermission: Permissions not granted, requesting permissions");
            requestForPermissions();
        } else {
            Log.d(TAG, "checkLocationPermission: Permissions granted, starting location updates");
            getUserLocation();
        }
    }

    private void requestForPermissions() {
        Log.d(TAG, "requestForPermissions: Requesting location permissions");
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "onRequestPermissionsResult: Permissions granted");
                Toast.makeText(this, R.string.location_permission_accepted, Toast.LENGTH_SHORT).show();
                getUserLocation();
            } else {
                Log.e(TAG, "onRequestPermissionsResult: Permissions denied");
                Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean hasMovedCamera = false;
    private Circle mUserLocationCircle;
    @SuppressLint("MissingPermission")
    private void getUserLocation() {
        Log.d(TAG, "getUserLocation: Starting location updates");

        LocationRequest locationRequest = new LocationRequest.Builder(PRIORITY_HIGH_ACCURACY, 8000) // 8 giây
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(10000) // 10 giây
                .setMaxUpdateDelayMillis(10000) // 10 giây
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Log.d(TAG, "onLocationResult: Received location update");

                Location location = locationResult.getLastLocation();
                if (location != null) {
                    Log.d(TAG, "onLocationResult: Location - Lat: " + location.getLatitude() + ", Lng: " + location.getLongitude());
                    LatLng userLocation = new LatLng(location.getLatitude(), location.getLongitude());

                    // Chỉ di chuyển camera một lần khi lần đầu nhận được vị trí
                    if (!hasMovedCamera) {
                        // Xóa circle cũ nếu có (nếu đã có circle từ trước)
                        if (mUserLocationCircle != null) {
                            mUserLocationCircle.remove();
                        }

                        // Tạo một circle mới cho vị trí người dùng
                        mUserLocationCircle = mMap.addCircle(new CircleOptions()
                                .center(userLocation)  // Vị trí trung tâm của circle
                                .radius(50)  // Bán kính của circle (đơn vị là mét)
                                .fillColor(0x550000FF)  // Màu xanh da trời nhạt với độ trong suốt
                                .strokeColor(0xFF0000FF)  // Màu xanh da trời đậm, không trong suốt
                                .strokeWidth(1));  // Độ rộng viền của circle

                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 18)); // Di chuyển camera đến vị trí mới
                        hasMovedCamera = true; // Đánh dấu là đã di chuyển camera
                    }

                    // Cập nhật vị trí người dùng trong Firebase
                    String userId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
                    DatabaseReference userLocationRef = FirebaseDatabase.getInstance().getReference("driverAvailable");

                    GeoFire geoFire = new GeoFire(userLocationRef);
                    geoFire.setLocation(userId, new GeoLocation(location.getLatitude(), location.getLongitude()));

                } else {
                    Log.e(TAG, "onLocationResult: LocationResult is null");
                    return;
                }
            }
        };

// Bắt đầu yêu cầu cập nhật vị trí
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    //fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    private void stopLocationUpdates() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hasMovedCamera = false; // Reset lại cờ khi Activity được mở lại
        checkLocationPermission(); // Bắt đầu khi Activity được mở lại
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates(); // Dừng khi activity bị ẩn
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        Log.d(TAG, "onMapReady: Map is ready");
        mMap = googleMap;
        // nút + và - zoom
        mMap.getUiSettings().setZoomControlsEnabled(true);

        // mMap.getUiSettings().setMyLocationButtonEnabled(true);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "onMapReady: Location permission granted, enabling My Location");
            mMap.setMyLocationEnabled(true);
        } else {
            Log.w(TAG, "onMapReady: Location permission not granted");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop: Removing location updates");
        if (fusedLocationProviderClient != null && locationCallback != null) {
            stopLocationUpdates();
        }

        // Xóa vị trí của người dùng khỏi Firebase
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference userLocationRef = FirebaseDatabase.getInstance().getReference("driverAvailable");
        GeoFire geoFire = new GeoFire(userLocationRef);

        geoFire.removeLocation(userId, (key, error) -> {
            if (error != null) {
                Log.e(TAG, "Failed to remove location: " + error.getMessage());
            } else {
                Log.d(TAG, "Location successfully removed for userId: " + userId);
            }
        });
    }


}
