package com.example.fireadmission;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.ConnectionsClient;

import org.json.JSONException;
import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {

    private RadioGroup radioGroup;
    private RadioButton userType;

    private static final String[] REQUIRED_PERMISSIONS =
            new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.READ_PHONE_STATE,
            };
    protected String[] getRequiredPermissions() {
        return REQUIRED_PERMISSIONS;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences sharedPreferences = getPreferences(Context.MODE_PRIVATE);

        String user = sharedPreferences.getString("user_type", null);
        if(user!=null){
            redirect(user, sharedPreferences.getString("key", null));
        }
        setContentView(R.layout.activity_login);
        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();
        radioGroup = (RadioGroup) findViewById(R.id.radioGrp);
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener(){
            public void onCheckedChanged(RadioGroup group, int checkedId){
                EditText text = findViewById(R.id.inputText);
                if(checkedId == R.id.radioButton2){
                    text.setHint("UID");
                }
                else{
                    text.setHint("Email address");
                }

                text.setError(null);
                text.setText("");
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!hasPermissions(this, getRequiredPermissions())) {
            if (Build.VERSION.SDK_INT < 23) {
                ActivityCompat.requestPermissions(
                        this, getRequiredPermissions(), 1);
            } else {
                requestPermissions(getRequiredPermissions(), 1);
            }
        }
    }

    public void login(View view){
        // check hasPermissions before continuing

        EditText text = findViewById(R.id.inputText);
        int selectedID = radioGroup.getCheckedRadioButtonId();
        if(TextUtils.isEmpty(text.getText()))   {
            text.setError("Enter "+text.getHint());
            return;
        }

        Intent intent;
        SharedPreferences type_pref = getPreferences( Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = type_pref.edit();
        if(selectedID == R.id.radioButton2){
            intent = new Intent(getBaseContext(), StudentActivity.class);
            editor.putString("user_type", "student");
        }
        else{
            intent = new Intent(getBaseContext(), TeacherActivity.class);
            editor.putString("user_type", "teacher");
        }
        editor.putString("key", text.getText().toString());
        editor.commit();

        intent.putExtra("key", text.getText().toString());
        startActivity(intent);
    }

    private void redirect(String user_type, String key){
        Intent intent;
        if(user_type.equals("student"))
            intent = new Intent(getBaseContext(), StudentActivity.class);
        else
            intent = new Intent(getBaseContext(), TeacherActivity.class);
        intent.putExtra("key", key);
        startActivity(intent);
    }

    public static boolean hasPermissions(Context context, String... permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}