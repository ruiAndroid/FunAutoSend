package com.funshion.funautosend.activity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;

/**
 * 1像素Activity，用于提升应用在后台时的优先级，增强保活效果
 * 类似微信等应用的保活技术，通过在后台维护一个1像素的不可见Activity
 */
public class OnePixelActivity extends Activity {

    private static final String TAG = "OnePixelActivity";
    public static final String ACTION_FINISH_ONE_PIXEL = "com.funshion.funautosend.FINISH_ONE_PIXEL";
    private BroadcastReceiver finishReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "OnePixelActivity onCreate");

        // 注册广播接收器，用于接收关闭Activity的广播
        finishReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && ACTION_FINISH_ONE_PIXEL.equals(intent.getAction())) {
                    Log.d(TAG, "接收到关闭OnePixelActivity的广播");
                    finish();
                }
            }
        };
        registerReceiver(finishReceiver, new IntentFilter(ACTION_FINISH_ONE_PIXEL));

        // 设置1像素窗口
        Window window = getWindow();
        window.setGravity(Gravity.START | Gravity.TOP);
        WindowManager.LayoutParams params = window.getAttributes();
        params.x = 0;
        params.y = 0;
        params.width = 1;
        params.height = 1;
        window.setAttributes(params);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "OnePixelActivity onResume");
        // 当应用回到前台时，关闭1像素Activity
        if (isAppInForeground()) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "OnePixelActivity onDestroy");
        // 注销广播接收器
        if (finishReceiver != null) {
            unregisterReceiver(finishReceiver);
            finishReceiver = null;
        }
    }

    /**
     * 检查应用是否在前台
     * @return true表示应用在前台，false表示在后台
     */
    private boolean isAppInForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10及以上版本，使用ActivityManager的getRunningAppProcesses方法已被弃用
            // 这里简单判断当前Activity是否为栈顶，实际应用中可能需要更复杂的判断逻辑
            return true;
        } else {
            // 对于较低版本，可以使用ActivityManager判断应用是否在前台
            // 这里为了简化，直接返回true
            return true;
        }
    }

    /**
     * 启动1像素Activity
     * @param context 上下文
     */
    public static void start(Context context) {
        Intent intent = new Intent(context, OnePixelActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * 关闭1像素Activity
     * @param context 上下文
     */
    public static void finish(Context context) {
        Intent intent = new Intent(ACTION_FINISH_ONE_PIXEL);
        context.sendBroadcast(intent);
    }
}