package com.recognizetext;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.recognizetext.FaceDetectionCode.FaceActivity;
import com.recognizetext.Helper.TextGraphic;
import com.recognizetext.TextRecognizeCode.TextActivity;

public class MainActivity extends AppCompatActivity {
    private Button buttonOne,buttonTwo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        buttonOne = findViewById(R.id.buttonOne);
        buttonTwo = findViewById(R.id.buttonTwo);

        buttonOne.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(MainActivity.this, "Recognize Text", Toast.LENGTH_SHORT).show();
                Intent intent=new Intent(MainActivity.this, TextActivity.class);
                startActivity(intent);
            }
        });

        buttonTwo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(MainActivity.this, "Face detection", Toast.LENGTH_SHORT).show();
                Intent intent=new Intent(MainActivity.this, FaceActivity.class);
                startActivity(intent);
            }
        });
    }
}
