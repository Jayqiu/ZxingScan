# ZxingScan
使用Zxing实现二维码条形码的扫描和解析

	![](https://github.com/Jayqiu/ZxingScan/blob/master/GIF.gif)
# 说明
项目没有对权限申请做处理的，下载直接运行的需要手动的开启权限
### 使用

extends CaptureActivity
```java
/**
  *	扫描解析
  * @param resultString
  * @param barcode
  * @param scaleFactor
  */
    @Override
    public void onHandleDecode(String resultString, Bitmap barcode, float scaleFactor) {
        Toast.makeText(this, resultString, Toast.LENGTH_SHORT).show();

    }
    /**
	 *相册选择图片解析
	 * @param resultString
	 * @param status 200 成功 300 失败
	 */
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
```
### 自定义 title
```java
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
                 // 相册选择图片
                startQRCodeForAlbum();
            }
        });
```
### 设置扫描框和文字颜色
```java
  @Override
  protected void onResume() {
        super.onResume();
        setTextColor(R.color.colorPrimary);
  }
```
