package com.example.fireadmission;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.biometric.BiometricPrompt;
import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
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
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.Executor;

public class StudentActivity extends AppCompatActivity {

    private int maxConnections = 2;

    private String UID;
    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    private String self_endpointId;

    private String subject;
    private String time_slot;
    private String prof;

    private String attendance_status;

    // key is the destination and value is the next hop
    private HashMap<String, String> relay_table = new HashMap<>();

    TextView mStatusText;
    ProgressBar mSpinner;
    Button mBiometricLoginButton;
    GifDrawable drawable_check;
    GifImageView check_image;

    private ConnectionsClient mConnectionsClient;
    private String sourceEndpoint = null;
    private ArrayList<String> destEndpoints = new ArrayList<>();

    JSONObject attendanceData = null;
    LinearLayout studentAttendanceList;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student);

        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();

        check_image    = (GifImageView) findViewById(R.id.check_mark);
        drawable_check = (GifDrawable) check_image.getDrawable();
        drawable_check.pause();
        drawable_check.seekToFrame(0);
        drawable_check.setLoopCount(1);

        UID = getIntent().getStringExtra("key");
        TextView uid = findViewById(R.id.studentUID);
        uid.setText(UID);

        studentAttendanceList = findViewById(R.id.StudentAttendance);

        setStudentAttendance();

        mStatusText = findViewById(R.id.textView3);
        mSpinner    = findViewById(R.id.progressBar);
        mBiometricLoginButton = findViewById(R.id.button2);

        mConnectionsClient = Nearby.getConnectionsClient(this);

        executor = ContextCompat.getMainExecutor(this);
        biometricPrompt = new BiometricPrompt(StudentActivity.this,
                executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode,  @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Toast.makeText(getApplicationContext(), "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                Toast.makeText(getApplicationContext(), "Authentication succeeded!", Toast.LENGTH_SHORT).show();

                StudentActivity.this.mBiometricLoginButton.setEnabled(false);
                StudentActivity.this.mBiometricLoginButton.setVisibility(View.INVISIBLE);

                StudentActivity.this.mStatusText.setVisibility(View.VISIBLE);
                StudentActivity.this.mSpinner.setVisibility(View.VISIBLE);

                startAdvertising();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(getApplicationContext(), "Authentication failed",
                        Toast.LENGTH_SHORT)
                        .show();
            }
        });



        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric login for our app")
                .setSubtitle("Log in using your biometric credential")
                .setDeviceCredentialAllowed(true)
                .build();

        // Prompt appears when user clicks "Log in".
        // Consider integrating with the keystore to unlock cryptographic operations,
        // if needed by your app.
        mBiometricLoginButton.setOnClickListener(v -> biometricPrompt.authenticate(promptInfo));
    }

    // this is called whenever the leaf node wants to send data to teacher... relay is done here
    private class DiscoverReceiveBytesPayloadListener extends PayloadCallback {

        @Override
        public void onPayloadReceived(String endpointId, Payload payload) {
            try{
                byte[] receivedBytes = payload.asBytes();
                String msg = new String(receivedBytes);
                JSONObject data = new JSONObject(msg);

                switch(data.getString("msg_type")){
                    case "MARK":
                            StudentActivity.this.process_MARK_Message(endpointId, data.getString("uid"));
                            StudentActivity.this.relay_MARK_Message(receivedBytes);
                        break;
                    default:
                        Log.e("FAIL", "RECEIVED UNEXPECTED MESSAGE ON RELAY" + data.getString("msg_type"));
                }

            }catch(Exception e){
                Log.e("FAIL", "ERROR WHILE RELAYING DATA", e);
            }
        }

        @Override
        public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
            // Bytes payloads are sent as a single chunk, so you'll receive a SUCCESS update immediately
            // after the call to onPayloadReceived().
        }
    }

    private void process_MARK_Message(String fromEndpointId, String destUid){
         StudentActivity.this.relay_table.put(destUid, fromEndpointId);
    }

    private void relay_MARK_Message(byte[] data){
        Payload bytesPayload = Payload.fromBytes( data );
        StudentActivity.this.mConnectionsClient.sendPayload(StudentActivity.this.sourceEndpoint, bytesPayload);
    }

    private ConnectionLifecycleCallback discoverConnectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
                    Log.i("INFO", "DISCOVER CONNECTION INITIATED");

                    if(StudentActivity.this.destEndpoints.size() < StudentActivity.this.maxConnections - 1){
                        Nearby.getConnectionsClient(StudentActivity.this).acceptConnection(endpointId, new DiscoverReceiveBytesPayloadListener());
                    }
                }

                @Override
                public void onConnectionResult(String endpointId, ConnectionResolution result) {

                    switch (result.getStatus().getStatusCode()) {
                        case ConnectionsStatusCodes.STATUS_OK:
                            StudentActivity.this.destEndpoints.add( endpointId );

                            Log.i("INFO", "DISCOVER CONNECTION RESULT OK");
                            Toast.makeText(StudentActivity.this, "DISCOVER CONNECTION RESULT OK", Toast.LENGTH_SHORT).show();

                            if(StudentActivity.this.destEndpoints.size() == StudentActivity.this.maxConnections - 1){
                                stopDiscovery();
                            }

                            StudentActivity.this.send_INIT_Message(endpointId);

                            break;
                        case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
                            Log.i("INFO", "DISCOVER CONNECTION RESULT REJECTED");
                            Toast.makeText(StudentActivity.this, "DISCOVER CONNECTION RESULT REJECTED", Toast.LENGTH_SHORT).show();
                            break;
                        case ConnectionsStatusCodes.STATUS_ERROR:
                            Log.i("INFO", "DISCOVER CONNECTION RESULT ERROR");
                            Toast.makeText(StudentActivity.this, "DISCOVER CONNECTION ERROR", Toast.LENGTH_SHORT).show();
                            break;
                        default:
                            Log.i("INFO", "DISCOVER CONNECTION RESULT UNKNOWN");
                    }
                }

                @Override
                public void onDisconnected(String endpointId) {
                    Log.e("FAIL", "DISCOVER DISCONNECTED FROM ENDPOINT " + endpointId);

                    StudentActivity.this.destEndpoints.remove(endpointId);
                }
            };

    private void send_INIT_Message(String endpointId) {
        try {
            JSONObject attendance_context = new JSONObject();

            attendance_context.put("msg_type" , "INIT");
            attendance_context.put("prof"     , StudentActivity.this.prof);
            attendance_context.put("subject"  , StudentActivity.this.subject);
            attendance_context.put("time_slot", StudentActivity.this.time_slot);
            attendance_context.put("whoami"   , endpointId);

            Payload bytesPayload = Payload.fromBytes( attendance_context.toString().getBytes() );
            StudentActivity.this.mConnectionsClient.sendPayload(endpointId, bytesPayload);

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
                        Nearby.getConnectionsClient(StudentActivity.this)
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

        Nearby.getConnectionsClient(StudentActivity.this)
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
        Toast.makeText(StudentActivity.this, "STOPPED DISCOVERY", Toast.LENGTH_SHORT).show();
    }


    private class AdvertReceiveBytesPayloadListener extends PayloadCallback {

        @Override
        public void onPayloadReceived(String endpointId, Payload payload) {
            try {
                byte[] receivedBytes = payload.asBytes();
                String msg = new String(receivedBytes);
                JSONObject data = new JSONObject(msg);

                switch(data.getString("msg_type")){
                    case "STOP":
                            StudentActivity.this.process_STOP_Message(data);
                        break;

                    case "INIT":
                            StudentActivity.this.process_INIT_Message(data);
                            StudentActivity.this.send_MARK_Message();
                        break;

                    case "ACK":
                            if(data.getString("destUid").equals(StudentActivity.this.UID)){
                                StudentActivity.this.process_ACK_Message(data);
                            }else{
                                StudentActivity.this.relay_ACK_Message(data.getString("destUid"), receivedBytes);
                            }
                        break;

                    default:
                        Log.e("FAIL", "UNEXPECTED MESSAGE WHILE RECEIVING DATA FROM MESH" + data.getString("msg_type"));
                }

                Log.i("MSG RECEIVED FROM MESH", msg);
            } catch (Exception e) {
                Log.e("ERROR", "MAJOR ERROR", e);
            }
        }

        @Override
        public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
            // Bytes payloads are sent as a single chunk, so you'll receive a SUCCESS update immediately
            // after the call to onPayloadReceived().
        }
    }

    private void relay_ACK_Message(String destUid, byte[] data) {
         String next_hop = StudentActivity.this.relay_table.get(destUid);

        Payload bytesPayload = Payload.fromBytes( data );
        StudentActivity.this.mConnectionsClient.sendPayload(next_hop, bytesPayload);
    }

    private void process_ACK_Message(JSONObject data) {
        // data store karna hai jo user ko dikhega when STOP trigger hoga
        try{
            StudentActivity.this.attendance_status = data.getString("status");

            updateStudentAttendance(StudentActivity.this.subject,StudentActivity.this.prof,2);

        }catch(Exception ex){
            StudentActivity.this.attendance_status = "ERROR";

            Log.e("FAIL", "ERROR WHILE READING ACK", ex);
        }
    }

    private void process_STOP_Message(JSONObject data){
        ArrayList<Task<Void>> all_msgs = new ArrayList<>();
        byte[] bytes_payload = data.toString().getBytes();

        for(String endpointID : StudentActivity.this.destEndpoints){
            Payload payload = Payload.fromBytes(bytes_payload);

            Task<Void> msg = StudentActivity.this.mConnectionsClient.sendPayload(endpointID, payload);

            all_msgs.add(msg);
        }

        Tasks.whenAll(all_msgs).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                StudentActivity.this.mConnectionsClient.stopAllEndpoints();
                StudentActivity.this.afterMarked();
            }
        });

    }

    private void process_INIT_Message(JSONObject data){
        try{
            StudentActivity.this.self_endpointId = data.getString("whoami");
            StudentActivity.this.prof            = data.getString("prof");
            StudentActivity.this.subject         = data.getString("subject");
            StudentActivity.this.time_slot       = data.getString("time_slot");

            updateStatus(StudentActivity.this.subject + " ("+StudentActivity.this.prof+")\n\n"+StudentActivity.this.time_slot);
        }catch(Exception e){
            Log.e("FAIL", "ERROR WHILE PARSING INIT MESSAGE", e);
        }
    }

    private void send_MARK_Message() {
        try {
            JSONObject mark = new JSONObject();

            mark.put("msg_type" , "MARK");
            mark.put("uid"      , StudentActivity.this.UID);
            mark.put("source"   , StudentActivity.this.self_endpointId);

            Payload bytesPayload = Payload.fromBytes( mark.toString().getBytes() );
            StudentActivity.this.mConnectionsClient.sendPayload(StudentActivity.this.sourceEndpoint, bytesPayload);

        } catch (Exception e) {
            Log.e("FAIL", "EXCEPTION WHILE SENDING INIT MESSAGE", e);
        }
    }

    private void afterMarked(){
        StudentActivity.this.updateStatus(StudentActivity.this.attendance_status);
        StudentActivity.this.mSpinner.setVisibility(View.INVISIBLE);
        StudentActivity.this.check_image.setVisibility(View.VISIBLE);

        GifDrawable drawable_check = (GifDrawable) ((GifImageView)findViewById(R.id.check_mark)).getDrawable();
        drawable_check.start();

        stopAdvertising();
        stopDiscovery();
    }

    private ConnectionLifecycleCallback advertConnectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
                    Log.i("INFO", "ADVERT CONNECTION INITIATED");

                    if(StudentActivity.this.sourceEndpoint == null){
                        Nearby.getConnectionsClient(StudentActivity.this).acceptConnection(endpointId, new AdvertReceiveBytesPayloadListener());
                    }
                }

                @Override
                public void onConnectionResult(String endpointId, ConnectionResolution result) {

                    switch (result.getStatus().getStatusCode()) {
                        case ConnectionsStatusCodes.STATUS_OK:
                            StudentActivity.this.sourceEndpoint = endpointId;
                            Log.i("INFO", "ADVERT CONNECTION RESULT OK");
                            Toast.makeText(StudentActivity.this, "ADVERT CONNECTION RESULT OOK WITH " + endpointId, Toast.LENGTH_SHORT).show();

                            stopAdvertising();

                            startDiscovery();

                            break;
                        case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
                            Log.i("INFO", "ADVERT CONNECTION RESULT REJECTED");
                            Toast.makeText(StudentActivity.this, "ADVERT CONNECTION RESULT REJECTED", Toast.LENGTH_SHORT).show();
                            break;
                        case ConnectionsStatusCodes.STATUS_ERROR:
                            Log.i("INFO", "ADVERT CONNECTION RESULT ERROR");
                            Toast.makeText(StudentActivity.this, "ADVERT CONNECTION ERROR", Toast.LENGTH_SHORT).show();
                            break;
                        default:
                            Log.i("INFO", "ADVERT CONNECTION RESULT UNKNOWN");
                    }
                }

                @Override
                public void onDisconnected(String endpointId) {
                    Log.e("FAIL", "ADVERT DISCONNECTED FROM ENDPOINT " + endpointId);

                    StudentActivity.this.sourceEndpoint = null;
                }
            };

    private void updateStatus(String s) {
        StudentActivity.this.mStatusText.setText(s);
    }

    private void startAdvertising() {
        AdvertisingOptions advertisingOptions = new AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build();

        Nearby.getConnectionsClient(this)
        .startAdvertising(
                "NICKNAME",
                "RANDOM STRING",
                advertConnectionLifecycleCallback,
                advertisingOptions
        )
        .addOnSuccessListener(
                (Void unused) -> {
                    Log.i("INFO", "STARTED ADVERTISING");
                })
        .addOnFailureListener(
                (Exception e) -> {
                    Log.e("FAIL", "FAILED TO START ADVERTISING", e);
                });
    }

    private void stopAdvertising() {
        mConnectionsClient.stopAdvertising();
        Log.i("INFO", "STOPPED ADVERTISING");
        Toast.makeText(StudentActivity.this, "STOPPED ADVERTISING", Toast.LENGTH_SHORT).show();
    }

    /*Get Hashmap which has Student Attendance Data*/
    private JSONObject getStudentAttendance(){
        return attendanceData;
    }

    /*Update Hashmap which has Student Attendance Data and update the shared Prefrence/File Data*/
    private void updateStudentAttendance(String subject,String teacherCode, int total_lec){

        //Update Hash
        try {
            String key = subject + '-' + teacherCode;
            if (this.attendanceData.has(key)) {
                JSONObject temp = this.attendanceData.getJSONObject(key);
                temp.put("total_lec",total_lec);
                temp.put("present_lec",temp.getInt("present_lec")+1);
                this.attendanceData.put(key, temp);
            } else {
                JSONObject temp = new JSONObject();
                temp.put("total_lec",total_lec);
                temp.put("present_lec",1);
                this.attendanceData.put(key, temp );
            }
            //Update Shared Prefrences
            Iterator<String> keys = this.attendanceData.keys();
            this.studentAttendanceList.removeAllViews();
            while(keys.hasNext()) {
                String temp = keys.next();
                if (this.attendanceData.get(temp) instanceof JSONObject) {
                    total_lec = ((JSONObject) this.attendanceData.get(temp)).getInt("total_lec");
                    int present_lec = ((JSONObject) this.attendanceData.get(temp)).getInt("present_lec");
                    addAttendance(temp, present_lec, total_lec );
                }
            }
            SharedPreferences sharedPreferences = getPreferences(Context.MODE_PRIVATE);

            SharedPreferences.Editor editor = sharedPreferences.edit();


            String attendanceDataString = attendanceData.toString();
            editor.putString("attendanceData", attendanceDataString);

            boolean commit = editor.commit();
        }catch (Exception e){

        }
    }
    private void setStudentAttendance(){
        try {

            SharedPreferences sharedPreferences = getPreferences(Context.MODE_PRIVATE);
            String key = "attendanceData";
            if(sharedPreferences.contains(key)) {
                String attendanceString = sharedPreferences.getString(key, null);
                this.attendanceData = new JSONObject(attendanceString);

                Iterator<String> keys = this.attendanceData.keys();

                while(keys.hasNext()) {
                    String temp = keys.next();
                    if (this.attendanceData.get(temp) instanceof JSONObject) {
                        int total_lec = ((JSONObject) this.attendanceData.get(temp)).getInt("total_lec");
                        int present_lec = ((JSONObject) this.attendanceData.get(temp)).getInt("present_lec");
                        addAttendance(temp, present_lec, total_lec );
                    }
                }

            }
            else{
                this.attendanceData = new JSONObject();
            }
        }catch (Exception e){

        }
    }

    public void addAttendance(String sub, int present_lec, int total_lec){
        TextView view = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(60,0,0,10);
        view.setLayoutParams(params);
        String text = sub+": " + (present_lec) + " / " + total_lec;
        view.setText(text);
        if((float)present_lec/(float)total_lec<0.75){
            view.setTextColor(Color.parseColor("#F30404"));
        }

        view.setTextSize(18);
        this.studentAttendanceList.addView(view);
    }
}

