package com.github.thuhen.beeapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
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

import java.util.HashMap;
import java.util.Map;

public class CustomerSettingActivity extends AppCompatActivity {
    private EditText mNameField;
    private EditText mPhoneField;
    private String mName;
    private String mPhone;
    private Button mConfirm;
    private Button mBack;
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
        mNameField = findViewById(R.id.setting_name);
        mPhoneField = findViewById(R.id.setting_phone);
        mConfirm = findViewById(R.id.setting_confirm);
        mBack = findViewById(R.id.setting_back);
        mAuth= FirebaseAuth.getInstance();
        userId= mAuth.getCurrentUser().getUid();
        mCustomerDatabase= FirebaseDatabase.getInstance().getReference().child("Users").child("Customers").child(userId);
        getUserInformation();
        mConfirm.setOnClickListener(view -> {
           saveUserInformation();
        });
        mBack.setOnClickListener(view -> {
            finish();
            return;
        });


    }
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
        Map userInfo = new HashMap();
        mName = mNameField.getText().toString();
        mPhone = mPhoneField.getText().toString();
        userInfo.put("name", mName);
        userInfo.put("phone", mPhone);
        mCustomerDatabase.updateChildren(userInfo);
        Toast.makeText(this, R.string.confirm_infor_successful, Toast.LENGTH_SHORT).show();



    }
}