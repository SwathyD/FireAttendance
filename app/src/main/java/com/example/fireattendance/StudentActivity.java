package com.example.fireattendance;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.biometric.BiometricPrompt;
import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.BatteryManager;
import android.os.Build;
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

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.Executor;

public class StudentActivity extends AppCompatActivity {

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
    private String destEndpoint   = null;

    JSONObject attendanceData = null;
    LinearLayout studentAttendanceList;

    // TEMP VAR
    BluetoothAdapter mBluetoothAdapter;
    String btName = "";
    boolean hasAttendanceStopped    = false;
    boolean hasDiscoveryStopped     = true;
    private final BroadcastReceiver mBroadcastReceiver1 = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch(state) {
                    case BluetoothAdapter.STATE_OFF:
                        Log.i("INFO", "BLUETOOTH OFF");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Log.i("INFO", "BLUETOOTH TURNING OFF");
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.i("INFO", "BLUETOOTH ON");
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        Log.i("INFO", "BLUETOOTH TURNING ON");
                        break;
                }
            }

            if(action.equals(BluetoothAdapter.ACTION_DISCOVERY_STARTED)){
                Log.i("INFO", "STARTING DISCOVERY.... TRUEE");
            }

            if(action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)){
                Log.i("INFO", "FINISHED DISCOVERY.... TRUEE");

                if(hasDiscoveryStopped && !hasAttendanceStopped){
                    mBluetoothAdapter.startDiscovery();
                }
            }

            if(action.equals(BluetoothDevice.ACTION_FOUND)){
                BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                short  rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, (short) 0);

                if(device.getName() != null){
                    Log.i("INFO", device.getName() + " - " + rssi);
                    send_SNIFF_Message(device.getName(), rssi);
                }

            }
        }
    };

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

        // TEMP CODE STARTS
        mBluetoothAdapter   = BluetoothAdapter.getDefaultAdapter();
        btName              = mBluetoothAdapter.getName();

        IntentFilter filter1 = new IntentFilter();
        filter1.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter1.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter1.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter1.addAction(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mBroadcastReceiver1, filter1);
        // TEMP CODE ENDS

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

                // TEMP CODE STARTS
//                mBluetoothAdapter.enable();

