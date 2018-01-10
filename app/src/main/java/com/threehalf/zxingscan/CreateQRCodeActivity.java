package com.threehalf.zxingscan;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;

import com.threehalf.scanner.CaptureActivity;
import com.threehalf.scanner.QRCodeFactory;

import java.io.InputStream;

/**
 * Created by jayqiu
 * on 2018/1/9.
 */

public class CreateQRCodeActivity extends Activity {
    private ImageView imageView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create);
        imageView = findViewById(R.id.iv_code);
        String ret = getIntent().getStringExtra("ret");
//        Bitmap bitmap =
        Bitmap bmp= BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);

//        Bitmap bitmap = QRCodeFactory.createQRCode(ret, 200,200);
        Bitmap bitmap = QRCodeFactory.createQRImage(ret, 200,bmp);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        }

    }
}
