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
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;
import com.google.android.gms.common.api.Status;
import com.google.android.libraries.places.api.model.Place;

public class CustomerMapActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private LatLng userLocation;
    private Button mRequest;
    private Button mLogout;
    private Button mSetting;
    //vi tri đón khách nay
    private LatLng pickupLocation;
    private Marker pickupMarker;
    private String customerId = "";
    private Button mCallDriver;




    private static final int LOCATION_REQUEST_CODE = 100;
    private static final String TAG = "CustomerMapActivity"; // Tag dùng trong Logcat
    private Boolean requestBol = false;

    private AutocompleteSupportFragment autocompleteFragment;
    private String destinaion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        com.github.thuhen.beeapp.databinding.ActivityCustomerMapBinding binding = ActivityCustomerMapBinding.inflate(getLayoutInflater());
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
        if (!checkLocationPermission())
            requestForPermissions();
        getUserLocation();

        mLogout = (Button) findViewById(R.id.logout);
        if (mLogout == null) {
            Log.e("Error", "mLogout không được ánh xạ chính xác!");
            return;
        }
        mLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "mLogout clicked: Removing location updates");
                stopLocationUpdates();

                deleteLocationOnFirebase();
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(CustomerMapActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
                return;

            }
        });

        mRequest = findViewById(R.id.button_call_request);
        if (mRequest == null) {
            Log.e("Error", "mRequest không được ánh xạ chính xác!");
            return;
        }
        mRequest.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("MissingPermission")
            @Override
            public void onClick(View view) {
                if (requestBol) {
                    requestBol = false;
                    geoQuery.removeAllListeners();
                    //xóa driversWorking
                    driverLocationRef.removeEventListener(driverLocationListener);
                    //xóa kết nối giữa driver và customer
                    if (driverFoundID != null) {
                        DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference()
                                .child("Users").child("Drivers");
                        driverRef.setValue(true);
                        Log.d(TAG, "mRequestonClick: driverRef.setValue(true)");
                        driverFoundID = null;
                    }
                    cancelRequestClosestDriver();

                } else {
                    requestBol = true;
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


                                    Log.d(TAG, "saved location");
                                    pickupLocation = new LatLng(location.getLatitude(), location.getLongitude());

                                    pickupMarker = mMap.addMarker(new MarkerOptions().position(pickupLocation)
                                            .title(getString(R.string.pickup_here))
                                            .icon(BitmapDescriptorFactory.fromResource(R.mipmap.icon_app_bee_customer)))
                                    ;

                                    getClosestDriver();
                                }
                            });
                }
            }
        });

        mSetting = (Button) findViewById(R.id.setting);
        if (mSetting == null) {
            Log.e("Error", "mSetting không được ánh xạ chính xác!");
            return;
        }
        mSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(CustomerMapActivity.this, CustomerSettingActivity.class);
                startActivity(intent);
                return;
            }

        });

        autocompleteFragment = (AutocompleteSupportFragment) getSupportFragmentManager()
                .findFragmentById(R.id.autoComplete_Fragment);

        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                // Xử lý khi người dùng chọn địa điểm
                destinaion = place.getName().toString();   // Lấy tên địa điểm
            }

            @Override
            public void onError(Status status) {
                // Xử lý khi có lỗi xảy ra
                Log.e("Place", "An error occurred: " + status);
            }
        });
    }


    private void cancelRequestClosestDriver() {
        //xóa customerRequest
        String userId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
        DatabaseReference userLocationRef = FirebaseDatabase.getInstance().getReference("customerRequest");
        GeoFire geoFire = new GeoFire(userLocationRef);
        geoFire.removeLocation(userId);
        //set lại điều kiện timd tài xế
        driverFound = false;
        radius = 1;
        //xóa marker khách khỏi bản đồ
        if (pickupMarker != null)
            pickupMarker.remove();
        if (mDriverMarker != null)
            mDriverMarker.remove();
        mRequest.setText(R.string.call_bee);
    }

    private int radius = 1;
    private Boolean driverFound = false;
    private String driverFoundID;
    private GeoQuery geoQuery;

    private void getClosestDriver() {
        // Vào khu firebase các tài xế đang rảnh
        DatabaseReference driverLocation = FirebaseDatabase.getInstance().getReference()
                .child("driverAvailable");
        // Tạo đối tượng GeoFire để truy vấn vị trí tài xế
        GeoFire geofire = new GeoFire(driverLocation);
        // Truy vấn vị trí tài xế gần nhất
        geoQuery = geofire.queryAtLocation
                (new GeoLocation(pickupLocation.latitude, pickupLocation.longitude), radius);
        // Xóa tất cả listener cũ
        geoQuery.removeAllListeners();
        // Thêm listener mới
        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                if (!driverFound && requestBol) {
                    driverFound = true;
                    driverFoundID = key;
                    Log.e(TAG, "getClosestDriver: Driver ID is " + key);
                    //vào firebase tài xế gần nhất
                    DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference()
                            .child("Users").child("Drivers").child(driverFoundID).child("customerRequest");
                    // lấy id customer
                    String customerID = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
                    // lưu id customerRequest vào driver
                    HashMap map = new HashMap();
                    map.put("customerRideId", customerID);//lưu id customer
                    map.put("destination",destinaion);
                    // cập nhật driver
                    driverRef.updateChildren(map);
                    // hiện market vị trí tài xế
                    getDriverLocation();
                    mRequest.setText(R.string.looking_for_driver_location);
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
                    if (radius <= 5) {
                        radius++;
                        // Nếu không tìm thấy tài xế, tăng bán kính tìm kiếm
                        getClosestDriver();
                    } else {
                        Toast.makeText(CustomerMapActivity.this, R.string.no_driver_found, Toast.LENGTH_SHORT).show();
                        cancelRequestClosestDriver();
                    }
                }
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }

    private Marker mDriverMarker;
    private DatabaseReference driverLocationRef;
    private ValueEventListener driverLocationListener;

    private void getDriverLocation() {
        if (driverFoundID == null || driverFoundID.isEmpty()) {
            Toast.makeText(this, String.format("getDriverLocation: Driver ID is null or empty"), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "getDriverLocation: Driver ID is null or empty");
            mRequest.setText(R.string.call_bee);
            return;
        }

        // vào khu driverWorking
        driverLocationRef = FirebaseDatabase.getInstance().getReference()
                .child("driversWorking").child(driverFoundID).child("l");
        // Lắng nghe sự thay đổi của vị trí tài xế
        driverLocationListener = driverLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Nếu tài xế đã được ghép, lấy tọa độ vị trí tài xế
                if (snapshot.exists() && snapshot.getValue() != null && requestBol) {
                    // Lấy tọa độ vị trí tài xế
                    List<Object> map = (List<Object>) snapshot.getValue();
                    // Kiểm tra tọa độ có đủ 2 phần tử
                    if (map != null && map.size() >= 2) {
                        try {
                            double driverLat = Double.parseDouble(map.get(0).toString());
                            double driverLng = Double.parseDouble(map.get(1).toString());
                            LatLng driverLatLng = new LatLng(driverLat, driverLng);
                            // Hiển thị vị trí tài xế trên bản đồ
                            updateDriverMarker(driverLat, driverLng);
                            if (pickupLocation != null) {
                                // Tính khoảng cách
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
                .title("Driver Location")
                .icon(BitmapDescriptorFactory.fromResource(R.mipmap.icon_app_bee_driver)));
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pickupLocation, 15));
    }

    //luư tọa độ điểm đón của khách
    private void saveCustomerRequest() {
        if (pickupLocation == null) {
            Log.e(TAG, "saveCustomerRequest: pickupLocation is null");
            return;
        }
        // lấy id customer
        String userId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
        // Lưu vị trí khách hàng vào Firebase customerRequest
        DatabaseReference customerRequestRef = FirebaseDatabase.getInstance().getReference().child("customerRequest").child(userId);
        HashMap<String, Object> requestMap = new HashMap<>();

        requestMap.put("l", Arrays.asList(pickupLocation.latitude, pickupLocation.longitude));
        // update location pịckup at customerRequest
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
        if (distanceInKm < 0.01)
            mRequest.setText("@string/driver_is_here");
        else {
            mRequest.setText(String.format("Driver Found: %.2f km away", distanceInKm));

        }
        Log.d(TAG, "Distance between Customer and Driver: " + distanceInKm + " km");
        // Hiển thị khoảng cách
        // Cập nhật TextView hoặc Button

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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
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

    private Circle mUserLocationCircle;
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
                        // Log.d(TAG, "onLocationResult: Location - Lat: " + latitude + ", Lng: " + longitude);
                        userLocation = new LatLng(latitude, longitude);

                        if (!hasMovedCamera) {
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 20)); // Di chuyển camera đến vị trí mới
                            hasMovedCamera = true; // Đánh dấu là đã di chuyển camera
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
        // Xử lý sự kiện khi người dùng nhấp vào bản đồ
        //
        // có thể thêm chức năng: cửa sổ xác nhận đặt điểm đón
//        mMap.setOnMapClickListener(latLng -> {
//            Log.d(TAG, "onMapClick: User clicked on map");
//            // Lưu tọa độ của điểm được chọn
//            pickupLocation = latLng;
//            // Xóa tất cả marker cũ
//            mMap.clear();
//            // Thêm marker mới cho điểm được chọn
//            mMap.addMarker(new MarkerOptions().position(pickupLocation).title("Pickup Location"));
//            Toast.makeText(this, "Pickup location set!", Toast.LENGTH_SHORT).show();
//            // Lưu tọa độ điểm đón vào Firebase
//            saveCustomerRequest();
//        });
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
                    Log.e(TAG, "Failed to remove location: " + error.getMessage());
                } else {
                    Log.d(TAG, "Location successfully removed for userId: " + userId);
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

