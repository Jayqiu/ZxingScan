package com.threehalf.scanner;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
import com.google.zxing.client.result.ResultParser;
import com.threehalf.scanner.camera.CameraManager;
import com.threehalf.scanner.common.BitmapUtils;
import com.threehalf.scanner.decode.BitmapDecoder;
import com.threehalf.scanner.decode.CaptureActivityHandler;
import com.threehalf.scanner.view.ViewfinderView;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Map;



/**
 * 
 * 此Activity所做的事： 1.开启camera，在后台独立线程中完成扫描任务；
 * 2.绘制了一个扫描区（viewfinder）来帮助用户将条码置于其中以准确扫描； 3.扫描成功后会将扫描结果展示在界面上。
 * 
 * @author jayqiu
 */
public abstract class CaptureActivity extends Activity implements SurfaceHolder.Callback, View.OnClickListener {

	private static final String TAG = CaptureActivity.class.getSimpleName();

	private static final int REQUEST_CODE = 100;

	public static final int PARSE_BARCODE_FAIL = 300;
	public static final int PARSE_BARCODE_SUC = 200;
//	private TitleBarView mTitleBarView;

	/**
	 * 是否有预览
	 */
	private boolean hasSurface;

	/**
	 * 活动监控器。如果手机没有连接电源线，那么当相机开启后如果一直处于不被使用状态则该服务会将当前activity关闭。
	 * 活动监控器全程监控扫描活跃状态，与CaptureActivity生命周期相同.每一次扫描过后都会重置该监控，即重新倒计时。
	 */
	private InactivityTimer inactivityTimer;

	/**
	 * 声音震动管理器。如果扫描成功后可以播放一段音频，也可以震动提醒，可以通过配置来决定扫描成功后的行为。
	 */
	private BeepManager beepManager;

	/**
	 * 闪光灯调节器。自动检测环境光线强弱并决定是否开启闪光灯
	 */
	private AmbientLightManager ambientLightManager;

	private CameraManager cameraManager;
	/**
	 * 扫描区域
	 */
	private ViewfinderView viewfinderView;

	private CaptureActivityHandler handler;

	private Result lastResult;

	private boolean isFlashlightOpen;

	/**
	 * 【辅助解码的参数(用作MultiFormatReader的参数)】 编码类型，该参数告诉扫描器采用何种编码方式解码，即EAN-13，QR
	 * Code等等 对应于DecodeHintType.POSSIBLE_FORMATS类型
	 * 参考DecodeThread构造函数中如下代码：hints.put(DecodeHintType.POSSIBLE_FORMATS,
	 * decodeFormats);
	 */
	private Collection<BarcodeFormat> decodeFormats;

	/**
	 * 【辅助解码的参数(用作MultiFormatReader的参数)】 该参数最终会传入MultiFormatReader，
	 * 上面的decodeFormats和characterSet最终会先加入到decodeHints中 最终被设置到MultiFormatReader中
	 * 参考DecodeHandler构造器中如下代码：multiFormatReader.setHints(hints);
	 */
	private Map<DecodeHintType, ?> decodeHints;

	/**
	 * 【辅助解码的参数(用作MultiFormatReader的参数)】 字符集，告诉扫描器该以何种字符集进行解码
	 * 对应于DecodeHintType.CHARACTER_SET类型
	 * 参考DecodeThread构造器如下代码：hints.put(DecodeHintType.CHARACTER_SET,
	 * characterSet);
	 */
	private String characterSet;

	private Result savedResultToShow;
	private FrameLayout mFlTitle;

	private IntentSource source;

	/**
	 * 图片的路径
	 */
	private String photoPath;

	private Handler mHandler = new MyHandler(this);

	 class MyHandler extends Handler {

		private WeakReference<Activity> activityReference;

		public MyHandler(Activity activity) {
			activityReference = new WeakReference<Activity>(activity);
		}

