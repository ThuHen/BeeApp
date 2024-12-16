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

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.github.thuhen.beeapp.databinding.ActivityDriverMapBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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

    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private String customerId = "";
    private LatLng userLocation;
    private static final int LOCATION_REQUEST_CODE = 100;
    private static final String TAG = "DriverMapActivity"; // Tag dùng trong Logcat

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        com.github.thuhen.beeapp.databinding.ActivityDriverMapBinding binding = ActivityDriverMapBinding.inflate(getLayoutInflater());
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
        if (!checkLocationPermission())
            requestForPermissions();
        getUserLocation();

        Button mLogout = (Button) findViewById(R.id.logout);
        mLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "mLogout clicked: Removing location updates");
                stopLocationUpdates();

                deleteLocationOnFirebase();
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(DriverMapActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
                return;

            }
        });
        getAssignedCustomer();
    }
//    public interface FirebaseCallback {
//        void onCheckResult(boolean hasCustomerRideId);
//    }
//
//    private void checkCustomerRideId(String driverId, FirebaseCallback callback) {
//        if (driverId == null || driverId.isEmpty()) {
//            Log.e("CheckCustomerRideId", "Driver ID is null or empty");
//            callback.onCheckResult(false); // Trả về false nếu driverId không hợp lệ
//            return;
//        }
//
//        // Tham chiếu tới nhánh Drivers
//        DatabaseReference assignedCustomerRef = FirebaseDatabase.getInstance().getReference()
//                .child("Users").child("Drivers").child(driverId);
//
//        // Lắng nghe sự thay đổi trong nhánh Drivers
//        assignedCustomerRef.addValueEventListener(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot snapshot) {
//                // Kiểm tra xem nhánh snapshot có tồn tại và có chứa con là customerRideId
//                if (snapshot.exists() && snapshot.hasChild("customerRideId")) {
//                    callback.onCheckResult(true); // Trả về true nếu customerRideId tồn tại
//                } else {
//                    callback.onCheckResult(false); // Trả về false nếu không tồn tại
//                }
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError error) {
//                Log.e("CheckCustomerRideId", "Database error: " + error.getMessage());
//                callback.onCheckResult(false); // Trả về false nếu có lỗi
//            }
//        });
//    }




    private Marker pickupMarker;
    private DatabaseReference assignedCustomerPickupLocationRef;
    private ValueEventListener assignedCustomerPickupLocationListener;

    private Boolean getAssignedCustomerPickupLocation() {
        // Kiểm tra xem customerId có hợp lệ không
        if (customerId != null && !customerId.isEmpty()) {
            //get l: location customerRequest
            assignedCustomerPickupLocationRef = FirebaseDatabase.getInstance()
                    .getReference("customerRequest").child(customerId).child("l");
            // Lắng nghe sự thay đổi của vị trí khách hàng
            assignedCustomerPickupLocationListener = assignedCustomerPickupLocationRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    // Nếu vị trí khách hàng không tồn tại, không làm gì cả
                    if (!snapshot.exists() && Objects.equals(customerId, "")) {
                        Log.w(TAG, "onDataChange: No pickup location found for customerId: " + customerId);
                        return;
                    }
                    try {
                        // Lấy tọa độ vị trí khách hàng
                        List<Object> locationData = (List<Object>) snapshot.getValue();
                        // Kiểm tra tọa độ có đủ 2 phần tử
                        if (locationData == null || locationData.size() < 2) {
                            Log.e(TAG, "onDataChange: Invalid location data");
                            return;
                        }
                        // get longtitue, latitue
                        double locationLat = Double.parseDouble(locationData.get(0).toString());
                        double locationLng = Double.parseDouble(locationData.get(1).toString());
                        LatLng customerLatLng = new LatLng(locationLat, locationLng);
                        // Add marker to map
                        pickupMarker = mMap.addMarker(new MarkerOptions()
                                .position(customerLatLng)
                                .title("Customer Pickup Location"));
                        // Move and zoom camera to customer location
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(customerLatLng, 15));
                        Log.d(TAG, "Customer Pickup Location: Lat=" + locationLat + ", Lng=" + locationLng);


                        // Tính khoảng cách
                        calculateDistance(userLocation, customerLatLng);


                    } catch (Exception e) {
                        Log.e(TAG, "onDataChange: Error parsing location data", e);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "onCancelled: Failed to retrieve pickup location", error.toException());
                }
            });
            return true;
        } else {
            Log.e(TAG, "getAssignedCustomerPickupLocation: customerId is null or empty");
            return false;
        }

    }

    private void getAssignedCustomer() {
        // Lấy ID của tài xế này từ driverId
        String driverId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
        // Lấy thông tin tài xế này từ Firebase
        DatabaseReference assignedCustomerRef = FirebaseDatabase.getInstance().getReference()
                .child("Users").child("Drivers").child(driverId);
        // Lắng nghe sự thay đổi của thông tin tài xế
        assignedCustomerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Nếu tài xế đã được gán cho khách hàng, lấy ID của khách hàng
                if (snapshot.exists() && snapshot.hasChild("customerRideId")) {
                    // Lấy ID của khách hàng
                    customerId = Objects.requireNonNull(snapshot.child("customerRideId").getValue()).toString();
                    Log.d(TAG, "Assigned customer ID: " + customerId);
                    // Lấy vị trí của khách hàng
                    if (getAssignedCustomerPickupLocation())
                        //chuyển trang thái driver: available -> working
                        changeDriverStatusToWorking();
                } else {
                    Log.w(TAG, "getAssignedCustomer: No assigned customer found");
                    customerId = ""; // Reset customerId
                    if (pickupMarker != null)
                        pickupMarker.remove();
                        // Nếu có listener từ  customerRequest, xóa nó
                    if (assignedCustomerPickupLocationListener != null)
                        assignedCustomerPickupLocationRef.removeEventListener(assignedCustomerPickupLocationListener);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching assigned customer: " + error.getMessage());
            }
        });

    }

    private void calculateDistance(LatLng driverLatLng, LatLng customerLatLng) {
        if (driverLatLng == null || customerLatLng == null) {
            Log.e(TAG, "calculateDistance: One of the locations is null");
            return;
        }
        Location driverLocation = new Location("");
        driverLocation.setLatitude(driverLatLng.latitude);
        driverLocation.setLongitude(driverLatLng.longitude);

        Location customerLocation = new Location("");
        customerLocation.setLatitude(customerLatLng.latitude);
        customerLocation.setLongitude(customerLatLng.longitude);
        // Tính khoảng cách (mét)
        float distanceInMeters = driverLocation.distanceTo(customerLocation);
        // Chuyển đổi sang km
        float distanceInKm = distanceInMeters / 1000;
        Log.d(TAG, "Distance between Driver and Customer: " + distanceInKm + " km");
        // Hiển thị thông báo
        Toast.makeText(this, String.format("Customer is %.2f km away", distanceInKm), Toast.LENGTH_SHORT).show();
    }

    private Boolean checkLocationPermission() {
        Log.d(TAG, "checkLocationPermission: Checking permissions");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "checkLocationPermission: Permissions not granted, requesting permissions");
            return false;
        } else {
            Log.d(TAG, "checkLocationPermission: Permissions granted, starting location updates");
            return true;
        }
    }

    private void requestForPermissions() {
        Log.d(TAG, "requestForPermissions: Requesting location permissions");
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "onRequestPermissionsResult: Permissions granted");
                Toast.makeText(this, R.string.location_permission_accepted, Toast.LENGTH_SHORT).show();
                // getUserLocation();
            } else {
                Log.e(TAG, "onRequestPermissionsResult: Permissions denied");
                Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
    private void changeDriverStatusToWorking() {

        // Lấy ID của tài xế này từ driverId
        String driverId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
        DatabaseReference driverRefAvailable = FirebaseDatabase.getInstance().getReference("driverAvailable");
        DatabaseReference driverRefWorking = FirebaseDatabase.getInstance().getReference("driversWorking");
        GeoFire geoFireAvailable = new GeoFire(driverRefAvailable);
        GeoFire geoFireWorking = new GeoFire(driverRefWorking);
        if (Objects.equals(customerId, "") || customerId == null) {

            geoFireWorking.removeLocation(driverId);
            geoFireAvailable.setLocation(driverId, new GeoLocation(userLocation.latitude, userLocation.longitude));
            Log.d(TAG, "saved location available driver");
        } else {
            geoFireAvailable.removeLocation(driverId);
            geoFireWorking.setLocation(driverId, new GeoLocation(userLocation.latitude, userLocation.longitude));
            Log.d(TAG, "saved location available driver");

        }

    }
    private boolean hasMovedCamera = false;

    @SuppressLint("MissingPermission")
    private void getUserLocation() {
        Log.d(TAG, "getUserLocation: Starting location updates");

        locationRequest = new LocationRequest.Builder(PRIORITY_HIGH_ACCURACY, 5000)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(2000)
                .setMaxUpdateDelayMillis(5000)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {

                    if (location != null) {
                        double latitude = location.getLatitude();
                        double longitude = location.getLongitude();
                        Log.d(TAG, "onLocationResult: Location - Lat: " + latitude + ", Lng: " + longitude);
                        userLocation = new LatLng(latitude, longitude);

                        if (!hasMovedCamera) {
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 20)); // Di chuyển camera đến vị trí mới
                            hasMovedCamera = true; // Đánh dấu là đã di chuyển camera
                        }

                        // Cập nhật vị trí người dùng trong Firebase
                        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                        if (currentUser != null) {

                            changeDriverStatusToWorking();

                        } else {
                            Log.e(TAG, "onLocationResult: cant save location, user logOut");
                            return;
                        }


                    } else {
                        Log.e(TAG, "onLocationResult: LastLocationResult is null");
                        return;
                    }
                }
            }
        }

        ;

