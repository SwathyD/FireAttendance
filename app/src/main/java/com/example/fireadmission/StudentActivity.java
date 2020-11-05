package com.example.fireadmission;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.biometric.BiometricPrompt;
import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKeys;
import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
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

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.Executor;

public class StudentActivity extends AppCompatActivity {

    private int maxConnections = 2;

    private String   UID;
    private Executor executor;

    private BiometricPrompt            biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    private String self_endpointId;
    private String subject;
    private String time_slot;
    private String prof;
    private int    lec_no;

    private String     attendance_status;
    private JSONObject attendance_context = new JSONObject();

    // key is the destination and value is the next hop
    private HashMap<String, String> relay_table = new HashMap<>();

    TextView    mStatusText;
    ProgressBar mSpinner;
    Button      mBiometricLoginButton;

    GifDrawable  drawable_check, drawable_exclm;
    GifImageView check_image   , exclm_image;

    private ConnectionsClient mConnectionsClient;
    private String            sourceEndpoint = null;
    private ArrayList<String> destEndpoints  = new ArrayList<>();
    private boolean           isMarked       = false;
    private boolean           stopReceived   = false;

    private String isSecure    = null;
    private int    present_for = 0;
    private String authCode    = null;

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

        exclm_image    = (GifImageView) findViewById(R.id.exclm_mark);
        drawable_exclm = (GifDrawable) exclm_image.getDrawable();
        drawable_exclm.pause();
        drawable_exclm.seekToFrame(0);
        drawable_exclm.setLoopCount(1);

        UID = getIntent().getStringExtra("key");
        TextView uid = findViewById(R.id.studentUID);
        uid.setText(UID);

        studentAttendanceList = findViewById(R.id.StudentAttendance);

        setStudentAttendance();

        mStatusText = findViewById(R.id.textView3);
        mSpinner    = findViewById(R.id.progressBar);
        mBiometricLoginButton = findViewById(R.id.button2);

        mConnectionsClient = Nearby.getConnectionsClient(this);
        isMarked = false;

        executor = ContextCompat.getMainExecutor(this);
        biometricPrompt = new BiometricPrompt(StudentActivity.this,
                executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode,  @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);

                StudentActivity.this.isSecure = "no";

