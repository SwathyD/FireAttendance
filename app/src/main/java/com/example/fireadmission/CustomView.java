package com.example.fireadmission;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;


public class CustomView extends ConstraintLayout {

    Context ctxt;

    // state can be new, warning, error, normal
    public CustomView(Context context, String label1, String state) {
        super(context);

        this.ctxt = context;

        inflate(context, R.layout.customview, this);
        TextView tv = findViewById(R.id.label);
        tv.setText(label1);

        ImageView button = findViewById(R.id.delete);
        button.setContentDescription(label1);

        switch(state){
            case "new"     :
                findViewById(R.id.latest).setVisibility(View.VISIBLE);
                tv.setTextColor(Color.parseColor("#FF049804"));
                break;
            case "warning" :
                findViewById(R.id.potential_false_mark).setVisibility(View.VISIBLE);
                tv.setTextColor(Color.parseColor("#FF9800"));
                break;
            case "error"   :
                findViewById(R.id.false_mark).setVisibility(View.VISIBLE);
                tv.setTextColor(Color.parseColor("#F30404"));
                break;
            case "normal"  :
                // do nothing
                break;
        }

        Animation fadeIn = AnimationUtils.loadAnimation(context, R.anim.tween);
        this.startAnimation(fadeIn);
    }

    public void setOnDeleteListener(Executor listener){
        findViewById(R.id.delete).setOnClickListener(view -> {

            String uid = view.getContentDescription().toString();
            AlertDialog.Builder alert = new AlertDialog.Builder(this.ctxt);
            alert.setTitle("Alert!");
            alert.setMessage("Unmark "+uid+" ?");
            alert.setPositiveButton("Yes", (dialog, which) -> listener.execute(CustomView.this, uid));
            alert.setNegativeButton("No", (dialog, which) -> {});

            alert.show();
        });
    }

    public void setOnClickListener(Executor listener){
        findViewById(R.id.label).setOnClickListener(view -> listener.execute(CustomView.this));
    }
}
interface Executor{
    void execute(View v);
    void execute(View v, String uid);
}