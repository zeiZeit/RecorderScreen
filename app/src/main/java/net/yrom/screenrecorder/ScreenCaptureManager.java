package net.yrom.screenrecorder;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodecInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.StrictMode;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.Context.MEDIA_PROJECTION_SERVICE;
import static net.yrom.screenrecorder.MainActivity.ACTION_STOP;
import static net.yrom.screenrecorder.ScreenRecorder.AUDIO_AAC;
import static net.yrom.screenrecorder.ScreenRecorder.VIDEO_AVC;

public class ScreenCaptureManager {
    private Activity activity;
    private static ScreenCaptureManager instance;

    private int mResultCode;
    private Intent mResultData;

    private Notifications mNotifications;
    private MediaProjectionManager mMediaProjectionManager;
    private ScreenRecorder mRecorder;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;


    private ScreenCaptureManager() {

    }

    public static ScreenCaptureManager getInstance() {
        if (instance == null) {
            instance = new ScreenCaptureManager();
        }

        return instance;
    }

    /**
     * 初始化，在授权后调用
     * @param activity
     * @param resultCode
     * @param resultData
     */
    public void init(Activity activity,int resultCode,Intent resultData) {
        this.activity = activity;
        mResultCode = resultCode;
        mResultData = resultData;
        mMediaProjectionManager = (MediaProjectionManager) activity.getSystemService(MEDIA_PROJECTION_SERVICE);
        mNotifications = new Notifications(activity);

    }

