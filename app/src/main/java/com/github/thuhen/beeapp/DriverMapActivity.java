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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
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

import com.google.android.gms.maps.model.BitmapDescriptorFactory;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DriverMapActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private ActivityDriverMapBinding binding;

    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private String customerId = "";
    private LatLng userLocation;
    private static final int LOCATION_REQUEST_CODE = 100;
    private static final String TAG = "DriverMapActivity"; // Tag dùng trong Logcat
    private Button mLogout;
    private Button mSettings;
    private Button mHistory;
    private LinearLayout mCustomerInfor;
    private ImageView customerProfileImage;
    private TextView customerName;
    private TextView customerPhone;
    private TextView customerDestination;
    private LatLng destinationLocation;
    private Marker destinationMarker;
    private LatLng customerLatLng;
    private int status = 0;
    private Double routeDistance = (double) -1;
    private Marker pickupMarker;
    private Button mRideStatus;

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
        getUserLocation();
        getAssignedCustomer();
        mLogout = (Button) findViewById(R.id.logout);

        mSettings = findViewById(R.id.settings);
        mCustomerInfor = findViewById(R.id.customer_infor);
        customerProfileImage = findViewById(R.id.customer_profile_image);
        customerName = findViewById(R.id.customer_name);
        customerPhone = findViewById(R.id.customer_phone);
        customerDestination = findViewById(R.id.customer_Destination);
        mRideStatus = findViewById(R.id.btn_ride_status);
        mHistory = (Button) findViewById(R.id.history);
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
        mSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(DriverMapActivity.this, DriverSettingActivity.class);
                startActivity(intent);
                //finish();
                return;
            }
        });
        mHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "mHistory onClick: ");
                Intent intent = new Intent(DriverMapActivity.this, HistoryActivity.class);
                intent.putExtra("customerOrDriver", "Drivers");
                startActivity(intent);
                return;
            }
        });
        mRideStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (customerId == "") {
                    Toast.makeText(DriverMapActivity.this, R.string.no_customer, Toast.LENGTH_SHORT).show();
                    return;
                }
                switch (status) {
                    case 1:
                        mRideStatus.setText(R.string.end_ride);
                        status = 2;

                        break;

                    case 2:
                        recordRide();
                        endRide();
                        break;
                }

            }
        });
    }

    private void recordRide() {
        String driverId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference()
                .child("Users").child("Drivers").child(driverId).child("history");
        DatabaseReference customerRef = FirebaseDatabase.getInstance().getReference()
                .child("Users").child("Customers").child(customerId).child("history");
        DatabaseReference historyRef = FirebaseDatabase.getInstance().getReference()
                .child("history");
        String historyId = historyRef.push().getKey();// lấy id duy nhất
        driverRef.child(historyId).setValue(true);
        customerRef.child(historyId).setValue(true);
        HashMap<String, Object> map = new HashMap<>();
        map.put("driverId", driverId);
        map.put("customerId", customerId);
        map.put("rating", 0);
        historyRef.child(historyId).updateChildren(map);

    }


    private DatabaseReference assignedCustomerDestiationLocationRef;
    private ValueEventListener assignedCustomerPickupLocationListener;
    private DatabaseReference assignedCustomerPickupLocationRef;

    private void getAssignedCustomer() {
        // Lấy ID của tài xế này từ driverId
        String driverId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
        // Lấy thông tin tài xế này từ Firebase
        DatabaseReference assignedCustomerRef = FirebaseDatabase.getInstance().getReference()
                .child("Users").child("Drivers").child(driverId).child("customerRequest").child("customerRideId");
        // Lắng nghe sự thay đổi của thông tin tài xế
        assignedCustomerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Nếu tài xế đã nhận yêu cầu từ khách hàng
                if (snapshot.exists()) {
                    status = 1;
                    // Lấy ID của khách hàng từ customerRideId
                    customerId = snapshot.getValue().toString();
                    Log.d(TAG, "Assigned customer ID: " + customerId);
                    // Lấy vị trí của khách hàng
                    if (getAssignedCustomerPickupLocation()) {
                        // chuyển trạng thái driver: available -> working
                        changeDriverStatusToWorking();
                        getAssignedCustomerDestination();
                        getAssignedCustomerPickupInfo();
                    }
                } else {
                    endRide();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "getAssignedCustomer: Error fetching assigned customer: " + error.getMessage());
            }
        });

    }

    private void endRide() {
        String driverId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
        DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference()
                .child("Users").child("Drivers").child(driverId).child("customerRequest");
        driverRef.removeValue();

        Log.d(TAG, "mRequestonClick: driverRef.setValue(true)");
        mRideStatus.setText(R.string.pickup_customer);
        hideCustomerInfoUI();
        Log.w(TAG, "getAssignedCustomer: No assigned customer found");
        customerId = ""; // Reset customerId
        if (pickupMarker != null)
            pickupMarker.remove();
        if (destinationMarker != null)
            destinationMarker.remove();
        // Nếu có listener từ  customerRequest, xóa nó
        if (assignedCustomerPickupLocationListener != null)
            assignedCustomerPickupLocationRef.removeEventListener(assignedCustomerPickupLocationListener);


    }


    private void hideCustomerInfoUI() {
        Log.d(TAG, "hideCustomerInfoUI: Hiding customer info UI");
        mCustomerInfor.setVisibility(View.GONE);
        customerName.setText("");
        customerPhone.setText("");
        customerDestination.setText(R.string.destination);
        customerProfileImage.setImageResource(R.mipmap.icon_default_user);
    }

    private Boolean getAssignedCustomerPickupLocation() {
        Log.d(TAG, "getAssignedCustomerPickupLocation: ");
        // Kiểm tra xem customerId có hợp lệ không
        if (customerId != null && !customerId.isEmpty()) {
            //get l: location customerRequest
            assignedCustomerPickupLocationRef = FirebaseDatabase.getInstance()
                    .getReference("customerRequest").child(customerId).child("l");
            // Lắng nghe sự thay đổi của vị trí khách hàng
            assignedCustomerPickupLocationListener = assignedCustomerPickupLocationRef
                    .addValueEventListener(new ValueEventListener() {
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
                                customerLatLng = new LatLng(locationLat, locationLng);
                                // Add marker to map
                                if (pickupMarker != null)
                                    pickupMarker.remove();
                                pickupMarker = mMap.addMarker(new MarkerOptions()
                                        .position(customerLatLng)
                                        .title("Customer Pickup Location")
                                        .icon(BitmapDescriptorFactory.fromResource(R.mipmap.icon_app_bee_customer)));
                                // Move and zoom camera to customer location
                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(customerLatLng, 15));
                                Log.d(TAG, "Customer Pickup Location: Lat=" + locationLat + ", Lng=" + locationLng);


                                // Tính khoảng cách
                                double distanceInKm = calculateDistance(userLocation, customerLatLng);
                                Log.d(TAG, "Distance between Driver and Customer: " + distanceInKm + " km");
                                Toast.makeText(DriverMapActivity.this, String.format("Customer is %.2f km away", distanceInKm), Toast.LENGTH_SHORT).show();
//                                getAssignedCustomerDestination();
//                                routeDistance = calculateDistance(destinationLocation, customerLatLng);
//                                Log.d(TAG, "Distance route : " + routeDistance + " km");
//                                customerDestination.setText(String.format("Distance: %.2f km", routeDistance));


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

    private void getAssignedCustomerDestination() {
        Log.d(TAG, "getAssignedCustomerDestination: ");
        String driverId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
        assignedCustomerDestiationLocationRef = FirebaseDatabase.getInstance().getReference()
                .child("Users").child("Drivers").child(driverId)
                .child("customerRequest").child("destination");
        assignedCustomerDestiationLocationRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Kiểm tra xem snapshot có chứa dữ liệu không
                    if (snapshot.hasChild("latitude") && snapshot.hasChild("longitude")) {
                        // Lấy giá trị latitude và longitude
                        double latitude = snapshot.child("latitude").getValue(Double.class);
                        double longitude = snapshot.child("longitude").getValue(Double.class);

                        // Tạo LatLng từ dữ liệu
                        destinationLocation = new LatLng(latitude, longitude);
                        // In ra hoặc sử dụng LatLng
                        Log.d("getAssignedCustomerDestination", "Lấy được tọa độ điểm đến: ");
                        // Add marker to map
                        if (destinationMarker != null)
                            destinationMarker.remove();
                        destinationMarker = mMap.addMarker(new MarkerOptions()
                                .position(destinationLocation)
                                .title("Customer Pickup Location"));
                        // .icon(BitmapDescriptorFactory.fromResource(R.mipmap.icon_app_bee_customer)));
                        // Move and zoom camera to customer location
                        //mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(destinationLocation, 15));
                        Log.d(TAG, "Customer destination Location: Lat=" + latitude + ", Lng=" + longitude);

                    } else {
                        Log.e("getAssignedCustomerDestination", "Dữ liệu vị trí không đầy đủ!");
                    }
                } else {
                    Log.e("getAssignedCustomerDestination", "Không tìm thấy dữ liệu!");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("getAssignedCustomerDestination", "Lỗi khi đọc dữ liệu: " + error.getMessage());
            }
        });

    }

    private void getAssignedCustomerPickupInfo() {
        Log.d(TAG, "getAssignedCustomerPickupInfo: ");
        mCustomerInfor.setVisibility(View.VISIBLE);
        DatabaseReference mCustomerDatabase = FirebaseDatabase.getInstance().getReference()
                .child("Users").child("Customers").child(customerId);
        mCustomerDatabase.addValueEventListener(new ValueEventListener() {

            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.getChildrenCount() > 0) {
                    // Lấy thông tin người dùng từ snapshot
                    Map<String, Object> map = (Map<String, Object>) snapshot.getValue();
                    if (map.containsKey("name")) {
                        String mName = map.get("name").toString();
                        customerName.setText(mName);
                    }
                    if (map.containsKey("phone")) {
                        String mPhone = map.get("phone").toString();
                        customerPhone.setText(mPhone);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "getAssignedCustomerPickupInfo:Error fetching customer info: " + error.getMessage());

            }
        });
    }

    private double calculateDistance(LatLng a, LatLng b) {
        if (a == null) {
            Log.e(TAG, "calculateDistance: LatLng a is null");
            return -1;
        }
        if (b == null) {
            Log.e(TAG, "calculateDistance: LatLng b is null");
            return -1;
        }
        Location aLocation = new Location("");
        aLocation.setLatitude(a.latitude);
        aLocation.setLongitude(a.longitude);

        Location bLocation = new Location("");
        bLocation.setLatitude(customerLatLng.latitude);
        bLocation.setLongitude(b.longitude);
        // Tính khoảng cách (mét)
        float distanceInMeters = aLocation.distanceTo(bLocation);
        // Chuyển đổi sang km
        float distanceInKm = distanceInMeters / 1000;
        // Hiển thị thông báo
        return distanceInKm;
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
        if (userLocation == null) {
            Log.e(TAG, "changeDriverStatusToWorking: userLocation is null");
            return;
        }
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
                        // Log.d(TAG, "onLocationResult: Location - Lat: " + latitude + ", Lng: " + longitude);
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
        if (!checkLocationPermission())
            requestForPermissions();
        mMap = googleMap;
        // nút + và - zoom
        mMap.getUiSettings().setZoomControlsEnabled(true);


        mMap.setMyLocationEnabled(true); // Bật chức năng My Location

        mMap.getUiSettings().setMyLocationButtonEnabled(true); // Bật/Tắt nút My Location
//
//// Thay đổi vị trí của nút My Location
//        mMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
//            @Override
//            public void onMapLoaded() {
//                try {
//                    // Lấy tham chiếu đến nút My Location
//                    View locationButton = ((View) mapFragment.getView().findViewById(0x2)); // ID của nút My Location
//                    if (locationButton != null) {
//                        // Lấy layoutParams hiện tại của nút My Location
//                        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) locationButton.getLayoutParams();
//
//                        // Di chuyển nút My Location xuống dưới một chút
//                        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); // Giữ nó ở dưới cùng
//                        layoutParams.setMargins(0, 0, 0, 150);  // Thêm khoảng cách từ dưới lên (150px)
//
//                        locationButton.setLayoutParams(layoutParams);  // Cập nhật layout mới
//                    }
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
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
