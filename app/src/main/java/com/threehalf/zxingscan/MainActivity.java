package com.threehalf.zxingscan;

import android.graphics.Bitmap;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.threehalf.scanner.CaptureActivity;

public class MainActivity extends CaptureActivity {
    private TextView tvBack;
    private TextView tvRight;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View mView = getLayoutInflater().inflate(R.layout.title_bar_view, null);
        setTitleView(mView);
        tvBack = mView.findViewById(R.id.topbar_back);
        tvRight = mView.findViewById(R.id.topbar_right);
        tvBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        tvRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startQRCodeForAlbum();
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        setTextColor(R.color.colorPrimary);
    }

    @Override
    public void onHandleDecode(String resultString, Bitmap barcode, float scaleFactor) {
        Toast.makeText(this, resultString, Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onAlbumDecode(String resultString, int status) {
        switch (status) {
            case PARSE_BARCODE_SUC: // 解析图片成功

                Toast.makeText(this, "解析成功，结果为：" + resultString, Toast.LENGTH_SHORT).show();
                break;

            case PARSE_BARCODE_FAIL:// 解析图片失败
                Toast.makeText(this, "解析图片失败", Toast.LENGTH_SHORT).show();

                break;

            default:
                break;
        }

    }
}
