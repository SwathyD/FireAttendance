package com.example.fireadmission;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;


public class CustomView extends ConstraintLayout {

    // state can be new, warning, error, normal
    public CustomView(Context context, String label1, String state) {
        super(context);

        inflate(context, R.layout.customview, this);
        TextView tv = findViewById(R.id.label);
        tv.setText(label1);

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
        findViewById(R.id.delete).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                listener.execute(CustomView.this);
            }
        });
    }
}
interface Executor{
    void execute(View v);
}