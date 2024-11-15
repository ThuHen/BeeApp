package com.github.thuhen.beeapp;

import static com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

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
import com.google.android.gms.maps.model.MarkerOptions;
import com.github.thuhen.beeapp.databinding.ActivityDriverMapBinding;

public class DriverMapActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private ActivityDriverMapBinding binding;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;

    private static final int LOCATION_REQUEST_CODE = 100;
    private static final String TAG = "DriverMapActivity"; // Tag dùng trong Logcat

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityDriverMapBinding.inflate(getLayoutInflater());
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
        if (locationCallback != null) {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        }
    }
}