//                Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
//                discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
//                startActivity(discoverableIntent);

                Log.i("INFO", "STARTED BLUETOOTH");
                // TEMP CODE ENDS

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
                            StudentActivity.this.process_MARK_Message(endpointId, data.getString("source"));
                            StudentActivity.this.relay_MARK_Message(receivedBytes);
                        break;

                    case "SNIFF":
                        StudentActivity.this.relay_SNIFF_Message(receivedBytes);
                        break;

                    default:
                        Log.e("INFO", "RECEIVED UNEXPECTED MESSAGE ON RELAY" + data.getString("msg_type"));
                }

            }catch(Exception e){
                Log.e("INFO", "ERROR WHILE RELAYING DATA", e);
            }
        }

        @Override
        public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
            // Bytes payloads are sent as a single chunk, so you'll receive a SUCCESS update immediately
            // after the call to onPayloadReceived().
        }
    }

    private void process_MARK_Message(String from, String dest){
        // StudentActivity.this.relay_table.put(dest, from);
        // future mein jab multiple connections ho sakenge tab ye implement karna hoga
    }

    private void relay_MARK_Message(byte[] data){
        Payload bytesPayload = Payload.fromBytes( data );
        StudentActivity.this.mConnectionsClient.sendPayload(StudentActivity.this.sourceEndpoint, bytesPayload);
    }

    private void relay_SNIFF_Message(byte[] data){
        Payload bytesPayload = Payload.fromBytes( data );
        StudentActivity.this.mConnectionsClient.sendPayload(StudentActivity.this.sourceEndpoint, bytesPayload);
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

                            StudentActivity.this.send_INIT_Message();

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
                    Log.e("INFO", "DISCOVER DISCONNECTED FROM ENDPOINT " + endpointId);

                    StudentActivity.this.destEndpoint = null;
                }
            };

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(mBroadcastReceiver1);
    }

    private void send_INIT_Message() {
        try {
            JSONObject attendance_context = new JSONObject();

            attendance_context.put("msg_type" , "INIT");
            attendance_context.put("prof"     , StudentActivity.this.prof);
            attendance_context.put("subject"  , StudentActivity.this.subject);
            attendance_context.put("time_slot", StudentActivity.this.time_slot);
            attendance_context.put("whoami"   , StudentActivity.this.destEndpoint);

            Payload bytesPayload = Payload.fromBytes( attendance_context.toString().getBytes() );
            StudentActivity.this.mConnectionsClient.sendPayload(StudentActivity.this.destEndpoint, bytesPayload);

        } catch (Exception e) {
            Log.e("INFO", "EXCEPTION WHILE SENDING INIT MESSAGE", e);
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
                                            Log.e("INFO", "FAILED CONNECTION REQUEST", e);
                                        });
                    }

                    @Override
                    public void onEndpointLost(String endpointId) {
                        Log.i("INFO", "ENDPOINT LOST");
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
                            Log.e("INFO", "FAILED TO DISCOVER", e);
                        });
    }

    protected void stopDiscovery() {
        hasDiscoveryStopped = true;
        mConnectionsClient.stopDiscovery();
        Log.i("INFO", "STOPPED NearbyConnections DISCOVERY");
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
                            if(data.getString("dest").equals(StudentActivity.this.self_endpointId)){
                                StudentActivity.this.process_ACK_Message(data);
                            }else{
                                StudentActivity.this.relay_ACK_Message(data.getString("dest"), receivedBytes);
                            }
                        break;

                    default:
                        Log.e("INFO", "UNEXPECTED MESSAGE WHILE RECEIVING DATA FROM MESH" + data.getString("msg_type"));
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

    private void relay_ACK_Message(String dest, byte[] data) {
        // String next_hop = StudentActivity.this.relay_table.get(dest);
        // jab future mein multiple connections hoyenge tab next_hop identify karna padega, abhi since ek hi hai isliye directly usko forward kar sakte hai

        Payload bytesPayload = Payload.fromBytes( data );
        StudentActivity.this.mConnectionsClient.sendPayload(StudentActivity.this.destEndpoint, bytesPayload);
    }

    private void process_ACK_Message(JSONObject data) {
        // data store karna hai jo user ko dikhega when STOP trigger hoga
        try{
            StudentActivity.this.attendance_status = data.getString("status");
            updateStudentAttendance("SE","RSM",2);
        }catch(Exception ex){
            StudentActivity.this.attendance_status = "ERROR";

            Log.e("INFO", "ERROR WHILE READING ACK", ex);
        }
    }

    private void process_STOP_Message(JSONObject data){
        hasAttendanceStopped = true;
        mBluetoothAdapter.cancelDiscovery();
        if(StudentActivity.this.destEndpoint != null){
            Payload bytesPayload = Payload.fromBytes( data.toString().getBytes() );
            StudentActivity.this.mConnectionsClient.sendPayload(StudentActivity.this.destEndpoint, bytesPayload).addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    StudentActivity.this.mConnectionsClient.stopAllEndpoints();
                    StudentActivity.this.afterMarked();
                }
            });
        }else{
            StudentActivity.this.mConnectionsClient.stopAllEndpoints();
            StudentActivity.this.afterMarked();
        }
    }

    private void process_INIT_Message(JSONObject data){
        try{
            StudentActivity.this.self_endpointId = data.getString("whoami");
            StudentActivity.this.prof            = data.getString("prof");
            StudentActivity.this.subject         = data.getString("subject");
            StudentActivity.this.time_slot       = data.getString("time_slot");

            updateStatus(StudentActivity.this.subject + " ("+StudentActivity.this.prof+")\n\n"+StudentActivity.this.time_slot);
        }catch(Exception e){
            Log.e("INFO", "ERROR WHILE PARSING INIT MESSAGE", e);
        }
    }

    private void send_SNIFF_Message(String recordee, short rssi){
        try {
            Log.i("INFO", "PREPARING SNIFF MESSAGE FOR " + recordee);

            JSONObject sniff = new JSONObject();

            sniff.put("msg_type" , "SNIFF");
            sniff.put("src"  , this.btName );
            sniff.put("bt_name"  , recordee);
            sniff.put("rssi"     , rssi);

            Payload bytesPayload = Payload.fromBytes( sniff.toString().getBytes() );
            StudentActivity.this.mConnectionsClient.sendPayload(StudentActivity.this.sourceEndpoint, bytesPayload);

            Log.i("INFO", "SENDING SNIFF MESSAGE FOR " + recordee);
        } catch (Exception e) {
            Log.e("INFO", "EXCEPTION WHILE SENDING SNIFF MESSAGE", e);
        }
    }

    private void afterMarked(){
        StudentActivity.this.updateStatus(StudentActivity.this.attendance_status);
        StudentActivity.this.mSpinner.setVisibility(View.INVISIBLE);
        StudentActivity.this.check_image.setVisibility(View.VISIBLE);

        GifDrawable drawable_check = (GifDrawable) ((GifImageView)findViewById(R.id.check_mark)).getDrawable();
        drawable_check.start();

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

                            mBluetoothAdapter.startDiscovery();
                            //startDiscovery();

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
                    Log.e("INFO", "ADVERT DISCONNECTED FROM ENDPOINT " + endpointId);

                    StudentActivity.this.sourceEndpoint = null;
                }
            };

    private void updateStatus(String s) {
        StudentActivity.this.mStatusText.setText(s);
    }

    private void send_MARK_Message() {
        try {
            JSONObject mark = new JSONObject();
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = StudentActivity.this.registerReceiver(null, ifilter);
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

            float batteryPct = level * 100 / (float)scale;
            mark.put("msg_type" , "MARK");
            mark.put("uid"      , StudentActivity.this.UID);
            mark.put("source"   , StudentActivity.this.self_endpointId);
            mark.put("bt_name"  , StudentActivity.this.btName);
            //Put Required Details here
            //Battery and percent , Manufacturer name of sender
            mark.put("battery_mah",level );
            mark.put("battery_percent", batteryPct );
            mark.put("manufacturer_name", Build.MANUFACTURER);

            Log.i("INFO BATTERY", level + "" );
            Payload bytesPayload = Payload.fromBytes( mark.toString().getBytes() );
            StudentActivity.this.mConnectionsClient.sendPayload(StudentActivity.this.sourceEndpoint, bytesPayload);

        } catch (Exception e) {
            Log.e("INFO", "EXCEPTION WHILE SENDING INIT MESSAGE", e);
        }
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
                    Log.e("INFO", "FAILED TO START ADVERTISING", e);
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
class StBleObs {
    String recorder, recordee;
    int rssi;

    public StBleObs(String recorder, String recordee, int rssi) {
        this.recorder = recorder;
        this.recordee = recordee;
        this.rssi = rssi;
    }
}

