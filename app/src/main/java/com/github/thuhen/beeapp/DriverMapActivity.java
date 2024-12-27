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
import android.widget.Switch;
import android.widget.TextView;
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

import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
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
    private FusedLocationProviderClient mFusedLocationProviderClient;
    private LocationCallback mLocationCallback;
    private LocationRequest mLocationRequest;
    private String customerId = "";
    private LatLng driverLatLng;
    private static final int LOCATION_REQUEST_CODE = 100;
    private static final String TAG = "DriverMapActivity"; // Tag dùng trong Logcat
    private Button mLogout;
    private Button mSettings;
    private Button mHistory;
    private Switch mWorkingSwitch;
    private LinearLayout mCustomerInfor;
    private ImageView customerProfileImage;
    private TextView customerName;
    private TextView customerPhone;
    //    private TextView customerDestination;
    private LatLng destinationLocation;
    private Marker destinationMarker;
    private LatLng customerLatLng;
    private int statusWorking = 0;
//    0= chua co khách,da ket thuc ;1= ghep được khách;2=đã chở khách, đang đi
    private Double routeDistance = (double) -1;
    private Marker pickupMarker;
    private Button mRideStatus;
    private boolean hasMovedCamera = false;
    private boolean saveLocationOnFb = false; // Mặc định không gọi
    private Location mLastCustomerLocation;
    private float rideDistance = 0;

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
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        getUserLocation();
//        getAssignedCustomer();
        mLogout = (Button) findViewById(R.id.logout);

        mSettings = findViewById(R.id.settings);
        mCustomerInfor = findViewById(R.id.customer_infor);
        customerProfileImage = findViewById(R.id.customer_profile_image);
        customerName = findViewById(R.id.customer_name);
        customerPhone = findViewById(R.id.customer_phone);
//        customerDestination = findViewById(R.id.customer_Destination);
        mRideStatus = findViewById(R.id.btn_ride_status);
        mHistory = (Button) findViewById(R.id.history);
        mWorkingSwitch = findViewById(R.id.workingSwitch);

        mLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "mLogout clicked: Removing location updates");
                stopLocationUpdates();

                deleteDriverAvailableOnFirebase();
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
                switch (statusWorking) {
                    case 1:
                        LatLngBounds.Builder builder = new LatLngBounds.Builder();
                        builder.include(customerLatLng); // Thêm điểm 1
                        builder.include(destinationLocation); // Thêm điểm 2
                        LatLngBounds bounds = builder.build();

// Lấy kích thước của màn hình để tính toán padding
                        int padding = 100; // Padding (khoảng cách từ các điểm đến viền màn hình, tính bằng pixel)

// Di chuyển và zoom camera đến bounds
                        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
                        mRideStatus.setText(R.string.end_ride);
                        statusWorking = 2;
                        break;

                    case 2:
                        recordRide();
                        endRide();
                        statusWorking =0;
                        break;
                }

            }
        });
        mWorkingSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (b)
                driverWorkingStatus(true);
            else {
                driverWorkingStatus(false);
            }
        });
    }

    public interface UserInformationCallback {
        void onComplete(boolean isComplete);
    }

    private void checkUserInformation(UserInformationCallback callback) {
        String userId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
        DatabaseReference mDriverDatabase = FirebaseDatabase.getInstance().getReference()
                .child("Users").child("Drivers").child(userId);

        mDriverDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean isValid = true;

                if (snapshot.exists() && snapshot.getChildrenCount() > 0) {
                    Map<String, Object> map = (Map<String, Object>) snapshot.getValue();

                    // Kiểm tra từng trường thông tin
                    if (!map.containsKey("name")) isValid = false;
                    if (!map.containsKey("phone")) isValid = false;
                    if (!map.containsKey("car")) isValid = false;
                    if (!map.containsKey("service")) isValid = false;
                } else {
                    isValid = false; // Snapshot không tồn tại hoặc không có dữ liệu
                }

                // Trả về kết quả qua callback
                callback.onComplete(isValid);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onComplete(false); // Có lỗi khi truy xuất dữ liệu
            }
        });
    }

    //    //Tính khoảng cách từ điểm đón đến điểm đến