// Bắt đầu yêu cầu cập nhật vị trí

        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null);

    }

    private void stopLocationUpdates() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        Log.d(TAG, "stopLocationUpdates: Removing location updates");
    }


    @SuppressLint("MissingPermission")
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume:");
        hasMovedCamera = false; // Reset lại cờ khi Activity được mở lại
        // Kiểm tra nếu người dùng đã đăng nhập và cần cập nhật vị trí


        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null);
            Log.d(TAG, "onResume: continue update location.");
        } else {
            Log.d(TAG, "onResume: User not logged in, no need to update location.");
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause:");
        stopLocationUpdates();


    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        Log.d(TAG, "onMapReady: Map is ready");
        mMap = googleMap;
        // nút + và - zoom
        mMap.getUiSettings().setZoomControlsEnabled(true);

        mMap.getUiSettings().setMyLocationButtonEnabled(true);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "onMapReady: Location permission granted, enabling My Location");
            mMap.setMyLocationEnabled(true);
        } else {
            Log.w(TAG, "onMapReady: Location permission not granted");
        }
    }

    public void deleteLocationOnFirebase() {
        // Xóa vị trí của người dùng khỏi Firebase
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String userId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
            DatabaseReference userLocationRef = FirebaseDatabase.getInstance().getReference("driverAvailable");
            GeoFire geoFire = new GeoFire(userLocationRef);

            geoFire.removeLocation(userId, (key, error) -> {
                if (error != null) {
                    Log.e(TAG, "deleteLocationOnFirebase:Failed to remove location: " + error.getMessage());
                } else {
                    Log.d(TAG, "deleteLocationOnFirebase:Location successfully removed for userId: " + userId);
                }
            });
            Log.d(TAG, "deleteLocationOnFirebase:Xóa vị trí của người dùng khỏi Firebase");

        } else {
            Log.e(TAG, "deleteLocationOnFirebase: deleted location, user logOut");
            return;
        }

    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop:");
        stopLocationUpdates();
        deleteLocationOnFirebase();

    }


}
