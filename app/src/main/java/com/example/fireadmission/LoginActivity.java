package com.example.fireadmission;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;

public class LoginActivity extends AppCompatActivity {

    private RadioGroup radioGroup;
    private RadioButton userType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();
    }

    public void login(View view){
        radioGroup = (RadioGroup) findViewById(R.id.radioGrp);
        int selectedID = radioGroup.getCheckedRadioButtonId();
        if(selectedID == R.id.radioButton2)
            setContentView(R.layout.activity_student);
        else
            setContentView(R.layout.activity_teacher);
    }
}