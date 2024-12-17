package com.github.thuhen.beeapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.icu.text.SimpleDateFormat;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CustomerSettingActivity extends AppCompatActivity {
    private EditText mNameField;
    private EditText mPhoneField;
    private String mName;
    private String mPhone;
    private Button mConfirm;
    private Button mBack;
    private ImageView mProfileImage;
    private DatabaseReference mCustomerDatabase;
    private FirebaseAuth mAuth;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_customer_setting);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        mProfileImage = findViewById(R.id.profile_image);
        mNameField = findViewById(R.id.setting_name);
        mPhoneField = findViewById(R.id.setting_phone);
        mConfirm = findViewById(R.id.setting_confirm);
        mBack = findViewById(R.id.setting_back);
        mAuth = FirebaseAuth.getInstance();
        userId = mAuth.getCurrentUser().getUid();
        mCustomerDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Customers").child(userId);
        getUserInformation();
        mConfirm.setOnClickListener(view -> {
            saveUserInformation();
        });
        mBack.setOnClickListener(view -> {
            finish();
            return;
        });
        mProfileImage.setOnClickListener(view -> {
//            openImagePicker();

        });


    }
//    private Uri resultUri;
//    // Đăng ký ActivityResultLauncher để chọn ảnh
//
//    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
//            new ActivityResultContracts.StartActivityForResult(),
//            result -> {
//                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
//                    resultUri = result.getData().getData();
//                    mProfileImage.setImageURI(resultUri); // Hiển thị ảnh đã chọn lên ImageView
//                } else {
//                    Toast.makeText(this, "Không chọn ảnh nào!", Toast.LENGTH_SHORT).show();
//                }
//            }
//    );
//    private void openImagePicker() {
//        Intent intent = new Intent(Intent.ACTION_PICK);
//        intent.setType("image/*");
//        imagePickerLauncher.launch(intent); // Khởi chạy trình chọn ảnh
//
//    }

    private void getUserInformation() {
        mCustomerDatabase.addValueEventListener(new ValueEventListener() {

            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.getChildrenCount() > 0) {
                    // Lấy thông tin người dùng từ snapshot
                    Map<String, Object> map = (Map<String, Object>) snapshot.getValue();
                    if (map.containsKey("name")) {
                        mName = map.get("name").toString();
                        mNameField.setText(mName);
                    }
                    if (map.containsKey("phone")) {
                        mPhone = map.get("phone").toString();
                        mPhoneField.setText(mPhone);
                    }


                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }
    private void saveUserInformation() {
        mName = mNameField.getText().toString();
        mPhone = mPhoneField.getText().toString();
        if (mName.isEmpty() || mPhone.isEmpty()) {
            Toast.makeText(this, "Tên và số điện thoại không được để trống!", Toast.LENGTH_SHORT).show();
            return;
        }
        // Tạo một Map để lưu thông tin user
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("name", mName);
        userInfo.put("phone", mPhone);

        // Cập nhật thông tin user lên Firebase Database
        mCustomerDatabase.updateChildren(userInfo);

//        if (resultUri != null) {
//            // Tham chiếu đến Firebase Storage
//            FirebaseStorage storage = FirebaseStorage.getInstance();
//            StorageReference filePath = storage.getReference().child("profile_images").child(userId);
//            Bitmap bitmap = null;
//            try {
//                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), resultUri);
//                ByteArrayOutputStream baos = new ByteArrayOutputStream();
//                bitmap.compress(Bitmap.CompressFormat.JPEG, 20, baos);
//                byte[] data = baos.toByteArray();
//                bitmap.recycle(); // Giải phóng bộ nhớ
//
//                Log.d("CustomerSetting", "Image bitmap created and compressed.");
//
//                // Tải ảnh lên Firebase Storage
//                UploadTask uploadTask = filePath.putBytes(data);
//                uploadTask.addOnSuccessListener(taskSnapshot -> {
//                    // Lấy URL download ảnh từ Firebase Storage
//                    filePath.getDownloadUrl().addOnSuccessListener(downloadUri -> {
//                        Log.d("CustomerSetting", "Image upload successful, download URL: " + downloadUri.toString());
//
//                        // Cập nhật URL ảnh vào Firebase Database
//                        Map<String, Object> newImage = new HashMap<>();
//                        newImage.put("profileImageUrl", downloadUri.toString());
//                        mCustomerDatabase.updateChildren(newImage);
//
//                        Toast.makeText(this, R.string.confirm_infor_successful, Toast.LENGTH_SHORT).show();
//                        finish();
//                    }).addOnFailureListener(e -> {
//                        Log.e("CustomerSetting", "Failed to get download URL: " + e.getMessage());
//                        Toast.makeText(this, "Lỗi khi lấy URL ảnh!", Toast.LENGTH_SHORT).show();
//                    });
//                }).addOnFailureListener(e -> {
//                    Log.e("CustomerSetting", "Image upload failed: " + e.getMessage());
//                    Toast.makeText(this, "Tải ảnh lên thất bại!", Toast.LENGTH_SHORT).show();
//                });
//            } catch (Exception e) {
//                e.printStackTrace();
//                Log.e("CustomerSetting", "Error processing image: " + e.getMessage());
//                Toast.makeText(this, "Lỗi khi xử lý ảnh!", Toast.LENGTH_SHORT).show();
//                return;
//            }
//
//
//        } else {
//            // Không có ảnh để tải lên, chỉ lưu thông tin cơ bản
//
//            return;
//        }
        Toast.makeText(this, R.string.confirm_infor_successful, Toast.LENGTH_SHORT).show();
    }

}