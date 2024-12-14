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
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.github.thuhen.beeapp.databinding.ActivityCustomerMapBinding;
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
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CustomerMapActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private ActivityCustomerMapBinding binding;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;
    private Button mLogout;
    private Button mRequest;
    private LatLng pickupLocation;
    private String customerId = "";
    //private LocationRequest mLocationRequest;
    private Button mCallDriver;


    private static final int LOCATION_REQUEST_CODE = 100;
    private static final String TAG = "CustomerMapActivity"; // Tag dùng trong Logcat

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityCustomerMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Log.d(TAG, "onCreate: Activity started");

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
                Intent intent = new Intent(CustomerMapActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });
        mRequest = findViewById(R.id.button_call_request);
        mRequest.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("MissingPermission")
            @Override
            public void onClick(View view) {
                if (mRequest == null) {
                    Log.e("Error", "mRequest không được ánh xạ chính xác!");
                    return;
                }
                fusedLocationProviderClient.getLastLocation()
                        .addOnSuccessListener(CustomerMapActivity.this, new OnSuccessListener<Location>() {
                            @Override
                            public void onSuccess(Location location) {
                                if (location == null) {
                                    Log.e("Error", "location is null!");
                                    return;
                                }
                                String userId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
                                DatabaseReference userLocationRef = FirebaseDatabase.getInstance().getReference("customerRequest");

                                GeoFire geoFire = new GeoFire(userLocationRef);
                                geoFire.setLocation(userId, new GeoLocation(location.getLatitude(), location.getLongitude()));

                                pickupLocation = new LatLng(location.getLatitude(), location.getLongitude());
                                mMap.addMarker(new MarkerOptions().position(pickupLocation).title(getString(R.string.pickup_here)));
                            }
                        });

                Snackbar snackbar = Snackbar.make(view, "Getting your driver...", Snackbar.LENGTH_INDEFINITE);
                snackbar.show();
                Handler handler = new Handler();
                handler.postDelayed(() -> {
                    // Code sẽ chạy sau 3 giây
                    Toast.makeText(CustomerMapActivity.this, "Task completed!", Toast.LENGTH_SHORT).show();
                }, 3000);

                saveCustomerRequest();
                getClosestDriver();

            }
        });

    }

    private int radius = 1;
    private Boolean driverFound = false;
    private String driverFoundID;
    private void getClosestDriver() {
        if (pickupLocation == null) {
            Log.e(TAG, "getClosestDriver: Pickup location is null. Cannot search for drivers.");
            Toast.makeText(this, "Pickup location is not set. Please select a location.", Toast.LENGTH_SHORT).show();
            return;
        }
        DatabaseReference driverLocation = FirebaseDatabase.getInstance().getReference().child("driverAvailable");
        GeoFire geofire = new GeoFire(driverLocation);
        //lỗi: pickupLocation bị null
        GeoQuery geoQuery = geofire.queryAtLocation(new GeoLocation(pickupLocation.latitude, pickupLocation.longitude), radius);
        geoQuery.removeAllListeners();
        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                if (!driverFound) {
                    driverFound = true;
                    driverFoundID = key;

                    DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID);
                    String customerID = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
                    HashMap map = new HashMap();
                    map.put("customerRideId", customerID);
                    driverRef.updateChildren(map);
                    getDriverLocation();
                    mRequest.setText(R.string.looking_for_driver_location);
                    // Gọi notifyDriverFound khi tìm thấy tài xế
                }
            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady() {
                if (!driverFound) {
                    radius++;
                    getClosestDriver();
                }
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }

    private Marker mDriverMarker;

    private void getDriverLocation() {
        if (driverFoundID == null || driverFoundID.isEmpty()) {
            Log.e(TAG, "getDriverLocation: Driver ID is null or empty");
            return;
        }

        DatabaseReference driverLocationRef = FirebaseDatabase.getInstance()
                .getReference("driverAvailable").child(driverFoundID).child("l");

        driverLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.getValue() != null) {
                    List<Object> map = (List<Object>) snapshot.getValue();
                    if (map != null && map.size() >= 2) {
                        try {
                            double driverLat = Double.parseDouble(map.get(0).toString());
                            double driverLng = Double.parseDouble(map.get(1).toString());
                            LatLng driverLatLng = new LatLng(driverLat, driverLng);
                            updateDriverMarker(driverLat, driverLng);

                            if (pickupLocation != null) {
                                calculateDistance(pickupLocation, driverLatLng);
                            }
                        } catch (NumberFormatException | NullPointerException e) {
                            Log.e(TAG, "Invalid driver location data", e);
                        }
                    } else {
                        Log.w(TAG, "onDataChange: Driver location data is invalid");
                    }
                } else {
                    Log.w(TAG, "onDataChange: No driver location found");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "onCancelled: Error fetching driver location", error.toException());
            }
        });
    }

    private void updateDriverMarker(double driverLat, double driverLng) {
        LatLng driverLatLng = new LatLng(driverLat, driverLng);

        // Xóa marker cũ nếu có
        if (mDriverMarker != null) {
            mDriverMarker.remove();
        }

        // Thêm marker mới cho vị trí tài xế
        mDriverMarker = mMap.addMarker(new MarkerOptions()
                .position(driverLatLng)
                .title("Driver Location"));

        // Nếu vị trí đón đã được chọn, tính khoảng cách giữa tài xế và khách hàng
        if (pickupLocation != null) {
            Location pickupLoc = new Location("");
            pickupLoc.setLatitude(pickupLocation.latitude);
            pickupLoc.setLongitude(pickupLocation.longitude);

            Location driverLoc = new Location("");
            driverLoc.setLatitude(driverLat);
            driverLoc.setLongitude(driverLng);

            float distance = pickupLoc.distanceTo(driverLoc) / 1000; // Đổi sang km
            mRequest.setText(String.format("Driver is %.2f km away", distance));
        }
    }

    private void saveCustomerRequest() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference customerRequestRef = FirebaseDatabase.getInstance().getReference().child("customerRequest").child(userId);

        HashMap<String, Object> requestMap = new HashMap<>();
        requestMap.put("i", Arrays.asList(pickupLocation.latitude, pickupLocation.longitude));
        customerRequestRef.updateChildren(requestMap);

        Log.d(TAG, "saveCustomerRequest: Customer request saved with location: " + pickupLocation.latitude + ", " + pickupLocation.longitude);
    }

    private void calculateDistance(LatLng customerLatLng, LatLng driverLatLng) {
        if (customerLatLng == null || driverLatLng == null) {
            Log.e(TAG, "calculateDistance: One of the locations is null");
            return;
        }

        // Tạo đối tượng Location cho Customer
        Location customerLocation = new Location("");
        customerLocation.setLatitude(customerLatLng.latitude);
        customerLocation.setLongitude(customerLatLng.longitude);

        // Tạo đối tượng Location cho Driver
        Location driverLocation = new Location("");
        driverLocation.setLatitude(driverLatLng.latitude);
        driverLocation.setLongitude(driverLatLng.longitude);

        // Tính khoảng cách (mét)
        float distanceInMeters = customerLocation.distanceTo(driverLocation);

        // Chuyển đổi sang km
        float distanceInKm = distanceInMeters / 1000;

        Log.d(TAG, "Distance between Customer and Driver: " + distanceInKm + " km");

        // Hiển thị khoảng cách
        Toast.makeText(this, String.format("Driver is %.2f km away", distanceInKm), Toast.LENGTH_SHORT).show();

        // Cập nhật TextView hoặc Button
        mRequest.setText(String.format("Driver is %.2f km away", distanceInKm));
    }


    private void checkLocationPermission() {
        Log.d(TAG, "checkLocationPermission: Checking permissions");
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
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
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST_CODE);
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
//        locationCallback = new LocationCallback() {
//            @Override
//            public void onLocationResult(LocationResult locationResult) {
//                if (locationResult == null) {
//                    return;
//                }
//                for (Location location : locationResult.getLocations()) {
//                    // Xử lý vị trí mới tại đây
//                    double latitude = location.getLatitude();
//                    double longitude = location.getLongitude();
//                    Log.d("Location", "Lat: " + latitude + ", Lng: " + longitude);
//
//                    // Hiển thị trên bản đồ nếu cần
//                    LatLng userLatLng = new LatLng(latitude, longitude);
//                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15));
//                }
//            }
//        };
    }

    private Circle mUserLocationCircle;
    private boolean hasMovedCamera = false;
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
                if (mMap == null) {
                    Log.e(TAG, "onLocationResult:  GoogleMap is null");
                    return;
                }
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

                } else {
                    Log.e(TAG, "onLocationResult: LocationResult is null");
                    return;
                }
            }
        };

