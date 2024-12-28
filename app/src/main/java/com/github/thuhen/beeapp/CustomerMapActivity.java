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
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RatingBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
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
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
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

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class CustomerMapActivity extends FragmentActivity implements OnMapReadyCallback {
    private GoogleMap mMap;
    private ActivityCustomerMapBinding binding;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private LatLng customerLatLng;
    private Button mRequest;
    private Button mLogout;
    private Button mSetting;
    private Button mHistory;
    private TextView mDistance;
    private TextView mCost;
    //vi tri đón khách nay
    private LinearLayout mCostLayout;
    private LatLng pickupLatLng;
    private Marker pickupMarker;
    private LatLng destinationLocation;
    private Marker destinationMarker;
    private LinearLayout mDriverInfo;
    private ImageView driverProfileImage;
    private TextView driverName;
    private TextView driverPhone;
    private TextView driverCar;
    private RatingBar mRatingBar;
    private static final int LOCATION_REQUEST_CODE = 100;
    private static final String TAG = "CustomerMapActivity"; // Tag dùng trong Logcat
    private Boolean requestBol = false;
    private RadioGroup mRadioGroup;
    private RadioGroup mRadioGroupPay;
    private String requestservice;

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
        mDriverInfo = findViewById(R.id.driver_info);
        driverProfileImage = findViewById(R.id.driver_profile_image);
        driverName = findViewById(R.id.driver_name);
        driverPhone = findViewById(R.id.driver_phone);
        driverCar = findViewById(R.id.driver_car);
        mRadioGroup = findViewById(R.id.radioGroup);
        mRadioGroup.check(R.id.xeMay);
        mRadioGroupPay = findViewById(R.id.radioGroup_pay);
        mRadioGroupPay.check(R.id.cash);
        mLogout = (Button) findViewById(R.id.logout);
        mRequest = findViewById(R.id.button_call_request);
        mSetting = (Button) findViewById(R.id.setting);
        mHistory = (Button) findViewById(R.id.history);
        mRatingBar = (RatingBar) findViewById(R.id.ratingBar);
        mCostLayout = findViewById(R.id.cost_layout);
        mDistance = findViewById(R.id.distance);
        mCost = findViewById(R.id.cost);

        mLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "mLogout clicked: Removing location updates");
                stopLocationUpdates();
                //endRide();

                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(CustomerMapActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
                return;

            }
        });

        mRequest.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("MissingPermission")
            @Override
            public void onClick(View view) {
                if (destinationMarker == null) {
                    Toast.makeText(CustomerMapActivity.this, R.string.destination_not_set, Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "onClick: destinationMarker is null");
                    return;
                }

                if (!requestBol) {
                    checkPayMomo();
                    int selectedId = mRadioGroup.getCheckedRadioButtonId();
                    final RadioButton radioButton = (RadioButton) findViewById(selectedId);
                    if (radioButton.getText() == null) {
                        return;
                    }
                    requestservice = radioButton.getText().toString();
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
                                    pickupLatLng = new LatLng(location.getLatitude(), location.getLongitude());

                                    pickupMarker = mMap.addMarker(new MarkerOptions().position(pickupLatLng)
                                            .title(getString(R.string.pickup_here))
                                            .icon(BitmapDescriptorFactory.fromResource(R.mipmap.icon_marker_customer_foreground)))
                                    ;

                                    getClosestDriver();
                                }
                            });

                } else {
                    //xóa kết nối giữa driver và customer: xóa customerRequest con bên trong driver
                    if (driverFoundID != null) {
                        DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference()
                                .child("Users").child("Drivers").child(driverFoundID).child("customerRequest");
                        // driverRef.setValue(true);
                        driverRef.removeValue();
                        Log.d(TAG, "mRequestonClick: driverRef.setValue(true)");
                        driverFoundID = null;
                    }
                    endRide();

                }
            }
        });

        mSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(CustomerMapActivity.this, CustomerSettingActivity.class);
                startActivity(intent);
                return;
            }

        });
        mHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "mHistory onClick: ");
                Intent intent = new Intent(CustomerMapActivity.this, HistoryActivity.class);
                intent.putExtra("customerOrDriver", "Customers");
                startActivity(intent);
                return;
            }
        });

