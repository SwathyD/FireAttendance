package com.example.fireadmission;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

public class LoginActivity extends AppCompatActivity {

    private RadioGroup radioGroup;
    private RadioButton userType;

    private void redirect(String user_type, String key){
        Intent intent;
        if(user_type.equals("student"))
            intent = new Intent(getBaseContext(), StudentActivity.class);
        else
            intent = new Intent(getBaseContext(), TeacherActivity.class);
        intent.putExtra("key", key);
        startActivity(intent);
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
            }
        });
    }

    public void login(View view){
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
}