package com.example.fireadmission;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKeys;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
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
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
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
import java.util.jar.JarOutputStream;

class Status{
    String status;
    String reason;
    boolean provideAuthCode;
}

public class TeacherActivity extends AppCompatActivity {

    private int maxConnections = 1;

    LinearLayout list;
    private ConnectionsClient mConnectionsClient;
    private ArrayList<String> destEndpoints = new ArrayList<>();
    private String subject = null;
    private String time_slot = null;
    private int lec_no = 0;
    private String prof = null;
    private HashMap<String, String> relay_table = new HashMap<>();

    // stores attendance count of all student as uid:{authCode, int} mapping and also stores subject_lec_no:int
    // if key doesn't exist, assume 0 and continue.
    private JSONObject attendance_context = null;
    private JSONObject attendance_context_subject = null;

    ArrayList<StudentRecord> student = new ArrayList<>();
    Executor listener = new Executor() {
        @Override
        public void execute(View v) {
            //do nothing
        }

        @Override
        public void execute(View v, String uid) {
            TeacherActivity.this.list.removeView(v);

            for(int i=0; i<student.size(); i++){
                if(student.get(i).uid.equals(uid)){
                    student.remove(i);
                }
            }

            try{
                JSONObject stud_data = TeacherActivity.this.attendance_context_subject.getJSONObject(uid);
                stud_data.put("present_for", stud_data.getInt("present_for") - 1);
                TeacherActivity.this.attendance_context_subject.put(uid, stud_data);

                send_ACK_Message(uid, "UNMARKED", false);
            }catch (Exception e){
                Log.e("FAIL", "ERROR WHILE RETRIEVING stud_data for UNMARKING", e);
            }
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


    public void addStudent(String text, String status, String reason) {
        CustomView v1 = new CustomView(this, text, status);
        v1.setOnDeleteListener(listener);
        v1.setOnClickListener(new Executor() {
            @Override
            public void execute(View v) {
                Toast.makeText(TeacherActivity.this, reason, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void execute(View v, String uid) {
                //do nothing
            }
        });
        list.addView(v1);
        final Handler handlerUI = new Handler(Looper.getMainLooper());
        Runnable r = () -> ((ScrollView) findViewById(R.id.scrollView)).fullScroll(View.FOCUS_DOWN);
        handlerUI.post(r);
        student.add(new StudentRecord(text, reason));
    }

    public void manuallyAddStudent(View v) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("Add");
        alert.setMessage("Enter the UID of the student");

        final EditText input = new EditText(this);
        alert.setView(input);
        alert.setPositiveButton("Ok", (dialog, which) -> {
            String uid = input.getText().toString();

            Status status = getStatusOfUID(uid, true, "", "");

            addStudent(uid, status.status, status.reason);

            send_ACK_Message(uid, "MARKED", status.provideAuthCode);
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
        EditText lec_no = findViewById(R.id.lec_no);

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

            TeacherActivity.this.subject   = subject.getText().toString();
            TeacherActivity.this.time_slot = start.getText().toString() + " - " + end.getText().toString();
            TeacherActivity.this.lec_no    = Integer.parseInt(lec_no.getText().toString());

            ((TextView) findViewById(R.id.textView6))
            .setText(TeacherActivity.this.subject + ", Lecture " + TeacherActivity.this.lec_no);

            ((TextView) findViewById(R.id.textView7))
            .setText(TeacherActivity.this.time_slot);

            TeacherActivity.this.list = findViewById(R.id.list);

            String data = load();

            try {
                TeacherActivity.this.attendance_context = new JSONObject(data);

                if(TeacherActivity.this.attendance_context.has(this.subject)){
                    this.attendance_context_subject = this.attendance_context.getJSONObject(this.subject);
                }else{
                    this.attendance_context_subject = new JSONObject();
                }

                this.attendance_context_subject.put("total_lec", this.lec_no);

            } catch (JSONException e) {
                Log.e("FAIL", "ERROR, JSON OBJECT PARSE ERROR", e);

                try {
                    TeacherActivity.this.attendance_context = new JSONObject();
                    this.attendance_context_subject = new JSONObject();
                    this.attendance_context_subject.put("total_lec", this.lec_no);
                } catch (JSONException jsonException) {
                    Log.e("FAIL", "MAJOR ERROR, WHILE CREATING BLANK ATTENDANCE CONTEXT", e);
                }
            }

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
                            TeacherActivity.this.process_MARK_Message(data, endpointId);
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

    private void send_ACK_Message(String destUid, String status, boolean provideAuthCode) {
        try{
            JSONObject response = new JSONObject();
            response.put("msg_type", "ACK");
            response.put("status"  , status);
            response.put("destUid" , destUid);
            response.put("present_for" , TeacherActivity.this.attendance_context_subject.getJSONObject(destUid).getString("present_for"));

            if(provideAuthCode){
                response.put("authCode", TeacherActivity.this.attendance_context_subject.getJSONObject(destUid).getString("authCode"));
            }

            if(TeacherActivity.this.relay_table.containsKey(destUid)){
                Payload bytesPayload = Payload.fromBytes( response.toString().getBytes() );
                TeacherActivity.this.mConnectionsClient.sendPayload(TeacherActivity.this.relay_table.get(destUid), bytesPayload);
            }

        } catch (Exception e) {
            Log.e("FAIL", "ERROR WHEN SENDING ACK", e);
        }
    }

    private void process_MARK_Message(JSONObject data, String fromEndpointId) {

        try{
            TeacherActivity.this.relay_table.put(data.getString("uid"), fromEndpointId);

            String uid = data.getString("uid");

            Status status = TeacherActivity.this.getStatusOfUID(uid, false, data.getString("isSecure"), data.getString("authCode"));

            TeacherActivity.this.addStudent(uid, status.status, status.reason);

            send_ACK_Message(uid, "MARKED", status.provideAuthCode);
        }catch(Exception e){
            Log.e("FAIL", "ERROR WHEN PROCESSING MARK", e);
        }
    }

    private Status getStatusOfUID(String uid, boolean isManual, String isSecure, String authCode) {
        Status status = new Status();
        status.status = "normal";
        status.reason = uid + " is Present.";
        status.provideAuthCode = false;

        try {

            if(!TeacherActivity.this.attendance_context_subject.has(uid)){
                status.status = "new";
                status.reason = uid + " is Newly Enrolled";
                status.provideAuthCode = true;

                JSONObject stud_data = new JSONObject();

                stud_data.put("authCode", getAlphaNumericString(10));
                stud_data.put("present_for", 0);


                TeacherActivity.this.attendance_context_subject.put(uid, stud_data);

            }else if(!isManual){

                if(isSecure.equals("no")){
                    status.status = "warning";
                    status.reason = uid +" did not Provide Fingerprint/Lock Screen Password for authentication.";
                }

                if(authCode.equals("")){
                    status.status = "warning";
                    status.reason = uid + " Deleted the Application Data.";
                    status.provideAuthCode = true;
                }

                try {
                    if(!authCode.equals("") && !authCode.equals(
                            TeacherActivity.this.attendance_context_subject.getJSONObject(uid)
                            .getString("authCode")
                    )){

                        status.status = "error";
                        status.reason = uid + " used a different Phone from the last time.";
                    }
                } catch (JSONException e) {
                    Log.e("FAIL", "MAJOR ERROR, KEY NOT PRESENT IN JSON OBJECT", e);
                }

            }

            JSONObject stud_data = TeacherActivity.this.attendance_context_subject.getJSONObject(uid);
            stud_data.put("present_for", stud_data.getInt("present_for") + 1);

            TeacherActivity.this.attendance_context_subject.put(uid, stud_data);

        } catch (JSONException e) {
            Log.e("FAIL", "MAJOR ERROR WHILE CREATING STATUS & UPDATING ATTENDANCE", e);

            status.status = "error";
            status.reason = "MAJOR ERROR";
        }

        return status;
    }

    private ConnectionLifecycleCallback discoverConnectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
                    Log.i("INFO", "DISCOVER CONNECTION INITIATED");

                    if(TeacherActivity.this.destEndpoints.size() < TeacherActivity.this.maxConnections){
                        Nearby.getConnectionsClient(TeacherActivity.this).acceptConnection(endpointId, new TeacherActivity.DiscoverReceiveBytesPayloadListener());
                    }
                }

                @Override
                public void onConnectionResult(String endpointId, ConnectionResolution result) {

                    switch (result.getStatus().getStatusCode()) {
                        case ConnectionsStatusCodes.STATUS_OK:
                            TeacherActivity.this.destEndpoints.add( endpointId );

                            Log.i("INFO", "DISCOVER CONNECTION RESULT OK");
                            Toast.makeText(TeacherActivity.this, "DISCOVER CONNECTION RESULT OK", Toast.LENGTH_SHORT).show();

                            if(TeacherActivity.this.destEndpoints.size() == TeacherActivity.this.maxConnections) {
                                stopDiscovery();
                            }

                            TeacherActivity.this.send_INIT_Message(endpointId);

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

                    TeacherActivity.this.destEndpoints.remove(endpointId);

                    relay_table.values().removeAll(Collections.singleton(endpointId));

                    startDiscovery();
                }
            };

    private void send_INIT_Message(String endpointID) {
        try {
            JSONObject init_data = new JSONObject();

            init_data.put("msg_type" , "INIT");
            init_data.put("prof"     , TeacherActivity.this.prof.substring(0, TeacherActivity.this.prof.indexOf('@')));
            init_data.put("subject"  , TeacherActivity.this.subject);
            init_data.put("time_slot", TeacherActivity.this.time_slot);
            init_data.put("whoami"   , endpointID);
            init_data.put("lec_no"   , TeacherActivity.this.lec_no);

            Payload bytesPayload = Payload.fromBytes( init_data.toString().getBytes() );
            TeacherActivity.this.mConnectionsClient.sendPayload( endpointID , bytesPayload);

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

        try {
            TeacherActivity.this.attendance_context.put(TeacherActivity.this.subject, TeacherActivity.this.attendance_context_subject);
        } catch (JSONException e) {
            Log.e("FAIL", "ERROR WHILE PLACING SUBJECT INTO ATTENDANCE CONTEXT", e);
        }

        store();

        createFile();
    }

    private void send_STOP_Message() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("msg_type", "STOP");

            byte[] payload_bytes = obj.toString().getBytes();

            Payload bytesPayload = Payload.fromBytes( payload_bytes );

            ArrayList<Task<Void>> all_msgs = new ArrayList<>();

            for(String endpointID : TeacherActivity.this.destEndpoints){
                Task<Void> msg = TeacherActivity.this.mConnectionsClient.sendPayload(endpointID, bytesPayload);

                all_msgs.add(msg);
            }

            Tasks.whenAll(all_msgs).addOnSuccessListener(aVoid -> TeacherActivity.this.mConnectionsClient.stopAllEndpoints());

        }catch(Exception e){
            Log.e("FAIL", "FAILED WHILE SENDING STOP MESSAGE", e);
        }
    }

    static String getAlphaNumericString(int n) {
        // chose a Character random from this String
        String AlphaNumericString =   "abcdefghijklmnopqrstuvwxyz"
                                    + "0123456789"
                                    + "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

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

            if (!root.exists())
            {
                root.mkdirs();
            }
            File file = new File(root, fileName);

            FileWriter writer = new FileWriter(file, true);
            writer.append("Subject: " + subject + "\nTime: " + TeacherActivity.this.time_slot + "\nStudent List: \n");
            for (int i = 0; i < student.size(); i++) {
                writer.append(student.get(i).uid + "\n");
            }
            writer.append("\n\n\n");
            writer.flush();
            writer.close();
            Toast.makeText(this, "Data has been written to Attendance/" + fileName, Toast.LENGTH_SHORT).show();
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setTitle("Backup");
            alert.setMessage("Do you want to proceed sending the file through email/chat?");
            alert.setPositiveButton("Yes", (dialog, which) ->{
                sendEmail(fileName);
            });

            alert.setNegativeButton("No", (dialog, which) -> {
                redirectToHomePage();
            });

            alert.show();

        } catch (IOException e) {
            e.printStackTrace();

        }
    }

    private void redirectToHomePage() {
        Intent intent = new Intent(getBaseContext(), TeacherActivity.class);
        intent.putExtra("key", this.prof);
        finish();
        startActivity(intent);
    }

    public void sendEmail(String fileName) {
        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.setType("text/plain");
        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{TeacherActivity.this.prof});
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Attendance report for " + subject + " lecture " + TeacherActivity.this.lec_no);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd");
        emailIntent.putExtra(Intent.EXTRA_TEXT, "Attendance Report: \nDate: " + formatter.format(new Date()) + "\nTime: " + TeacherActivity.this.time_slot);
        File root = Environment.getExternalStorageDirectory();
        String pathToMyAttachedFile = "Attendance/" + fileName;
        File file = new File(root, pathToMyAttachedFile);
        if (!file.exists() || !file.canRead()) {
            Toast.makeText(this, "Could not attach the file " + fileName, Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = FileProvider.getUriForFile(TeacherActivity.this, BuildConfig.APPLICATION_ID + ".provider", file);
        emailIntent.putExtra(Intent.EXTRA_STREAM, uri);
        emailIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(Intent.createChooser(emailIntent, "Share Attendance Records via"), 10);
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
            Log.e("FAIL", "ERROR WHILE LOADING APP_DATA", ex);
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

            byte[] fileContent = TeacherActivity.this.attendance_context.toString().getBytes(StandardCharsets.UTF_8);
            OutputStream outputStream = encryptedFile.openFileOutput();
            outputStream.write(fileContent);
            outputStream.flush();
            outputStream.close();
        }catch(Exception ex){
            Log.e("FAIL", "MAJOR ERROR WHILE STORING APP_DATA", ex);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        //email waala flow khatam hua
        if(requestCode == 10){
            redirectToHomePage();
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