		@Override
		public void handleMessage(Message msg) {

			switch (msg.what) {
			case PARSE_BARCODE_SUC: // 解析图片成功

				onAlbumDecode( msg.obj+"",PARSE_BARCODE_SUC);
				break;

			case PARSE_BARCODE_FAIL:// 解析图片失败
				onAlbumDecode("",PARSE_BARCODE_FAIL);

				break;

			default:
				break;
			}
			super.handleMessage(msg);
		}

	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Window window = getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.activity_capture);
		mFlTitle=(FrameLayout) findViewById(R.id.fl_title);
//		mTitleBarView = new TitleBarView(this);
//		mTitleBarView.setTitle("二维码扫描");
		hasSurface = false;
		inactivityTimer = new InactivityTimer(this);
		beepManager = new BeepManager(this);
		ambientLightManager = new AmbientLightManager(this);

	}
	public  void setTitleView(View view){
		mFlTitle.addView(view);
	}
	/**
	 * 设置文字和所有颜色  需要的onResume 后执行； ViewfinderView 在onResume 中初始化
	 * @param resId
	 */
	public void setTextColor(int resId){
		if(viewfinderView!=null){
			viewfinderView.setTextColor(resId);
		}

	}
	@Override
	protected void onResume() {
		super.onResume();


		// 相机初始化的动作需要开启相机并测量屏幕大小，这些操作
		// 不建议放到onCreate中，因为如果在onCreate中加上首次启动展示帮助信息的代码的 话，
		// 会导致扫描窗口的尺寸计算有误的bug
		cameraManager = new CameraManager(getApplication());

		viewfinderView = (ViewfinderView) findViewById(R.id.capture_viewfinder_view);
		viewfinderView.setCameraManager(cameraManager);

		handler = null;
		lastResult = null;

		// 摄像头预览功能必须借助SurfaceView，因此也需要在一开始对其进行初始化
		// 如果需要了解SurfaceView的原理
		// 参考:http://blog.csdn.net/luoshengyang/article/details/8661317
		SurfaceView surfaceView = (SurfaceView) findViewById(R.id.capture_preview_view); // 预览
		SurfaceHolder surfaceHolder = surfaceView.getHolder();
		if (hasSurface) {
			// The activity was paused but not stopped, so the surface still
			// exists. Therefore
			// surfaceCreated() won't be called, so init the camera here.
			initCamera(surfaceHolder);

		} else {
			// 防止sdk8的设备初始化预览异常
			surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

			// Install the callback and wait for surfaceCreated() to init the
			// camera.
			surfaceHolder.addCallback(this);
		}

		// 加载声音配置，其实在BeemManager的构造器中也会调用该方法，即在onCreate的时候会调用一次
		beepManager.updatePrefs();

		// 启动闪光灯调节器
		ambientLightManager.start(cameraManager);

		// 恢复活动监控器
		inactivityTimer.onResume();

		source = IntentSource.NONE;
		decodeFormats = null;
		characterSet = null;
	}

	@Override
	protected void onPause() {
		if (handler != null) {
			handler.quitSynchronously();
			handler = null;
		}
		inactivityTimer.onPause();
		ambientLightManager.stop();
		beepManager.close();

		// 关闭摄像头
		cameraManager.closeDriver();
		if (!hasSurface) {
			SurfaceView surfaceView = (SurfaceView) findViewById(R.id.capture_preview_view);
			SurfaceHolder surfaceHolder = surfaceView.getHolder();
			surfaceHolder.removeCallback(this);
		}
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		inactivityTimer.shutdown();
		super.onDestroy();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			if ((source == IntentSource.NONE) && lastResult != null) { // 重新进行扫描
				restartPreviewAfterDelay(0L);
				return true;
			}
			break;
		case KeyEvent.KEYCODE_FOCUS:
		case KeyEvent.KEYCODE_CAMERA:
			// Handle these events so they don't launch the Camera app
			return true;

		case KeyEvent.KEYCODE_VOLUME_UP:
			cameraManager.zoomIn();
			return true;

		case KeyEvent.KEYCODE_VOLUME_DOWN:
			cameraManager.zoomOut();
			return true;

		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {

		if (resultCode == RESULT_OK) {
			final ProgressDialog progressDialog;
			switch (requestCode) {
			case REQUEST_CODE:

				// 获取选中图片的路径
				Cursor cursor = getContentResolver().query(intent.getData(), null, null, null, null);
				if (cursor.moveToFirst()) {
					photoPath = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
				}
				cursor.close();

				progressDialog = new ProgressDialog(this);
				progressDialog.setMessage("正在扫描...");
				progressDialog.setCancelable(false);
				progressDialog.show();

				new Thread(new Runnable() {

					@Override
					public void run() {

						Bitmap img = BitmapUtils.getCompressedBitmap(photoPath);

						BitmapDecoder decoder = new BitmapDecoder(CaptureActivity.this);
						Result result = decoder.getRawResult(img);

						if (result != null) {
							Message m = mHandler.obtainMessage();
							m.what = PARSE_BARCODE_SUC;
							m.obj = ResultParser.parseResult(result).toString();
							mHandler.sendMessage(m);
						} else {
							Message m = mHandler.obtainMessage();
							m.what = PARSE_BARCODE_FAIL;
							mHandler.sendMessage(m);
						}

						progressDialog.dismiss();

					}
				}).start();

				break;

			}
		}

	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (holder == null) {
			Log.e(TAG, "*** WARNING *** surfaceCreated() gave us a null surface!");
		}
		if (!hasSurface) {
			hasSurface = true;
			initCamera(holder);
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		/* hasSurface = false; */
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		hasSurface = false;
	}

	/**
	 * A valid barcode has been found, so give an indication of success and show
	 * the results.
	 * 
	 * @param rawResult
	 *            The contents of the barcode.
	 * @param scaleFactor
	 *            amount by which thumbnail was scaled
	 * @param barcode
	 *            A greyscale bitmap of the camera data which was decoded.
	 */
	public void handleDecode(Result rawResult, Bitmap barcode, float scaleFactor) {

		// 重新计时
		inactivityTimer.onActivity();

		lastResult = rawResult;

		// 把图片画到扫描框
		// viewfinderView.drawResultBitmap(barcode);

		beepManager.playBeepSoundAndVibrate();

		// Toast.makeText(this, "识别结果:" +
		// ResultParser.parseResult(rawResult).toString(),
		// Toast.LENGTH_SHORT).show();
		String resultString = ResultParser.parseResult(rawResult).toString();
		if (resultString.equals("")) {
			Toast.makeText(CaptureActivity.this, "Scan failed!", Toast.LENGTH_SHORT).show();
		} else {
			Intent resultIntent = new Intent();
			Bundle bundle = new Bundle();
			bundle.putString("result", resultString);
			bundle.putParcelable("bitmap", barcode);
			resultIntent.putExtras(bundle);
			this.setResult(RESULT_OK, resultIntent);

			onHandleDecode(resultString,barcode,scaleFactor);
			Log.e("===", resultString);
		}
		CaptureActivity.this.finish();
	}

	public void restartPreviewAfterDelay(long delayMS) {
		if (handler != null) {
			handler.sendEmptyMessageDelayed(R.id.restart_preview, delayMS);
		}
		resetStatusView();
	}

	public ViewfinderView getViewfinderView() {
		return viewfinderView;
	}

	public Handler getHandler() {
		return handler;
	}

	public CameraManager getCameraManager() {
		return cameraManager;
	}

	private void resetStatusView() {
		viewfinderView.setVisibility(View.VISIBLE);
		lastResult = null;
	}

	public void drawViewfinder() {
		viewfinderView.drawViewfinder();
	}

	private void initCamera(SurfaceHolder surfaceHolder) {
		if (surfaceHolder == null) {
			throw new IllegalStateException("No SurfaceHolder provided");
		}

		if (cameraManager.isOpen()) {
			Log.w(TAG, "initCamera() while already open -- late SurfaceView callback?");
			return;
		}
		try {
			cameraManager.openDriver(surfaceHolder);
			// Creating the handler starts the preview, which can also throw a
			// RuntimeException.
			if (handler == null) {
				handler = new CaptureActivityHandler(this, decodeFormats, decodeHints, characterSet, cameraManager);
			}
			decodeOrStoreSavedBitmap(null, null);
		} catch (IOException ioe) {
			Log.w(TAG, ioe);
			displayFrameworkBugMessageAndExit();
		} catch (RuntimeException e) {
			// Barcode Scanner has seen crashes in the wild of this variety:
			// java.?lang.?RuntimeException: Fail to connect to camera service
			Log.w(TAG, "Unexpected error initializing camera", e);
			displayFrameworkBugMessageAndExit();
		}
	}

	/**
	 * 向CaptureActivityHandler中发送消息，并展示扫描到的图像
	 * 
	 * @param bitmap
	 * @param result
	 */
	private void decodeOrStoreSavedBitmap(Bitmap bitmap, Result result) {
		// Bitmap isn't used yet -- will be used soon
		if (handler == null) {
			savedResultToShow = result;
		} else {
			if (result != null) {
				savedResultToShow = result;
			}
			if (savedResultToShow != null) {
				Message message = Message.obtain(handler, R.id.decode_succeeded, savedResultToShow);
				handler.sendMessage(message);
			}
			savedResultToShow = null;
		}
	}

	private void displayFrameworkBugMessageAndExit() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.app_name));
		builder.setMessage(getString(R.string.msg_camera_framework_bug));
		builder.setPositiveButton(R.string.button_ok, new FinishListener(this));
		builder.setOnCancelListener(new FinishListener(this));
		builder.show();
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {

		default:
			break;
		}

	}

	/**
	 * 相册选取QR
	 */
	public void startQRCodeForAlbum(){
		Intent albumIntent = new Intent(Intent.ACTION_PICK, null);
		albumIntent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
		startActivityForResult(albumIntent, REQUEST_CODE);
	}

	/**
	 *	扫描解析
	 * @param resultString
	 * @param barcode
	 * @param scaleFactor
	 */
	public  abstract  void  onHandleDecode(String resultString, Bitmap barcode, float scaleFactor);

	/**
	 *相册选择图片解析
	 * @param resultString
	 * @param status 200 成功 300 失败
	 */
	public  abstract  void  onAlbumDecode(String resultString,int status);
}
