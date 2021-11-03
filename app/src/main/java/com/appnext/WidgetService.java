package com.appnext;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;

import com.appnext.ml.Model;
import com.appnext.KNeighborsClassifier;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


public class WidgetService extends Service {

    private static final String REFRESH_BUTTON_CLICK = "com.nextapp.widget.refresh.CLICK";
    private static final String APP1_BUTTON_CLICK = "com.nextapp.widget.app1.CLICK";
    private static final String APP2_BUTTON_CLICK = "com.nextapp.widget.app2.CLICK";

    public static Bundle pkgInfo;

    public static byte[] appSeq = {13, 18, 13, 13, 13, 1, 18, 18, 13, 10};
    public static double[][] timeSeq = {{4}, {12}, {15}, {15}, {14}, {22}, {30}, {11}, {14}, {14}, {6}};

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        int[] idLs = manager.getAppWidgetIds(new ComponentName(getPackageName(), WidgetProvider.class.getName()));
        //用于遍历所有保存的widget的id
        for (int i : idLs) {
            int appID = i;
            //创建一个远程view，绑定我们要操控的widget布局文件
            RemoteViews remoteView = new RemoteViews(getPackageName(), R.layout.app_widget);

            Intent refreshIntent = new Intent();
            refreshIntent.setClass(this, WidgetProvider.class);
            refreshIntent.setAction(REFRESH_BUTTON_CLICK);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            remoteView.setOnClickPendingIntent(R.id.refresh_button, pendingIntent);

            Intent clickInt1 = new Intent();
            clickInt1.setClass(this, WidgetProvider.class);
            clickInt1.setAction(APP1_BUTTON_CLICK);
            PendingIntent pendingIntent1 = PendingIntent.getBroadcast(this, 0, clickInt1, PendingIntent.FLAG_UPDATE_CURRENT);
            remoteView.setOnClickPendingIntent(R.id.app1_icon, pendingIntent1);

            Intent clickInt2 = new Intent();
            clickInt2.setClass(this, WidgetProvider.class);
            clickInt2.setAction(APP2_BUTTON_CLICK);
            PendingIntent pendingIntent2 = PendingIntent.getBroadcast(this, 0, clickInt2, PendingIntent.FLAG_UPDATE_CURRENT);
            remoteView.setOnClickPendingIntent(R.id.app2_icon, pendingIntent2);

            // 更新 widget
            manager.updateAppWidget(appID, remoteView);
        }

        return START_STICKY;
    }


    public static class WidgetReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            if (pkgInfo == null) {
                pkgInfo = new Bundle();
                pkgInfo.putString("pkgName1", "com.android.contacts");
                pkgInfo.putString("className1", "com.android.contacts.activities.PeopleActivity");
                pkgInfo.putString("pkgName2", "com.android.settings");
                pkgInfo.putString("className2", "com.android.settings.Settings");
            }

            String action = intent.getAction();

            if (action.equals(REFRESH_BUTTON_CLICK)) {
                refresh(context);
            } else if (action.equals(APP1_BUTTON_CLICK)) {
                Intent clickInt = new Intent();
                clickInt.setClassName(pkgInfo.getString("pkgName1"), pkgInfo.getString("className1"));
                clickInt.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(clickInt);
            } else if (action.equals(APP2_BUTTON_CLICK)) {
                Intent clickInt = new Intent();
                clickInt.setClassName(pkgInfo.getString("pkgName2"), pkgInfo.getString("className2"));
                clickInt.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(clickInt);
            }
        }

        public int getIndexOfLargest(float[] array )
        {
            if ( array == null || array.length == 0 ) return -1; // null or empty

            int largest = 0;
            for ( int i = 1; i < array.length; i++ )
            {
                if ( array[i] > array[largest] ) largest = i;
            }
            return largest; // position of the first largest found
        }

        public void refresh(Context context) {

            pkgInfo = new Bundle();

            // predict app1
            KNeighborsClassifier clf = new KNeighborsClassifier(5, 36, 2, timeSeq, appSeq);

            double[] curTime = {4};
            // Prediction:
            int app1_id = clf.predict(curTime);

            pkgInfo.putString("pkgName1", "com.android.vending");
            pkgInfo.putString("className1", "com.android.vending.AssetBrowserActivity");

            int app2_id;
            // predict app2
            try {
                Model model = Model.newInstance(context);

                ByteBuffer byteBuffer = ByteBuffer.allocateDirect(1 * 10 * 1 * 4);
                byteBuffer.order(ByteOrder.nativeOrder());
                for (int i = 0; i < appSeq.length; i++) {
                    byteBuffer.putFloat(appSeq[i]);
                }
                byteBuffer.rewind();

                // Creates inputs for reference.
                TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 10, 1}, DataType.FLOAT32);
                inputFeature0.loadBuffer(byteBuffer);

                // Runs model inference and gets result.
                Model.Outputs outputs = model.process(inputFeature0);
                TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();
                float[] probability = outputFeature0.getFloatArray();
                app2_id = getIndexOfLargest(probability);

                // Releases model resources if no longer used.
                model.close();
            } catch (IOException e) {
                Log.e("tflite error", e.toString());
                app2_id = 0;
            }

            pkgInfo.putString("pkgName2", "com.android.contacts");
            pkgInfo.putString("className2", "com.android.contacts.activities.PeopleActivity");

            RemoteViews remoteView = new RemoteViews(context.getPackageName(), R.layout.app_widget);
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            PackageManager pm = context.getPackageManager();

            // update widget
            try {
                ApplicationInfo appInfo1 = pm.getApplicationInfo(pkgInfo.getString("pkgName1"), PackageManager.GET_META_DATA);
                Resources resources1 = pm.getResourcesForApplication(appInfo1);
                int appIconResId1 = appInfo1.icon;
                Bitmap appIconBitMap1 = BitmapFactory.decodeResource(resources1, appIconResId1);
                remoteView.setImageViewBitmap(R.id.app1_icon, appIconBitMap1);
                remoteView.setTextViewText(R.id.app1_name, String.format("APP%d", app1_id));

                ApplicationInfo appInfo2 = pm.getApplicationInfo(pkgInfo.getString("pkgName2"), PackageManager.GET_META_DATA);
                Resources resources2 = pm.getResourcesForApplication(appInfo2);
                int appIconResId2 = appInfo1.icon;
                Bitmap appIconBitMap2 = BitmapFactory.decodeResource(resources2, appIconResId2);
                remoteView.setImageViewBitmap(R.id.app2_icon, appIconBitMap2);
                remoteView.setTextViewText(R.id.app2_name, String.format("APP%d", app2_id));

                manager.updateAppWidget(new ComponentName(context, WidgetProvider.class), remoteView);

            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
    }
}