//    private double calculateHaversineDistance(LatLng pickupLatLng, LatLng destinationLatLng) {
//        final int R = 6371; // Bán kính Trái Đất tính bằng km
//
//        double latDistance = Math.toRadians(destinationLatLng.latitude - pickupLatLng.latitude);
//        double lonDistance = Math.toRadians(destinationLatLng.longitude - pickupLatLng.longitude);
//
//        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
//                + Math.cos(Math.toRadians(pickupLatLng.latitude))
//                * Math.cos(Math.toRadians(destinationLatLng.latitude))
//                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
//
//        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
////        return R * c * 1000; // Trả về khoảng cách bằng mét
//        return R * c; // Trả về khoảng cách bằng mét
//    }
    private double calculateTotalCost(double distance) {
        double driverPercentage = 0.4;
        double costPerKm = 5; // Giá mỗi km
        double baseFare = 10; // Phí cố định
        double totalCost = baseFare + (distance) * costPerKm;
        return totalCost;
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
        /// Tính khoảng cách giữa điểm đón và điểm đến
        double distance = calculateDistance(customerLatLng, destinationLocation);
        rideDistance = (float) distance; // Lưu khoảng cách vào rideDistance
        Log.d(TAG, "Distance (haversine): " + (rideDistance) + " km");

        // Tính toán chi phí chuyến đi

        double totalCost = calculateTotalCost(rideDistance);
        HashMap<String, Object> map = new HashMap<>();
        map.put("driverId", driverId);
        map.put("customerId", customerId);
        map.put("rating", 0);
        map.put("timestamp", getCurrentTimestamp());
        Map<String, Double> destinationMap = new HashMap<>();
        destinationMap.put("latitude", destinationLocation.latitude);
        destinationMap.put("longitude", destinationLocation.longitude);
        map.put("destination", destinationMap);
        map.put("location/from/lat", customerLatLng.latitude);
        map.put("location/from/long", customerLatLng.longitude);
        map.put("location/to/lat", destinationLocation.latitude);
        map.put("location/to/long", destinationLocation.longitude);
        map.put("distance", rideDistance);
        map.put("cost", totalCost);
        // Lưu trạng thái thanh toán
        map.put("customerPaid", true); // Khách hàng đã thanh toán
        map.put("driverPaidOut", false); // Tài xế chưa được trả tiền
        historyRef.child(historyId).updateChildren(map);
        Log.d(TAG, "recordRide: Ride recorded successfully. Total cost: " + totalCost + " nghìn VND");

    }

    private Long getCurrentTimestamp() {
        Long timestamp = System.currentTimeMillis() / 1000;
        return timestamp;
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
                    statusWorking = 1;
                    // Lấy ID của khách hàng từ customerRideId
                    customerId = snapshot.getValue().toString();
                    Log.d(TAG, "Assigned customer ID: " + customerId);
                    // Lấy vị trí của khách hàng
                    if (getAssignedCustomerPickupLocation()) {
                        // chuyển trạng thái driver: available -> working
                        changeWorkingStatus(saveLocationOnFb);
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
//        customerDestination.setText(R.string.destination);
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
                                        .title("Điểm đón khách")
                                        .icon(BitmapDescriptorFactory.fromResource(R.mipmap.icon_marker_customer_foreground)));
                                // Move and zoom camera to customer location
//                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(customerLatLng, 15));
                                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                                builder.include(customerLatLng); // Thêm điểm 1
                                builder.include(driverLatLng); // Thêm điểm 2
                                LatLngBounds bounds = builder.build();

// Lấy kích thước của màn hình để tính toán padding
                                int padding = 100; // Padding (khoảng cách từ các điểm đến viền màn hình, tính bằng pixel)

// Di chuyển và zoom camera đến bounds
                                mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
                                Log.d(TAG, "Customer Pickup Location: Lat=" + locationLat + ", Lng=" + locationLng);


                                // Tính khoảng cách
                                double distanceInKm = calculateDistance(driverLatLng, customerLatLng);
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
                                .title("Đích đến")
                                .icon(BitmapDescriptorFactory.fromResource(R.mipmap.icon_marker_destination_foreground)));
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

    private void changeWorkingStatus(boolean b) {
        if (b) {
            if (driverLatLng == null) {
                Log.e(TAG, "changeDriverStatusToWorking: userLocation is null");
                return;
            }
            // Lấy ID của tài xế này từ driverId
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) {
                String driverId = currentUser.getUid();
                DatabaseReference driverRefAvailable = FirebaseDatabase.getInstance().getReference("driverAvailable");
                DatabaseReference driverRefWorking = FirebaseDatabase.getInstance().getReference("driversWorking");
                GeoFire geoFireAvailable = new GeoFire(driverRefAvailable);
                GeoFire geoFireWorking = new GeoFire(driverRefWorking);
                if (Objects.equals(customerId, "") || customerId == null) {

                    geoFireWorking.removeLocation(driverId);
                    geoFireAvailable.setLocation(driverId, new GeoLocation(driverLatLng.latitude, driverLatLng.longitude));
                    Log.d(TAG, "saved location available driver");
                } else {
                    geoFireAvailable.removeLocation(driverId);
                    geoFireWorking.setLocation(driverId, new GeoLocation(driverLatLng.latitude, driverLatLng.longitude));
                    Log.d(TAG, "saved location available driver");

                }
            }
        } else {
            return;
        }
    }

    private void disconnectDriver() {
        stopLocationUpdates();
        deleteDriverAvailableOnFirebase();
    }

    private void driverWorkingStatus(Boolean status) {
        if (status) {
            checkUserInformation(isComplete -> {
                if (isComplete) {
                    // Thông tin đầy đủ, xử lý logic tiếp theo
                    Log.d("Info", "Thông tin người dùng đầy đủ.");
                } else {
                    Log.d("Info", "Thông tin người dùng chưa đầy đủ.");
                    // Hiển thị thông báo lỗi
                    Toast.makeText(DriverMapActivity.this, R.string.request_fill_info, Toast.LENGTH_SHORT).show();

                    // Chuyển trạng thái Switch về ban đầu (tắt)

                    mWorkingSwitch.setChecked(false);
                }
            });
            saveLocationOnFb = true;
            startLocationUpdates();
            getAssignedCustomer();
        } else {
            if (statusWorking == 1) {
                mWorkingSwitch.setChecked(true);
                Toast.makeText(DriverMapActivity.this, R.string.request_done_ride, Toast.LENGTH_SHORT).show();
                return;
            }
            saveLocationOnFb = false;
            deleteDriverAvailableOnFirebase();
        }
    }

    @SuppressLint("MissingPermission")
    private void getUserLocation() {
        Log.d(TAG, "getUserLocation: Starting location updates");

        mLocationRequest = new LocationRequest.Builder(PRIORITY_HIGH_ACCURACY, 5000)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(2000)
                .setMaxUpdateDelayMillis(5000)
                .build();

        mLocationCallback = new LocationCallback() {
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
                        driverLatLng = new LatLng(latitude, longitude);

                        // Kiểm tra và tính khoảng cách di chuyển
                        if (mLastCustomerLocation != null) {
                            // Tính khoảng cách từ vị trí trước đó đến vị trí hiện tại
                            rideDistance += mLastCustomerLocation.distanceTo(location); // Khoảng cách tính bằng mét
                            Log.d(TAG, "onLocationResult: Distance traveled: " + (rideDistance / 1000) + " km"); // In khoảng cách bằng km
                        }

                        // Cập nhật vị trí hiện tại
                        mLastCustomerLocation = location;


                        if (!hasMovedCamera) {
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(driverLatLng, 20)); // Di chuyển camera đến vị trí mới
                            hasMovedCamera = true; // Đánh dấu là đã di chuyển camera
                        }
                        // Cập nhật vị trí người dùng trong Firebase
                        changeWorkingStatus(saveLocationOnFb);
                    } else {
                        Log.e(TAG, "onLocationResult: LastLocationResult is null");
                        return;
                    }
                }
            }
        };