                StudentActivity.this.startAttendanceMarkingProcess();
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);

                StudentActivity.this.isSecure = "yes";

                StudentActivity.this.startAttendanceMarkingProcess();
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
                        .setTitle("Please Authenticate Yourself.")
                        .setDeviceCredentialAllowed(true)
                        .build();

        // Prompt appears when user clicks "Log in".
        // Consider integrating with the keystore to unlock cryptographic operations,
        // if needed by your app.
        mBiometricLoginButton.setOnClickListener(v -> biometricPrompt.authenticate(promptInfo));
    }

    private void startAttendanceMarkingProcess(){
        StudentActivity.this.mBiometricLoginButton.setEnabled(false);
        StudentActivity.this.mBiometricLoginButton.setVisibility(View.INVISIBLE);

        StudentActivity.this.mStatusText.setVisibility(View.VISIBLE);
        StudentActivity.this.mSpinner.setVisibility(View.VISIBLE);

        startAdvertising();
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

                    relay_table.values().removeAll(Collections.singleton(endpointId));

                    if(!StudentActivity.this.stopReceived){
                        startDiscovery();
                    }
                }
            };

    private void send_INIT_Message(String endpointId) {
        try {
            JSONObject init_data = new JSONObject();

            init_data.put("msg_type" , "INIT");
            init_data.put("prof"     , StudentActivity.this.prof);
            init_data.put("subject"  , StudentActivity.this.subject);
            init_data.put("time_slot", StudentActivity.this.time_slot);
            init_data.put("whoami"   , endpointId);
            init_data.put("lec_no"   , StudentActivity.this.lec_no);

            Payload bytesPayload = Payload.fromBytes( init_data.toString().getBytes() );
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

                Log.i("INFO", "Message From Mesh : " + msg);
            } catch (Exception e) {
                Log.e("FAIL", "MAJOR ERROR", e);
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
        try{
            StudentActivity.this.attendance_status = data.getString("status");
            StudentActivity.this.present_for = data.getInt("present_for");

            StudentActivity.this.isMarked = true;

            if(data.has("authCode")){
                StudentActivity.this.authCode = data.getString("authCode");
            }

        }catch(Exception ex){
            StudentActivity.this.attendance_status = "ERROR";

            Log.e("FAIL", "ERROR WHILE READING ACK", ex);
        }
    }

    private void process_STOP_Message(JSONObject data){
        StudentActivity.this.stopReceived = true;

        ArrayList<Task<Void>> all_msgs = new ArrayList<>();
        byte[] bytes_payload = data.toString().getBytes();

        for(String endpointID : StudentActivity.this.destEndpoints){
            Payload payload = Payload.fromBytes(bytes_payload);

            Task<Void> msg = StudentActivity.this.mConnectionsClient.sendPayload(endpointID, payload);

            all_msgs.add(msg);
        }

        Tasks.whenAll(all_msgs).addOnSuccessListener(aVoid -> {
            StudentActivity.this.mConnectionsClient.stopAllEndpoints();
            StudentActivity.this.afterMarked();
        });
    }

    private void process_INIT_Message(JSONObject data){
        try{
            StudentActivity.this.self_endpointId = data.getString("whoami");
            StudentActivity.this.prof            = data.getString("prof");
            StudentActivity.this.subject         = data.getString("subject");
            StudentActivity.this.time_slot       = data.getString("time_slot");
            StudentActivity.this.lec_no          = data.getInt("lec_no");

            if(this.attendance_context.has(this.subject+":"+this.prof)){
                StudentActivity.this.authCode = this.attendance_context.getJSONObject(this.subject+":"+this.prof).getString("authCode");
            }

            updateStatus(StudentActivity.this.subject + " ("+StudentActivity.this.prof+")\n"+StudentActivity.this.time_slot+"\n"+"Lecture "+StudentActivity.this.lec_no);
        }catch(Exception e){
            Log.e("FAIL", "ERROR WHILE PARSING INIT MESSAGE", e);
        }
    }

    private void send_MARK_Message() {
        if(StudentActivity.this.isMarked) return;

        try {
            JSONObject mark = new JSONObject();

            mark.put("msg_type" , "MARK");
            mark.put("uid"      , StudentActivity.this.UID);
            mark.put("source"   , StudentActivity.this.self_endpointId);
            mark.put("isSecure" , StudentActivity.this.isSecure);
            mark.put("authCode" , StudentActivity.this.authCode == null ? "" : StudentActivity.this.authCode);

            Payload bytesPayload = Payload.fromBytes( mark.toString().getBytes() );
            StudentActivity.this.mConnectionsClient.sendPayload(StudentActivity.this.sourceEndpoint, bytesPayload);

        } catch (Exception e) {
            Log.e("FAIL", "EXCEPTION WHILE SENDING INIT MESSAGE", e);
        }
    }

    private void afterMarked(){
        StudentActivity.this.updateStatus(StudentActivity.this.attendance_status);
        StudentActivity.this.mSpinner.setVisibility(View.INVISIBLE);

        if(StudentActivity.this.attendance_status.equals("MARKED")){
            StudentActivity.this.check_image.setVisibility(View.VISIBLE);
            drawable_check.start();
        }else{
            StudentActivity.this.exclm_image.setVisibility(View.VISIBLE);
            drawable_exclm.start();
        }

        stopAdvertising();
        stopDiscovery();

        try {
            SimpleDateFormat formatter = new SimpleDateFormat("EEE, MMM d");

            StudentActivity.this.attendance_context.put("last_updated", formatter.format(new Date()));

            JSONObject attendance_data = new JSONObject();
            attendance_data.put("present_for", StudentActivity.this.present_for);
            attendance_data.put("total_lec", StudentActivity.this.lec_no);
            attendance_data.put("authCode", StudentActivity.this.authCode);

            StudentActivity.this.attendance_context.put(StudentActivity.this.subject+":"+StudentActivity.this.prof, attendance_data);
        } catch (JSONException e) {
            Log.e("FAIL", "MAJOR ERROR WHILE POPULATING JSON AFTER MARKED", e);
        }

        renderAttendance();

        store();
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

                    if(!StudentActivity.this.stopReceived){
                        updateStatus("Waiting for Connection");
                        stopDiscovery();
                        startAdvertising();
                    }
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


    private void setStudentAttendance(){
        try {
            String data = load();

            StudentActivity.this.attendance_context = new JSONObject(data);

            if(!StudentActivity.this.attendance_context.has("last_updated")){
                StudentActivity.this.attendance_context.put("last_updated", "");
            }

        }catch (Exception e){
            Log.e("FAIL", "ERROR WHILE PARSING JSON OBJECT", e);

            try {
                StudentActivity.this.attendance_context = new JSONObject();
                StudentActivity.this.attendance_context.put("last_updated", "");
            } catch (JSONException ex) {
                Log.e("FAIL", "ERROR WHILE CREATING BLANK JSON OBJECT", ex);
            }
        }

        renderAttendance();
    }

    private void renderAttendance(){
        try{

            ((TextView)findViewById(R.id.last_updated))
            .setText(StudentActivity.this.attendance_context.getString("last_updated"));

            StudentActivity.this.studentAttendanceList.removeAllViews();

            for (Iterator<String> it = StudentActivity.this.attendance_context.keys(); it.hasNext(); ) {
                String key = it.next();

                if(key.equals("last_updated")) continue;

                String subject = key.substring(0, key.indexOf(':'));
                String prof    = key.substring(key.indexOf(':')+1);

                JSONObject attendance_data = this.attendance_context.getJSONObject(key);

                addAttendance(subject + " ("+ prof +")",
                        attendance_data.getInt("present_for"),
                        attendance_data.getInt("total_lec")
                );
            }

        }catch(Exception e){
            Log.e("FAIL", "ERROR WHILE RENDERING ATTENDANCE", e);
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

    public String load()
    {
        String data = "";
        try{
            Context context = getApplicationContext();

            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    "keystore-alias",
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build();

            String mainKey = MasterKeys.getOrCreate(spec);

            String fileToRead = "app_data.bin";
            EncryptedFile encryptedFile = new EncryptedFile.Builder(new File(Environment.getExternalStorageDirectory() + "/Attendance", fileToRead),
                    context,
                    mainKey,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build();

            InputStream inputStream = encryptedFile.openFileInput();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            int nextByte = inputStream.read();
            while (nextByte != -1) {
                byteArrayOutputStream.write(nextByte);
                nextByte = inputStream.read();
            }

            byte[] plaintext = byteArrayOutputStream.toByteArray();

            data = new String(plaintext);
        }catch(Exception ex){
            Log.e("FAIL", "ERROR WHILE FILE INPUT", ex);
        }

        return data;
    }

    public void store() {
        try{
            Context context = getApplicationContext();

            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    "keystore-alias",
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build();

            String mainKey = MasterKeys.getOrCreate(spec);

            String fileToWrite = "app_data.bin";

            File root = new File(Environment.getExternalStorageDirectory(), "Attendance");

            if (!root.exists())
            {
                root.mkdirs();
            }

            File f = new File(Environment.getExternalStorageDirectory() + "/Attendance", fileToWrite);

            if(f.exists()){
                f.delete();
            }

            EncryptedFile encryptedFile = new EncryptedFile.Builder(
                    f,
                    context,
                    mainKey,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build();

            byte[] fileContent = StudentActivity.this.attendance_context.toString().getBytes(StandardCharsets.UTF_8);
            OutputStream outputStream = encryptedFile.openFileOutput();
            outputStream.write(fileContent);
            outputStream.flush();
            outputStream.close();
        }catch(Exception ex){
            Log.e("FAIL", "MAJOR ERROR WHILE STORING APP_DATA", ex);
        }
    }
}

