package com.github.thuhen.beeapp;

import static com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.github.thuhen.beeapp.databinding.ActivityDriverMapBinding;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.List;

public class CustomerMapActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private ActivityDriverMapBinding binding;
    LocationRequest mLocationRequest;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;
    private Location mLastLocation;
    private Button mLogout, mRequest;
    private LatLng pickupLocation;
    private Button mCallDriver;

    private static final int LOCATION_REQUEST_CODE = 100;
    private static final String TAG = "DriverMapActivity"; // Tag dùng trong Logcat

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_map);
        //binding = ActivityDriverMapBinding.inflate(getLayoutInflater());
        //setContentView(binding.getRoot());

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
        mRequest = (Button) findViewById(R.id.request);

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
        mRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mRequest == null) {
                    Log.e("Error", "mRequest không được ánh xạ chính xác!");
                    return;
                }
                Snackbar snackbar = Snackbar.make(view, "Getting your driver...", Snackbar.LENGTH_INDEFINITE);
                snackbar.show();
                Handler handler = new Handler();
                handler.postDelayed(() -> {
                    // Code sẽ chạy sau 3 giây
                    Toast.makeText(CustomerMapActivity.this, "Task completed!", Toast.LENGTH_SHORT).show();
                }, 3000);
                if (pickupLocation == null) {
                    Toast.makeText(CustomerMapActivity.this , "Pickup location is not set!", Toast.LENGTH_SHORT).show();
                    return;
                }

                getClosestDriver();
            }

        });
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    // Xử lý vị trí mới tại đây
                    double latitude = location.getLatitude();
                    double longitude = location.getLongitude();
                    Log.d("Location", "Lat: " + latitude + ", Lng: " + longitude);

                    // Hiển thị trên bản đồ nếu cần
                    LatLng userLatLng = new LatLng(latitude, longitude);
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15));
                }
            }
        };

    }

    private int radius = 1;
    private Boolean driverFound = false;
    private String driverFoundID;

    private void getClosestDriver(){
        DatabaseReference driverLocation = FirebaseDatabase.getInstance().getReference().child("driverAvailable");
        GeoFire geofire = new GeoFire(driverLocation);
        GeoQuery geoQuery = geofire.queryAtLocation(new GeoLocation(pickupLocation.latitude, pickupLocation.longitude), radius);
        geoQuery.removeAllListeners();
        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                if(!driverFound){
                    driverFound=true;
                    driverFoundID=key;

                    DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID);
                    String customerID = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    HashMap map = new HashMap();
                    map.put("customerRideId", customerID);
                    driverRef.updateChildren(map);
                    getDriverLocation();
                    mRequest.setText("Looking for driver location...");
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
                if(!driverFound){
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
    private void getDriverLocation(){
        DatabaseReference driverLocationRef = FirebaseDatabase.getInstance().getReference().child("driversWorking").child(driverFoundID).child("i");
        driverLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Object value = snapshot.getValue();
                    if (value instanceof List) {
                        List<Object> map = (List<Object>) value;

                        double locationLat = 0.0;
                        double locationLng = 0.0;

                        // Kiểm tra và lấy giá trị latitude
                        if (map.size() > 0 && map.get(0) != null) {
                            try {
                                locationLat = Double.parseDouble(map.get(0).toString());
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "onDataChange: Invalid latitude format", e);
                            }
                        }

                        // Kiểm tra và lấy giá trị longitude
                        if (map.size() > 1 && map.get(1) != null) {
                            try {
                                locationLng = Double.parseDouble(map.get(1).toString());
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "onDataChange: Invalid longitude format", e);
                            }
                        }

                        // Nếu cả hai giá trị hợp lệ, cập nhật Marker
                        if (locationLat != 0.0 && locationLng != 0.0) {
                            LatLng driverLatLng = new LatLng(locationLat, locationLng);

                            if (mDriverMarker != null) {
                                mDriverMarker.remove();
                            }

                            mDriverMarker = mMap.addMarker(new MarkerOptions()
                                    .position(driverLatLng)
                                    .title("Your Driver"));
                            mRequest.setText("Driver Found");
                        }
                    } else {
                        Log.e(TAG, "onDataChange: Data snapshot is not a List");
                    }
                } else {
                    Log.w(TAG, "onDataChange: Snapshot does not exist");
                }

//                    if (snapshot.exists()) {
//                        List<Object> map = (List<Object>) snapshot.getValue();
//                        double locationLat = 0;
//                        double locationLng = 0;
//                        mRequest.setText("Driver Found");
//                        if (map.get(0) != null) {
//                            locationLat = Double.parseDouble(map.get(0).toString());
//                        }
//                        if (map.get(0) != null) {
//                            locationLng = Double.parseDouble(map.get(0).toString());
//                        }
//                        LatLng driverLatLng = new LatLng(locationLat, locationLng);
//                        if (mDriverMarker != null) {
//                            mDriverMarker.remove();
//                        }
//                        mDriverMarker = mMap.addMarker(new MarkerOptions().position(driverLatLng).title("your driver"));
//                          }
//                    }
                }

            @Override
            public void onCancelled (@NonNull DatabaseError error){

            }
        });
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
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Yêu cầu quyền nếu chưa được cấp
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
            return;
        }

        fusedLocationProviderClient.requestLocationUpdates(mLocationRequest, locationCallback, Looper.getMainLooper());
    }

    private void stopLocationUpdates() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
    }

    @SuppressLint("MissingPermission")
    private void getUserLocation() {
        Log.d(TAG, "getUserLocation: Starting location updates");

        LocationRequest locationRequest = new LocationRequest.Builder(PRIORITY_HIGH_ACCURACY, 5000) // 5 giây
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(10000) // 10 giây
                .setMaxUpdateDelayMillis(10000) // 10 giây
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Log.d(TAG, "onLocationResult: Received location update");
                if (locationResult == null || mMap == null) {
                    Log.e(TAG, "onLocationResult: LocationResult or GoogleMap is null");
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    Log.d(TAG, "onLocationResult: Location - Lat: " + location.getLatitude() + ", Lng: " + location.getLongitude());
                    LatLng userLocation = new LatLng(location.getLatitude(), location.getLongitude());
                    mMap.clear(); // Xóa marker cũ
                    mMap.addMarker(new MarkerOptions().position(userLocation).title("Your Location"));
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 15));
                }
            }
        };

        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }
    // 7. Xử lý vòng đời activity
    @Override
    protected void onResume() {
        super.onResume();
        startLocationUpdates(); // Bắt đầu khi activity hiển thị
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
        if (fusedLocationProviderClient != null && locationCallback != null) {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);
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
