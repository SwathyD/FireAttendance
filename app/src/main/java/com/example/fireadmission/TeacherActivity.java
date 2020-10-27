package com.example.fireadmission;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;
import com.google.android.gms.tasks.OnSuccessListener;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class TeacherActivity extends AppCompatActivity {

    LinearLayout list;
    private ConnectionsClient mConnectionsClient;
    private String destEndpoint = null;
    private String subject = null;
    private String time_slot = null;
    private String prof = null;

    ArrayList<StudentRecord> student = new ArrayList<>();
    Executor listener = new Executor() {
        @Override
        public void execute(View v) {
            TeacherActivity.this.list.removeView(v);
            //remove the student record from students arraylist
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher);
        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();

        prof = getIntent().getStringExtra("key");

        mConnectionsClient = Nearby.getConnectionsClient(this);
    }


    public void addStudent(String text, String status) {
        CustomView v1 = new CustomView(this, text, status);
        v1.setOnDeleteListener(listener);
        list.addView(v1);
        final Handler handlerUI = new Handler(Looper.getMainLooper());
        Runnable r = new Runnable() {
            public void run() {
                ((ScrollView) findViewById(R.id.scrollView)).fullScroll(View.FOCUS_DOWN);
            }
        };
        handlerUI.post(r);
        student.add(new StudentRecord(text, ""));
    }

    public void manuallyAddStudent(View v) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("Add");
        alert.setMessage("Enter the UID of the student");

        final EditText input = new EditText(this);
        alert.setView(input);
        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String value = input.getText().toString();
                addStudent(value, "new");
            }
        });

        alert.setNegativeButton("Cancel", (dialog, which) -> {
            //Cancel
        });

        alert.show();
    }

    public void showAttendance(View v) {

        EditText subject = findViewById(R.id.subject);
        EditText start = findViewById(R.id.start_time);
        EditText end = findViewById(R.id.end_time);

        boolean flag = true;
        if (TextUtils.isEmpty(subject.getText())) {
            subject.setError("Enter Subject");
            flag = false;
        }

        if (TextUtils.isEmpty(start.getText())) {
            start.setError("Enter Lecture's Start Time");
            flag = false;
        }

        if (TextUtils.isEmpty(end.getText())) {
            end.setError("Enter Lecture's End Time");
            flag = false;
        }

        if (flag) {
            setContentView(R.layout.activity_attendance);

            TeacherActivity.this.subject = subject.getText().toString();
            TeacherActivity.this.time_slot = start.getText().toString() + " - " + end.getText().toString();

            ((TextView) findViewById(R.id.textView6))
                    .setText(TeacherActivity.this.subject);

            ((TextView) findViewById(R.id.textView7))
                    .setText(TeacherActivity.this.time_slot);

            TeacherActivity.this.list = findViewById(R.id.list);

            startDiscovery();
        }

    }

    private class DiscoverReceiveBytesPayloadListener extends PayloadCallback {

        @Override
        public void onPayloadReceived(String endpointId, Payload payload) {
            try {
                byte[] receivedBytes = payload.asBytes();
                String msg = new String(receivedBytes);
                JSONObject data = new JSONObject(msg);

                switch (data.getString("msg_type")) {
                    case "MARK":
                        TeacherActivity.this.process_MARK_Message(data);
                        break;

                    default:
                        Log.e("FAIL", "UNEXPECTED MESSAGE TYPE RECEIVED " + data.getString("msg_type"));
                }

            } catch (Exception e) {
                Log.e("FAIL", "ERROR WHILE RECEIVING MESSAGE ", e);
            }
        }

        @Override
        public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
            // Bytes payloads are sent as a single chunk, so you'll receive a SUCCESS update immediately
            // after the call to onPayloadReceived().
        }
    }

    private void send_ACK_Message(String endpointId, String status) {
        try {
            JSONObject response = new JSONObject();
            response.put("msg_type", "ACK");
            response.put("status", status);
            response.put("dest", endpointId);

            Payload bytesPayload = Payload.fromBytes(response.toString().getBytes());
            TeacherActivity.this.mConnectionsClient.sendPayload(TeacherActivity.this.destEndpoint, bytesPayload);

        } catch (Exception e) {
            Log.e("FAIL", "ERROR WHEN SENDING ACK", e);
        }
    }

    private void process_MARK_Message(JSONObject data) {
        // auth code se dekhke authenticate karna hai
        try {
            TeacherActivity.this.addStudent(data.getString("uid"), "normal");

            send_ACK_Message(data.getString("source"), "MARKED:" + getAlphaNumericString(10));
        } catch (Exception e) {
            Log.e("FAIL", "ERROR WHEN PROCESSING MARK", e);
        }
    }

    private ConnectionLifecycleCallback discoverConnectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
                    Log.i("INFO", "DISCOVER CONNECTION INITIATED");

                    if (TeacherActivity.this.destEndpoint == null) {
                        Nearby.getConnectionsClient(TeacherActivity.this).acceptConnection(endpointId, new TeacherActivity.DiscoverReceiveBytesPayloadListener());
                    }
                }

                @Override
                public void onConnectionResult(String endpointId, ConnectionResolution result) {

                    switch (result.getStatus().getStatusCode()) {
                        case ConnectionsStatusCodes.STATUS_OK:
                            TeacherActivity.this.destEndpoint = endpointId;
                            Log.i("INFO", "DISCOVER CONNECTION RESULT OK");
                            Toast.makeText(TeacherActivity.this, "DISCOVER CONNECTION RESULT OK", Toast.LENGTH_SHORT).show();

                            stopDiscovery();

                            TeacherActivity.this.send_INIT_Message();

                            break;
                        case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
                            Log.i("INFO", "DISCOVER CONNECTION RESULT REJECTED");
                            Toast.makeText(TeacherActivity.this, "DISCOVER CONNECTION RESULT REJECTED", Toast.LENGTH_SHORT).show();
                            break;
                        case ConnectionsStatusCodes.STATUS_ERROR:
                            Log.i("INFO", "DISCOVER CONNECTION RESULT ERROR");
                            Toast.makeText(TeacherActivity.this, "DISCOVER CONNECTION ERROR", Toast.LENGTH_SHORT).show();
                            break;
                        default:
                            Log.i("INFO", "DISCOVER CONNECTION RESULT UNKNOWN");
                    }
                }

                @Override
                public void onDisconnected(String endpointId) {
                    Log.e("FAIL", "DISCOVER DISCONNECTED FROM ENDPOINT " + endpointId);

                    TeacherActivity.this.destEndpoint = null;
                }
            };

    private void send_INIT_Message() {
        try {
            JSONObject attendance_context = new JSONObject();

            attendance_context.put("msg_type", "INIT");
            attendance_context.put("prof", TeacherActivity.this.prof);
            attendance_context.put("subject", TeacherActivity.this.subject);
            attendance_context.put("time_slot", TeacherActivity.this.time_slot);
            attendance_context.put("whoami", TeacherActivity.this.destEndpoint);

            Payload bytesPayload = Payload.fromBytes(attendance_context.toString().getBytes());
            TeacherActivity.this.mConnectionsClient.sendPayload(TeacherActivity.this.destEndpoint, bytesPayload);

        } catch (Exception e) {
            Log.e("FAIL", "EXCEPTION WHILE SENDING INIT MESSAGE", e);
        }
    }

    private void startDiscovery() {
        EndpointDiscoveryCallback endpointDiscoveryCallback =
                new EndpointDiscoveryCallback() {
                    @Override
                    public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
                        // An endpoint was found. We request a connection to it.
                        Nearby.getConnectionsClient(TeacherActivity.this)
                                .requestConnection("NICKNAME", endpointId, discoverConnectionLifecycleCallback)
                                .addOnSuccessListener(
                                        (Void unused) -> {
                                            Log.i("INFO", "CONNECTION REQUESTED");
                                        })
                                .addOnFailureListener(
                                        (Exception e) -> {
                                            Log.e("FAIL", "FAILED CONNECTION REQUEST", e);
                                        });
                    }

                    @Override
                    public void onEndpointLost(String endpointId) {
                        Log.i("FAIL", "ENDPOINT LOST");
                    }
                };


        DiscoveryOptions discoveryOptions = new DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build();

        Nearby.getConnectionsClient(TeacherActivity.this)
                .startDiscovery("RANDOM STRING", endpointDiscoveryCallback, discoveryOptions)
                .addOnSuccessListener(
                        (Void unused) -> {
                            Log.i("INFO", "STARTED DISCOVERING");
                        })
                .addOnFailureListener(
                        (Exception e) -> {
                            Log.e("FAIL", "FAILED TO DISCOVER", e);
                        });
    }

    protected void stopDiscovery() {
        mConnectionsClient.stopDiscovery();
        Log.i("INFO", "STOPPED DISCOVERY");
        Toast.makeText(TeacherActivity.this, "STOPPED DISCOVERY", Toast.LENGTH_SHORT).show();
    }

    public void stopAttendance(View v) {
        send_STOP_Message();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 101);
        }

        createFile();
    }

    private void send_STOP_Message() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("msg_type", "STOP");

            Payload bytesPayload = Payload.fromBytes(obj.toString().getBytes());
            TeacherActivity.this.mConnectionsClient.sendPayload(TeacherActivity.this.destEndpoint, bytesPayload).addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    TeacherActivity.this.mConnectionsClient.stopAllEndpoints();
                }
            });
        } catch (Exception e) {
            Log.e("FAIL", "FAILED WHILE SENDING STOP MESSAGE", e);
        }
    }

    static String getAlphaNumericString(int n) {
        // chose a Character random from this String
        String AlphaNumericString = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                + "0123456789"
                + "abcdefghijklmnopqrstuvwxyz";

        // create StringBuffer size of AlphaNumericString
        StringBuilder sb = new StringBuilder(n);

        for (int i = 0; i < n; i++) {

            // generate a random number between
            // 0 to AlphaNumericString variable length
            int index = (int) (AlphaNumericString.length() * Math.random());

            // add Character one by one in end of sb
            sb.append(AlphaNumericString.charAt(index));
        }

        return sb.toString();
    }

    public void createFile() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "File write permission not granted", Toast.LENGTH_LONG).show();
                return;
            }
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd");
            Date now = new Date();
            String fileName = formatter.format(now) + "_" + TeacherActivity.this.time_slot.split("-")[0].replaceAll(" ", "") + ".csv";
            File root = new File(Environment.getExternalStorageDirectory(), "Attendance");
            //File root = new File(Environment.getExternalStorageDirectory(), "Notes");
            if (!root.exists()) {
                root.mkdirs();
            }
            File file = new File(root, fileName);

            FileWriter writer = new FileWriter(file, true);
            writer.append("Subject: " + subject + "\nTime: " + TeacherActivity.this.time_slot + "\nStudent List: \n");
            for (int i = 0; i < student.size(); i++) {
                writer.append(student.get(i) + "\n");
            }
            writer.append("\n\n\n");
            writer.flush();
            writer.close();
            Toast.makeText(this, "Data has been written to Attendance/" + fileName, Toast.LENGTH_SHORT).show();
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setTitle("Email");
            alert.setMessage("Do you want to proceed with sending the file through email?");
            alert.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    sendEmail(fileName);
                }
            });

            alert.setNegativeButton("No", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    //Cancel
                }
            });

            alert.show();

        } catch (IOException e) {
            e.printStackTrace();

        }

    }

    public void sendEmail(String fileName) {
        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.setType("text/plain");
        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{TeacherActivity.this.prof});
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Attendance report for " + subject);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd");
        emailIntent.putExtra(Intent.EXTRA_TEXT, "Attendance Report: \nDate: " + formatter.format(new Date()) + "\nTime: " + TeacherActivity.this.time_slot);
        File root = Environment.getExternalStorageDirectory();
        String pathToMyAttachedFile = "Attendance/" + fileName;
        File file = new File(root, pathToMyAttachedFile);
        if (!file.exists() || !file.canRead()) {
            Toast.makeText(this, "Could not attach the file " + fileName, Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = Uri.fromFile(file);
        emailIntent.putExtra(Intent.EXTRA_STREAM, uri);
        startActivity(Intent.createChooser(emailIntent, "Share Attendance Records via"));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                createFile();
            } else {
                Toast.makeText(TeacherActivity.this, "File Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}

class StudentRecord{
    String uid;
    String response;
    StudentRecord(String uid, String response){
        this.uid = uid;
        this.response = response;
    }
}