//        //Khởi tạo Google Places API
//        if (!Places.isInitialized()) {
//            Places.initialize(getApplicationContext(), getString(R.string.maps_api_key));
//        }
//
//        // Tìm AutocompleteSupportFragment trong layout
//        autocompleteFragment = (AutocompleteSupportFragment)
//                getSupportFragmentManager().findFragmentById(R.id.autoComplete_fragment);
//        autocompleteFragment.setPlaceFields(Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS));
//        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
//            @Override
//            public void onPlaceSelected(Place place) {
//                // Xử lý khi người dùng chọn địa điểm
//                destination = place.getName().toString();   // Lấy tên địa điểm
//            }
//
//            @Override
//            public void onError(Status status) {
//                // Xử lý khi có lỗi xảy ra
//                Log.e("Place", "An error occurred: " + status);
//            }
//        });
//        PlacesClient placesClient = Places.createClient(this);
//        FindAutocompletePredictionsRequest predictionsRequest = FindAutocompletePredictionsRequest.builder()
//                .setQuery("your_query")
//                .setTypeFilter(TypeFilter.ESTABLISHMENT)
//                .build();
//
//        placesClient.findAutocompletePredictions(predictionsRequest)
//                .addOnFailureListener(exception -> {
//                    Log.e("PlacesAPIError", "Request failed. Message: " + exception.getMessage());
//                    if (exception instanceof ApiException) {
//                        ApiException apiException = (ApiException) exception;
//                        Log.e("PlacesAPIError", "Status code: " + apiException.getStatusCode());
//                    }
//                });
    }

    private void checkPayMomo() {
        Log.d(TAG, "checkPayMomo: ");
        // Lấy ID của RadioButton được chọn
        int selectedId = mRadioGroupPay.getCheckedRadioButtonId();
        // Kiểm tra xem có RadioButton nào được chọn không
        if (selectedId == -1) {
            // Nếu chưa chọn, thông báo cho người dùng
            Toast.makeText(this, "Vui lòng chọn phương thức thanh toán", Toast.LENGTH_SHORT).show();
            return;
        }

// Lấy RadioButton được chọn
        RadioButton selectedRadioButton = findViewById(selectedId);

// Kiểm tra nội dung text của RadioButton và thực hiện hành động tương ứng
        if (selectedRadioButton.getText().equals("MoMo")) {
            // Xử lý khi chọn MoMo
            Toast.makeText(this, "Bạn đã chọn thanh toán bằng MoMo", Toast.LENGTH_SHORT).show();
            // Thêm logic cho thanh toán MoMo
        }
    }

    private DatabaseReference driverHasEndRef;
    ValueEventListener driverHasEndListener;

    private void getHasRiderEnd() {
        // Lấy thông tin tài xế này từ Firebase
        driverHasEndRef = FirebaseDatabase.getInstance().getReference()
                .child("Users").child("Drivers").child(driverFoundID).child("customerRequest");
        // Lắng nghe sự thay đổi của thông tin tài xế
        driverHasEndListener = driverHasEndRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {

                } else {
                    endRide();
                    if (destinationMarker != null)
                        destinationMarker.remove();
                    mRequest.setText(R.string.set_destination);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

    }


    private void endRide() {
        Log.d(TAG, "endRide: ");
        requestBol = false;
        geoQuery.removeAllListeners();
        driverLocationRef.removeEventListener(driverLocationListener);
        driverHasEndRef.removeEventListener(driverHasEndListener);
        cancelRequestClosestDriver();
        hideDriverInfoUI();
        flagCalCost = true;
    }

    private void hideDriverInfoUI() {
        Log.d(TAG, "hideDriverInfoUI: Hiding driver info UI");
        mDriverInfo.setVisibility(View.GONE);
        driverName.setText("");
        driverPhone.setText("");
        driverCar.setText("");
        driverProfileImage.setImageResource(R.mipmap.icon_default_user);
    }

    private void cancelRequestClosestDriver() {
        //xóa customerRequest lớn ở bên ngoài
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
//        if (destinationMarker != null)
//            destinationMarker.remove();
        mCostLayout.setVisibility(View.VISIBLE);
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
                (new GeoLocation(pickupLatLng.latitude, pickupLatLng.longitude), radius);
        // Xóa tất cả listener cũ
        geoQuery.removeAllListeners();
        // Thêm listener mới
        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                // Nếu tài xế đã được ghép, lấy tọa độ vị trí tài xế
                if (!driverFound && requestBol) {
                    DatabaseReference mCustomerDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(key);
                    mCustomerDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.exists() && snapshot.getChildrenCount() > 0) {
                                Map<String, Object> drivermap = (Map<String, Object>) snapshot.getValue();
                                if (driverFound) {
                                    return;
                                }

                                if (drivermap.get("service").equals(requestservice)) {
                                    driverFound = true;
                                    driverFoundID = key;//
                                    Log.e(TAG, "getClosestDriver: Driver ID is " + key);
                                    //vào firebase tài xế gần nhất
                                    DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference()
                                            .child("Users").child("Drivers").child(driverFoundID).child("customerRequest");
                                    // lấy id customer
                                    String customerID = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();

                                    HashMap map = new HashMap();
                                    map.put("customerRideId", customerID);//lưu id customer
                                    Map<String, Double> destinationMap = new HashMap<>();
                                    destinationMap.put("latitude", destinationLocation.latitude);
                                    destinationMap.put("longitude", destinationLocation.longitude);
                                    map.put("destination", destinationMap);
                                    // cập nhật driver
                                    driverRef.updateChildren(map);
                                    // hiện market vị trí tài xế
                                    getDriverLocation();
                                    // Hiển thị thông tin tài xế
                                    getDriverPickupInfo();
                                    mRequest.setText(R.string.looking_for_driver_location);
                                    getHasRiderEnd();
                                }
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {

                        }
                    });


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

    private void getDriverPickupInfo() {
        Log.d(TAG, "getDriverPickupInfo: Getting driver pickup info");
        // Hiển thị thông tin tài xế
        flagCalCost=false;
        mCostLayout.setVisibility(View.GONE);
        mDriverInfo.setVisibility(View.VISIBLE);
        DatabaseReference mCustomerDatabase = FirebaseDatabase.getInstance().getReference()
                .child("Users").child("Drivers").child(driverFoundID);
        mCustomerDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Nếu tài xế đã được ghép, lấy thông tin tài xế
                if (snapshot.exists() && snapshot.getChildrenCount() > 0) {
                    // Lấy thông tin người dùng từ snapshot
                    Map<String, Object> map = (Map<String, Object>) snapshot.getValue();
                    if (map.containsKey("name")) {
                        String mName = map.get("name").toString();
                        driverName.setText(mName);
                    }
                    if (map.containsKey("phone")) {
                        String mPhone = map.get("phone").toString();
                        driverPhone.setText(mPhone);
                    }
                    if (map.containsKey("car")) {
                        String mCar = map.get("car").toString();
                        driverCar.setText(mCar);
                    }
                    int ratingSum = 0;
                    float ratingsTotal = 0;
                    float ratingsAvg = 0;
                    for (DataSnapshot child : snapshot.child("rating").getChildren()) {
                        ratingSum = ratingSum + Integer.valueOf(child.getValue().toString());
                        ratingsTotal++;
                    }
                    if (ratingsTotal != 0) {
                        ratingsAvg = ratingSum / ratingsTotal;
                        mRatingBar.setRating(ratingsAvg);
                        mRatingBar.setIsIndicator(true);
                    }
                } else
                // Nếu không tìm thấy tài xế, hiển thị thông báo
                {
                    Toast.makeText(CustomerMapActivity.this, "Không có thông tin của tài xế này", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "getDriverPickupInfoonDataChange: No driver found");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
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
                            if (pickupLatLng != null) {
                                // Tính khoảng cách
                                double distance_driver = calculateDistance(pickupLatLng, driverLatLng);
                                if (distance_driver < 0.01)
                                    mRequest.setText(R.string.driver_is_here);
                                else {
                                    mRequest.setText(String.format("Driver Found: %.2f km away", distance_driver));

                                }
                                Log.d(TAG, "Distance between Customer and Driver: " + distance_driver + " km");

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
                .title("Tài xế đang đến")
                .icon(BitmapDescriptorFactory.fromResource(R.mipmap.icon_marker_driver_foreground)));

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(pickupLatLng); // Thêm điểm 1
        builder.include(driverLatLng); // Thêm điểm 2
        LatLngBounds bounds = builder.build();

// Lấy kích thước của màn hình để tính toán padding
        int padding = 150; // Padding (khoảng cách từ các điểm đến viền màn hình, tính bằng pixel)

// Di chuyển và zoom camera đến bounds
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
//        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pickupLatLng, 15));
    }

    //luư tọa độ điểm đón của khách
//    private void saveCustomerRequest() {
//        if (pickupLocation == null) {
//            Log.e(TAG, "saveCustomerRequest: pickupLocation is null");
//            return;
//        }
//        // lấy id customer
//        String userId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
//        // Lưu vị trí khách hàng vào Firebase customerRequest
//        DatabaseReference customerRequestRef = FirebaseDatabase.getInstance().getReference().child("customerRequest").child(userId);
//        HashMap<String, Object> requestMap = new HashMap<>();
//
//        requestMap.put("l", Arrays.asList(pickupLocation.latitude, pickupLocation.longitude));
//        // update location pịckup at customerRequest
//        customerRequestRef.updateChildren(requestMap);
//        Log.d(TAG, "saveCustomerRequest: Customer request saved with location: " + pickupLocation.latitude + ", " + pickupLocation.longitude);
//
//
//    }

    private double calculateDistance(LatLng customerLatLng, LatLng driverLatLng) {
        if (customerLatLng == null || driverLatLng == null) {
            Log.e(TAG, "calculateDistance: One of the locations is null");
            return -1;
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

        // Hiển thị khoảng cách
        // Cập nhật TextView hoặc Button
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

    private double calculateTotalCost(double distance) {
        double driverPercentage = 0.4;
        double costPerKm = 5; // Giá mỗi km
        double baseFare = 10; // Phí cố định
        double totalCost = baseFare + (distance) * costPerKm;
        return totalCost;
    }

    private void getDestinateCost(boolean b) {
        if (!b) return;
        mCostLayout.setVisibility(View.VISIBLE);
        mDriverInfo.setVisibility(View.GONE);
        double distance;
        double cost;
        distance = calculateDistance(customerLatLng, destinationLocation);
        cost = calculateTotalCost(distance);
        mDistance.setText(String.format(Locale.getDefault(), "%.2f km", distance));
        mCost.setText(String.format(Locale.getDefault(), "%.2f nghìn VND", cost));

    }

    private boolean flagCalCost= true;
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
                        customerLatLng = new LatLng(latitude, longitude);

                        if (!hasMovedCamera) {
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(customerLatLng, 20)); // Di chuyển camera đến vị trí mới
                            hasMovedCamera = true; // Đánh dấu là đã di chuyển camera
                        }
                        if (destinationLocation != null)
                            getDestinateCost(flagCalCost);
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

        //Xử lý sự kiện khi người dùng nhấp vào bản đồ

        // có thể thêm chức năng: cửa sổ xác nhận đặt điểm đón
        mMap.setOnMapClickListener(latLng -> {
            Log.d(TAG, "onMapClick: User clicked on map");

            if (!requestBol) {
                // Lưu tọa độ của điểm được chọn
                destinationLocation = latLng;
                // Xóa tất cả marker cũ
                if (destinationMarker != null) {
                    destinationMarker.remove();
                }
                // Thêm marker mới cho điểm được chọn
                destinationMarker = mMap.addMarker(new MarkerOptions().position(destinationLocation)
                        .title("Đích đến")
                        .icon(BitmapDescriptorFactory.fromResource(R.mipmap.icon_marker_destination_foreground)));
                Toast.makeText(this, "Destination location set!", Toast.LENGTH_SHORT).show();
                mRequest.setText(R.string.call_bee);
                // Lưu tọa độ điểm đón vào Firebase
                //saveCustomerRequest();
            }
        });
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


    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop:");
        stopLocationUpdates();

    }
}

