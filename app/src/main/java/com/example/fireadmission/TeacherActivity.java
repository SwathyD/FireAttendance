package com.example.fireadmission;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class TeacherActivity extends AppCompatActivity {

    LinearLayout list;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher);
        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();

    }

    public void addStudent(String text){
        TextView view = new TextView(this);
        view.setText(text);
        list.addView(view);
    }

    public void manuallyAddStudent(View v){
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("Add");
        alert.setMessage("Enter the UID of the student");

        final EditText input = new EditText(this);
        alert.setView(input);
        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String value = input.getText().toString();
                addStudent(value);
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //Cancel
            }
        });

        alert.show();
    }

    public void showAttendance(View v){

        EditText subject = findViewById(R.id.subject);
        EditText start = findViewById(R.id.start_time);
        EditText end = findViewById(R.id.end_time);
        boolean flag = true;
        if(TextUtils.isEmpty(subject.getText()))   {
            subject.setError("Enter subject");
            flag = false;
        }

        if(TextUtils.isEmpty(start.getText()))   {
            start.setError("Enter lecture start time");
            flag = false;
        }

        if(TextUtils.isEmpty(end.getText()))   {
            end.setError("Enter lecture end time");
            flag = false;
        }

        if(flag){
            setContentView(R.layout.activity_attendance);
            TextView text = findViewById(R.id.textView6);
            text.setText(subject.getText().toString());

            text = findViewById(R.id.textView7);
            text.setText(start.getText().toString().concat(" - ").concat(end.getText().toString()));

            list = findViewById(R.id.customList);
            addStudent("2019240068");
        }

    }
}