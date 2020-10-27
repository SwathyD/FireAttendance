package com.example.fireadmission;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.ConnectionsClient;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.nio.Buffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Calendar;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import androidx.annotation.Nullable;
import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKeys;

public class MainActivity extends Activity {

    String getAlphaNumericString(int n)
    {
        // chose a Character random from this String
        String AlphaNumericString =   "abcdefghijklmnopqrstuvwxyz"
                                    + "0123456789"
                                    + "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hello);

        try {

            File root = new File(Environment.getExternalStorageDirectory(), "Attendance");

            if (!root.exists())
            {
                root.mkdirs();
            }

            store();
            load();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeAsString(String data, String filePath) throws Exception{

        BufferedWriter bw = new BufferedWriter( new OutputStreamWriter( openFileOutput(filePath, Context.MODE_PRIVATE) ));
        bw.write(data);
        bw.flush();

        bw.close();
    }

    public String readAsString(String filePath) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader( openFileInput(filePath) ));
        StringBuilder sb = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }

        reader.close();
        return sb.toString();
    }

    public void store() throws GeneralSecurityException, IOException {
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

        byte[] fileContent = "hello, world!".getBytes(StandardCharsets.UTF_8);
        OutputStream outputStream = encryptedFile.openFileOutput();
        outputStream.write(fileContent);
        outputStream.flush();
        outputStream.close();
    }

    public void load() throws GeneralSecurityException, IOException
    {
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

        String data = new String(plaintext);

        Log.i("DATA", data);
    }
}