package com.example.fireadmission;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.biometric.BiometricPrompt;

import android.app.VoiceInteractor;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.security.keystore.StrongBoxUnavailableException;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.Executor;

import static android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL;

public class StudentActivity extends AppCompatActivity {

    private String UID;
    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    TextView mStatusText;
    ProgressBar mSpinner;
    Button mBiometricLoginButton;

    private ConnectionsClient mConnectionsClient;
    private String sourceEndpoint = null;
    private String destEndpoint   = null;

    JSONObject attendanceData = null;
    LinearLayout studentAttendaceList;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student);

        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();

        UID = getIntent().getStringExtra("key");
        TextView uid = findViewById(R.id.studentUID);
        uid.setText(UID);

        studentAttendaceList = findViewById(R.id.StudentAttendance);
        setstudentAttendance();

        /* Function Call for Test */
        updateStudentAttendance("ADS","KKD",4);

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
                .setTitle("Biometric login for my app")
                .setSubtitle("Log in using your biometric credential")
                .setNegativeButtonText("Use account password")
                .build();

        // Prompt appears when user clicks "Log in".
        // Consider integrating with the keystore to unlock cryptographic operations,
        // if needed by your app.
        mBiometricLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                biometricPrompt.authenticate(promptInfo);
            }
        });
    }

    private class DiscoverReceiveBytesPayloadListener extends PayloadCallback {

        @Override
        public void onPayloadReceived(String endpointId, Payload payload) {
            // This always gets the full data of the payload. Will be null if it's not a BYTES
            // payload. You can check the payload type with payload.getType().
            byte[] receivedBytes = payload.asBytes();

            String msg = new String(receivedBytes);

            Payload bytesPayload = Payload.fromBytes( receivedBytes );
            StudentActivity.this.mConnectionsClient.sendPayload(StudentActivity.this.sourceEndpoint, bytesPayload);

            Toast.makeText(StudentActivity.this, msg, Toast.LENGTH_SHORT).show();
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

                    if(StudentActivity.this.destEndpoint == null){
                        Nearby.getConnectionsClient(StudentActivity.this).acceptConnection(endpointId, new DiscoverReceiveBytesPayloadListener());
                    }
                }

                @Override
                public void onConnectionResult(String endpointId, ConnectionResolution result) {

                    switch (result.getStatus().getStatusCode()) {
                        case ConnectionsStatusCodes.STATUS_OK:
                            StudentActivity.this.destEndpoint = endpointId;
                            Log.i("INFO", "DISCOVER CONNECTION RESULT OK");
                            Toast.makeText(StudentActivity.this, "DISCOVER CONNECTION RESULT OK", Toast.LENGTH_SHORT).show();

                            stopDiscovery();

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

                    StudentActivity.this.destEndpoint = null;
                }
            };

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
            // This always gets the full data of the payload. Will be null if it's not a BYTES
            // payload. You can check the payload type with payload.getType().
            byte[] receivedBytes = payload.asBytes();

            String msg = new String(receivedBytes);

            if(msg.equals("STOP")){

                if(StudentActivity.this.destEndpoint != null){
                    Payload bytesPayload = Payload.fromBytes( receivedBytes );
                    StudentActivity.this.mConnectionsClient.sendPayload(StudentActivity.this.destEndpoint, bytesPayload);
                }

                StudentActivity.this.mConnectionsClient.stopAllEndpoints();

                StudentActivity.this.updateStatus("Attendance Marked!");

                StudentActivity.this.mSpinner.setVisibility(View.INVISIBLE);

            }

            Toast.makeText(StudentActivity.this, msg, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
            // Bytes payloads are sent as a single chunk, so you'll receive a SUCCESS update immediately
            // after the call to onPayloadReceived().
        }
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
                            Toast.makeText(StudentActivity.this, "ADVERT CONNECTION RESULT OK", Toast.LENGTH_SHORT).show();

                            stopAdvertising();

                            StudentActivity.this.updateStatus("Marking Attendance...");

                            StudentActivity.this.markMyAttendance();

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

    private void markMyAttendance() {
        Payload bytesPayload = Payload.fromBytes( UID.getBytes() );
        StudentActivity.this.mConnectionsClient.sendPayload(StudentActivity.this.sourceEndpoint, bytesPayload);
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
            this.studentAttendaceList.removeAllViews(); 
            while(keys.hasNext()) {
                String temp = keys.next();
                if (this.attendanceData.get(temp) instanceof JSONObject) {
                    total_lec = ((JSONObject) this.attendanceData.get(temp)).getInt("total_lec");
                    int present_lec = ((JSONObject) this.attendanceData.get(temp)).getInt("present_lec");
                    addAttendace(temp+": " + (present_lec) + " / " + total_lec );
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
    private void setstudentAttendance(){
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
                        addAttendace(temp+": " + (present_lec) + " / " + total_lec );
                    }
                }

            }
            else{
                this.attendanceData = new JSONObject();
            }
        }catch (Exception e){

        }
    }

    public void addAttendace(String text){
        TextView view = new TextView(this);
        view.setText(text);
        this.studentAttendaceList.addView(view);
    }
}

