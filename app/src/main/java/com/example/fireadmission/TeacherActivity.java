package com.example.fireadmission;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
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

public class TeacherActivity extends AppCompatActivity {

    LinearLayout list;
    private ConnectionsClient mConnectionsClient;
    private String destEndpoint = null;
    private String subject = null;
    private String time_slot = null;
    private String prof = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher);
        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();

        prof = getIntent().getStringExtra("key");

        mConnectionsClient = Nearby.getConnectionsClient(this);
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
        alert.setPositiveButton("Ok", (dialog, which) -> {
            String value = input.getText().toString();
            addStudent(value);
        });

        alert.setNegativeButton("Cancel", (dialog, which) -> {
            //Cancel
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

            TeacherActivity.this.subject = subject.getText().toString();
            TeacherActivity.this.time_slot = start.getText().toString() + " - " + end.getText().toString();

            ((TextView)findViewById(R.id.textView6))
            .setText( TeacherActivity.this.subject );

            ((TextView)findViewById(R.id.textView7))
            .setText( TeacherActivity.this.time_slot );

            TeacherActivity.this.list = findViewById(R.id.customList);

            startDiscovery();
        }

    }

    private class DiscoverReceiveBytesPayloadListener extends PayloadCallback {

        @Override
        public void onPayloadReceived(String endpointId, Payload payload) {
            try{
                byte[] receivedBytes = payload.asBytes();
                String msg = new String(receivedBytes);
                JSONObject data = new JSONObject(msg);

                switch(data.getString("msg_type")){
                    case "MARK":
                            TeacherActivity.this.process_MARK_Message(data);
                        break;

                    default:
                        Log.e("FAIL", "UNEXPECTED MESSAGE TYPE RECEIVED "+ data.getString("msg_type"));
                }

            }catch(Exception e){
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
        try{
            JSONObject response = new JSONObject();
            response.put("msg_type", "ACK");
            response.put("status"  , status);
            response.put("dest"    , endpointId);

            Payload bytesPayload = Payload.fromBytes( response.toString().getBytes() );
            TeacherActivity.this.mConnectionsClient.sendPayload(TeacherActivity.this.destEndpoint, bytesPayload);

        }catch(Exception e){
            Log.e("FAIL", "ERROR WHEN SENDING ACK", e);
        }
    }

    private void process_MARK_Message(JSONObject data) {
        // auth code se dekhke authenticate karna hai
        try{
            TeacherActivity.this.addStudent(data.getString("uid"));

            send_ACK_Message(data.getString("source"), "MARKED:"+getAlphaNumericString(10));
        }catch(Exception e){
            Log.e("FAIL", "ERROR WHEN PROCESSING MARK", e);
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

            attendance_context.put("msg_type" , "INIT");
            attendance_context.put("prof"     , TeacherActivity.this.prof);
            attendance_context.put("subject"  , TeacherActivity.this.subject);
            attendance_context.put("time_slot", TeacherActivity.this.time_slot);
            attendance_context.put("whoami"   , TeacherActivity.this.destEndpoint);

            Payload bytesPayload = Payload.fromBytes( attendance_context.toString().getBytes() );
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

    public void stopAttendance(View v){
        send_STOP_Message();
    }

    private void send_STOP_Message(){
        try{
            JSONObject obj = new JSONObject();
            obj.put("msg_type", "STOP");

            Payload bytesPayload = Payload.fromBytes( obj.toString().getBytes() );
            TeacherActivity.this.mConnectionsClient.sendPayload(TeacherActivity.this.destEndpoint, bytesPayload).addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    TeacherActivity.this.mConnectionsClient.stopAllEndpoints();
                }
            });
        }catch(Exception e){
            Log.e("FAIL", "FAILED WHILE SENDING STOP MESSAGE", e);
        }
    }

    static String getAlphaNumericString(int n)
    {
        // chose a Character random from this String
        String AlphaNumericString = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                                    + "0123456789"
                                    + "abcdefghijklmnopqrstuvwxyz";

        // create StringBuffer size of AlphaNumericString
        StringBuilder sb = new StringBuilder(n);

        for (int i = 0; i < n; i++) {

            // generate a random number between
            // 0 to AlphaNumericString variable length
            int index  = (int)(AlphaNumericString.length() * Math.random());

            // add Character one by one in end of sb
            sb.append( AlphaNumericString.charAt(index) );
        }

        return sb.toString();
    }
}
