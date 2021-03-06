package net.yrom.screenrecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodecInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.StrictMode;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static net.yrom.screenrecorder.MainActivity.ACTION_STOP;
import static net.yrom.screenrecorder.ScreenRecorder.AUDIO_AAC;
import static net.yrom.screenrecorder.ScreenRecorder.VIDEO_AVC;

public class RecordService extends Service {

    public RecordService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        ScreenCaptureManager.getInstance().prepare();
        ScreenCaptureManager.getInstance().startCapturing();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                ScreenCaptureManager.getInstance().stopRecordingAndOpenFile();
            }
        },10000);
        return super.onStartCommand(intent, flags, startId);
    }


    private void createNotificationChannel() {
        Notification.Builder builder = new Notification.Builder(this.getApplicationContext()); //????????????Notification?????????
        Intent nfIntent = new Intent(this, MainActivity.class); //???????????????????????????????????????????????????

        builder.setContentIntent(PendingIntent.getActivity(this, 0, nfIntent, 0)) // ??????PendingIntent
                .setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.mipmap.ic_launcher)) // ??????????????????????????????(?????????)
                //.setContentTitle("SMI InstantView") // ??????????????????????????????
                .setSmallIcon(R.mipmap.ic_launcher) // ??????????????????????????????
                .setContentText("is running......") // ?????????????????????
                .setWhen(System.currentTimeMillis()); // ??????????????????????????????

        /*????????????Android 8.0?????????*/
        //??????notification??????
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId("notification_id");
        }
        //????????????notification??????
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel("notification_id", "notification_name", NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        }

        Notification notification = builder.build(); // ??????????????????Notification
        notification.defaults = Notification.DEFAULT_SOUND; //????????????????????????
        startForeground(110, notification);

    }
}