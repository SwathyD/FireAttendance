package com.example.fireadmission;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.DialogInterface;
import android.content.Intent;
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
    ArrayList<String> student = new ArrayList<>();
    private String subject, time;
    String email;
    Executor listener = new Executor() {
        @Override
        public void execute(View v) {
            TeacherActivity.this.list.removeView(v);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher);
        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();
        email = getIntent().getStringExtra("key");

        mConnectionsClient = Nearby.getConnectionsClient(this);
    }



    public void addStudent(String text, String status){
        CustomView v1 = new CustomView(this, text, status);
        v1.setOnDeleteListener(listener);
        list.addView(v1);
        final Handler handlerUI = new Handler(Looper.getMainLooper());
        Runnable r = new Runnable() {
            public void run() {
                ((ScrollView)findViewById(R.id.scrollView)).fullScroll(View.FOCUS_DOWN);
            }
        };
        handlerUI.post(r);
//        TextView view = new TextView(this);
//        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
//        params.setMargins(60,0,0,10);
//        view.setLayoutParams(params);
//        view.setText(text);
//        if(status.equals("new"))
//            view.setTextColor(getResources().getColor(R.color.user_background));
//        else if(status.equals("error"))
//            view.setTextColor(Color.parseColor("#F30404"));
//        else if(status.equals("warning"))
//            view.setTextColor(Color.parseColor("#FF9800"));
//        view.setTextSize(18);
//        view.setOnClickListener(v -> {
//            AlertDialog.Builder alert = new AlertDialog.Builder(this);
//            alert.setTitle("Alert!");
//            alert.setMessage("Unmark "+text+" ?");
//            alert.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
//                @Override
//                public void onClick(DialogInterface dialog, int which) {
//                   list.removeView(view);
//                   student.remove(text);
//                }
//            });
//
//            alert.setNegativeButton("No", new DialogInterface.OnClickListener() {
//                @Override
//                public void onClick(DialogInterface dialog, int which) {
//                    //Cancel
//                }
//            });
//
//            alert.show();
//        });
//        list.addView(view);
        student.add(text);
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
                addStudent(value, "new");
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
            subject.setError("Enter Subject");
            flag = false;
        }

        if(TextUtils.isEmpty(start.getText()))   {
            start.setError("Enter Lecture's Start Time");
            flag = false;
        }

        if(TextUtils.isEmpty(end.getText()))   {
            end.setError("Enter Lecture's End Time");
            flag = false;
        }

        if(flag){
            setContentView(R.layout.activity_attendance);
            TextView text = findViewById(R.id.textView6);
            text.setText(subject.getText().toString());
            this.subject = subject.getText().toString();

            text = findViewById(R.id.textView7);
            text.setText(start.getText().toString().concat(" - ").concat(end.getText().toString()));
            this.time = start.getText().toString().concat(" - ").concat(end.getText().toString());

            TeacherActivity.this.list = findViewById(R.id.list);

            startDiscovery();
        }

    }

    private class DiscoverReceiveBytesPayloadListener extends PayloadCallback {

        @Override
        public void onPayloadReceived(String endpointId, Payload payload) {
            byte[] receivedBytes = payload.asBytes();

            String msg = new String(receivedBytes);

            Toast.makeText(TeacherActivity.this, msg, Toast.LENGTH_SHORT).show();

            TeacherActivity.this.addStudent(msg, "");
        }

        @Override
        public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
            // Bytes payloads are sent as a single chunk, so you'll receive a SUCCESS update immediately
            // after the call to onPayloadReceived().
        }
    }

    private ConnectionLifecycleCallback discoverConnectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
                    Log.i("INFO", "DISCOVER CONNECTION INITIATED");

                    if(TeacherActivity.this.destEndpoint == null){
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

    public void stopAttendance(View v){
        createFile();
        Payload bytesPayload = Payload.fromBytes( "STOP".getBytes() );
        TeacherActivity.this.mConnectionsClient.sendPayload(TeacherActivity.this.destEndpoint, bytesPayload);

    }

    public void createFile(){
        try
        {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd");
            Date now = new Date();
            String fileName = formatter.format(now) + "_"+time.split("-")[0].replaceAll(" ","")+ ".csv";
            File root = new File(Environment.getExternalStorageDirectory(), "Attendance");
            //File root = new File(Environment.getExternalStorageDirectory(), "Notes");
            if (!root.exists())
            {
                root.mkdirs();
            }
            File file = new File(root, fileName);

            FileWriter writer = new FileWriter(file,true);
            writer.append("Subject: "+subject+"\nTime: "+time+"\nStudent List: \n");
            for(int i=0; i<student.size(); i++){
                writer.append(student.get(i)+"\n");
            }
            writer.append("\n\n\n");
            writer.flush();
            writer.close();
            Toast.makeText(this, "Data has been written to Attendance/"+fileName, Toast.LENGTH_SHORT).show();
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

        }
        catch(IOException e)
        {
            e.printStackTrace();

        }

    }

    public void sendEmail(String fileName){
        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.setType("text/plain");
        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[] {email});
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Attendance report for "+subject);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd");
        emailIntent.putExtra(Intent.EXTRA_TEXT, "Attendance Report: \nDate: "+formatter.format(new Date())+"\nTime: "+time);
        File root = Environment.getExternalStorageDirectory();
        String pathToMyAttachedFile = "Attendance/"+fileName;
        File file = new File(root, pathToMyAttachedFile);
        if (!file.exists() || !file.canRead()) {
            Toast.makeText(this, "Could not attach the file "+fileName, Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = Uri.fromFile(file);
        emailIntent.putExtra(Intent.EXTRA_STREAM, uri);
        startActivity(Intent.createChooser(emailIntent, "Pick an Email provider"));
    }
}