package ru.ra66it.qrcodegeneratorexample;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ImageView imageView = findViewById(R.id.imageView);
        TextView textView = findViewById(R.id.textView);

        QRCodeGenerator qrCodeGenerator = new QRCodeGenerator();
        String content = "Hello World!";
        Bitmap bitmap = qrCodeGenerator.generate(content);

        imageView.setImageBitmap(bitmap);
        textView.setText(content);
    }
}