    /**
     * 根据android 版本 29以上在服务里边通知到位后调用，29以下在activity里边调用
     */
    public void prepare(){
        mMediaProjection = mMediaProjectionManager.getMediaProjection(mResultCode, Objects.requireNonNull(mResultData));

        MediaProjection.Callback mProjectionCallback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                if (mRecorder != null) {
                    stopRecorder();
                }
            }
        };
        mMediaProjection.registerCallback(mProjectionCallback, new Handler());
    }

    private BroadcastReceiver mStopActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_STOP.equals(intent.getAction())) {
                stopRecordingAndOpenFile();
            }
        }
    };

    public void stopRecordingAndOpenFile() {
        File file = new File(mRecorder.getSavedPath());
        stopRecorder();
        Log.i("@@", "stopRecordingAndOpenFile: "+activity.getString(R.string.recorder_stopped_saved_file)+" " + file);
        StrictMode.VmPolicy vmPolicy = StrictMode.getVmPolicy();
        try {
            // disable detecting FileUriExposure on public file
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
            viewResult(file);
        } finally {
            StrictMode.setVmPolicy(vmPolicy);
        }
    }

    private void viewResult(File file) {
        Intent view = new Intent(Intent.ACTION_VIEW);
        view.addCategory(Intent.CATEGORY_DEFAULT);
        view.setDataAndType(Uri.fromFile(file), VIDEO_AVC);
        view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            activity.startActivity(view);
        } catch (ActivityNotFoundException e) {
            // no activity can open this video
        }
    }


    public void startCapturing() {
        VideoEncodeConfig video = createVideoConfig();
        AudioEncodeConfig audio = createAudioConfig(); // audio can be null
        if (video == null) {
            Log.i("@@", "startCapturing: "+activity.getString(R.string.create_screenRecorder_failure));
            return;
        }

        File dir = getSavingDir();
        if (!dir.exists() && !dir.mkdirs()) {
            cancelRecorder();
            return;
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
        final File file = new File(dir, "Screenshots-" + format.format(new Date())
                + "-" + video.width + "x" + video.height + ".mp4");
        Log.d("@@", "Create recorder with :" + video + " \n " + audio + "\n " + file);
        mRecorder = newRecorder(mMediaProjection, video, audio, file);
        if (hasPermissions()) {
            startRecorder();
        } else {
            cancelRecorder();
        }
    }

    private boolean hasPermissions() {
        PackageManager pm = activity.getPackageManager();
        String packageName = activity.getPackageName();
        int granted = ( PackageManager.PERMISSION_GRANTED) | pm.checkPermission(WRITE_EXTERNAL_STORAGE, packageName);
        return granted == PackageManager.PERMISSION_GRANTED;
    }

    private void startRecorder() {
        if (mRecorder == null) return;
        mRecorder.start();
//        mButton.setText(getString(R.string.stop_recorder));
        activity.registerReceiver(mStopActionReceiver, new IntentFilter(ACTION_STOP));

    }

    private void stopRecorder() {
        mNotifications.clear();
        if (mRecorder != null) {
            mRecorder.quit();
        }
        mRecorder = null;
//        mButton.setText(getString(R.string.restart_recorder));
        try {
            activity.unregisterReceiver(mStopActionReceiver);
        } catch (Exception e) {
            //ignored
        }
    }

    private void cancelRecorder() {
        if (mRecorder == null) return;
        Toast.makeText(activity, activity.getString(R.string.permission_denied_screen_recorder_cancel), Toast.LENGTH_SHORT).show();
        stopRecorder();
    }


    private MediaProjection.Callback mProjectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            if (mRecorder != null) {
                stopRecorder();
            }
        }
    };

    private ScreenRecorder newRecorder(MediaProjection mediaProjection, VideoEncodeConfig video,
                                       AudioEncodeConfig audio, File output) {
        final VirtualDisplay display = getOrCreateVirtualDisplay(mediaProjection, video);
        ScreenRecorder r = new ScreenRecorder(video, audio, display, output.getAbsolutePath());
        r.setCallback(new ScreenRecorder.Callback() {
            long startTime = 0;

            @Override
            public void onStop(Throwable error) {
                stopRecorder();
                if (error != null) {
                    Log.i("@@", "onStop: Recorder error ! See logcat for more details");
                    error.printStackTrace();
                    output.delete();
                } else {
                    Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                            .addCategory(Intent.CATEGORY_DEFAULT)
                            .setData(Uri.fromFile(output));
                    activity.sendBroadcast(intent);
                }
            }

            @Override
            public void onStart() {
                mNotifications.recording(0);
            }

            @Override
            public void onRecording(long presentationTimeUs) {
                if (startTime <= 0) {
                    startTime = presentationTimeUs;
                }
                long time = (presentationTimeUs - startTime) / 1000;
                mNotifications.recording(time);
            }
        });
        return r;
    }


    private VirtualDisplay getOrCreateVirtualDisplay(MediaProjection mediaProjection, VideoEncodeConfig config) {
        if (mVirtualDisplay == null) {
            mVirtualDisplay = mediaProjection.createVirtualDisplay("ScreenRecorder-display0",
                    config.width, config.height, 1 /*dpi*/,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    null /*surface*/, null, null);
        } else {
            // resize if size not matched
            Point size = new Point();
            mVirtualDisplay.getDisplay().getSize(size);
            if (size.x != config.width || size.y != config.height) {
                mVirtualDisplay.resize(config.width, config.height, 1);
            }
        }
        return mVirtualDisplay;
    }

    private AudioEncodeConfig createAudioConfig() {
        String codec = "c2.android.aac.encoder";
        if (codec == null) {
            return null;
        }
        int bitrate = 80000;
        int samplerate = 44100;
        int channelCount = 1;
        int profile = 1;

        return new AudioEncodeConfig(codec, AUDIO_AAC, bitrate, samplerate, channelCount, profile);
    }

    private VideoEncodeConfig createVideoConfig() {
        final String codec = "c2.qti.avc.encoder";
        if (codec == null) {
            // no selected codec ??
            return null;
        }
        // video size

        int width = 1080;
        int height = 1920;
        int framerate = 90;
        int iframe = 1;
        int bitrate = 10000000;
        MediaCodecInfo.CodecProfileLevel profileLevel = null;
        return new VideoEncodeConfig(width, height, bitrate,
                framerate, iframe, codec, VIDEO_AVC, profileLevel);
    }

    private static File getSavingDir() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "Screenshots");
    }


    public void destroy(){
        stopRecorder();
        if (mVirtualDisplay != null) {
            mVirtualDisplay.setSurface(null);
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
        if (mMediaProjection != null) {
            mMediaProjection.unregisterCallback(mProjectionCallback);
            mMediaProjection.stop();
            mMediaProjection = null;
        }

    }


}