// Bắt đầu yêu cầu cập nhật vị trí
        startLocationUpdates();
    }

    private void stopLocationUpdates() {
        mFusedLocationProviderClient.removeLocationUpdates(mLocationCallback);
        Log.d(TAG, "stopLocationUpdates: Removing location updates");
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        mFusedLocationProviderClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null);
        Log.d(TAG, "startLocationUpdates: Starting location updates");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume:");
        hasMovedCamera = false; // Reset lại cờ khi Activity được mở lại
        // Kiểm tra nếu người dùng đã đăng nhập và cần cập nhật vị trí
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            startLocationUpdates();
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
        mMap.setOnMapLoadedCallback(() -> {
            // Di chuyển nút My Location
            try {
                View locationButton = ((View) findViewById(Integer.parseInt("1")).getParent())
                        .findViewById(Integer.parseInt("2"));
                if (locationButton != null) {
                    RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) locationButton.getLayoutParams();
                    params.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0); // Xóa căn trên cùng
                    params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE); // Căn lề phải
                    params.addRule(RelativeLayout.CENTER_VERTICAL); // Căn giữa theo chiều dọc
                    params.setMargins(0, 0, 30, 0); // Cách mép phải 30px
                    locationButton.setLayoutParams(params);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            // Di chuyển nút Zoom + -
            try {
                View zoomControls = ((View) findViewById(Integer.parseInt("1"))).findViewById(Integer.parseInt("0"));
                if (zoomControls != null) {
                    RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) zoomControls.getLayoutParams();
                    params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE); // Căn lề phải
//                    params.addRule(RelativeLayout.CENTER_VERTICAL); // Căn giữa theo chiều dọc
                    params.addRule(RelativeLayout.BELOW, ((View) findViewById(Integer.parseInt("2"))).getId());
                    params.setMargins(0, 0, 30, 0); // Cách mép phải 30px
                    zoomControls.setLayoutParams(params);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });


    }

    public void deleteDriverAvailableOnFirebase() {
        // Xóa vị trí của người dùng khỏi Firebase
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String userId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
            DatabaseReference userLocationRef = FirebaseDatabase.getInstance().getReference("driverAvailable");
            GeoFire geoFire = new GeoFire(userLocationRef);

            geoFire.removeLocation(userId, (key, error) -> {
                if (error != null) {
                    Log.e(TAG, "deleteDriverAvailableOnFirebase:Failed to remove location: " + error.getMessage());
                } else {
                    Log.d(TAG, "deleteDriverAvailableOnFirebase:Location successfully removed for userId: " + userId);
                }
            });
            Log.d(TAG, "deleteDriverAvailableOnFirebase:Xóa vị trí của người dùng khỏi Firebase");

        } else {
            Log.e(TAG, "deleteDriverAvailableOnFirebase: deleted location, user logOut");
            return;
        }

    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop:");
        disconnectDriver();

    }
}
