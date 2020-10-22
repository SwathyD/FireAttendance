package com.example.fireadmission;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.ConnectionsClient;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.Buffer;
import java.util.Random;

import androidx.annotation.Nullable;

public class MainActivity extends Activity {

    String getAlphaNumericString(int n)
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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hello);

        try {

            Log.i("DATA", getAlphaNumericString(10));

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

}