// Bắt đầu yêu cầu cập nhật vị trí
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
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
        mMap.setOnMapClickListener(latLng -> {
            pickupLocation = latLng;
            mMap.clear();
            mMap.addMarker(new MarkerOptions().position(pickupLocation).title("Pickup Location"));
            Toast.makeText(this, "Pickup location set!", Toast.LENGTH_SHORT).show();

            saveCustomerRequest();
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (fusedLocationProviderClient != null && locationCallback != null) {
            stopLocationUpdates();
        }
    }
//    @Override
//    public void onConnected(@Nullable Bundle bundle) {
//        mLocationRequest = new LocationRequest.Builder(PRIORITY_HIGH_ACCURACY, 100)
//                .setWaitForAccurateLocation(false)
//                .setMinUpdateIntervalMillis(2000)
//                .setMaxUpdateDelayMillis(100)
//                .build();
//    }
//
//    @Override
//    public void onConnectionSuspended(int i) {
//
//    }
//
//    @Override
//    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
//
//    }
//
//    @Override
//    public void onLocationChanged(@NonNull Location location) {
//        if(getApplicationContext() != null){
//            mLastLocation = location;
//
//            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
//
//            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
//            mMap.animateCamera(CameraUpdateFactory.zoomTo(11));
//
//            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
//            DatabaseReference refAvailable = FirebaseDatabase.getInstance().getReference("driverAvailable");
//            DatabaseReference refWorking = FirebaseDatabase.getInstance().getReference("driverWorking");
//            GeoFire geoFireAvailable = new GeoFire(refAvailable);
//            GeoFire geoFireWorking = new GeoFire(refWorking);
//            switch (customerId){
//                case "":
//                    geoFireWorking.removeLocation(userId);
//                    geoFireAvailable.setLocation(userId, new GeoLocation(location.getLatitude(), location.getLongitude()));
//                    break;
//
//
//                default:
//                    geoFireAvailable.removeLocation(userId);
//                    geoFireWorking.setLocation(userId, new GeoLocation(location.getLatitude(), location.getLongitude()));
//            }
//        }
//    }

}

