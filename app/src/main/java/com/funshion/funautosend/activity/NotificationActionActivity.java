package com.funshion.funautosend.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import com.funshion.funautosend.util.LogUtil;

import androidx.annotation.Nullable;

import com.funshion.funautosend.MainActivity;
import com.funshion.funautosend.service.SmsForwardService;
import com.funshion.funautosend.util.NotificationUtils;

/**
 * 通知操作处理Activity
 * 用于处理通知的操作按钮点击事件，如打开应用、停止服务等
 * 这个Activity是透明的，不会显示给用户，但可以处理通知的交互操作
 */
public class NotificationActionActivity extends Activity {

    private static final String TAG = "NotificationActionActivity";

    @SuppressLint("LongLogTag")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogUtil.d(TAG, "NotificationActionActivity onCreate");

        // 获取意图中的操作类型
        Intent intent = getIntent();
        if (intent != null) {
            String action = intent.getAction();
            LogUtil.d(TAG, "接收到的操作: " + action);

            // 根据操作类型执行相应的操作
            handleNotificationAction(action);
        }

        // 完成操作后立即关闭Activity
        finish();
    }

    /**
     * 处理通知的操作
     * @param action 操作类型
     */
    private void handleNotificationAction(String action) {
        if (action == null) {
            return;
        }

        switch (action) {
            case NotificationUtils.ACTION_OPEN_APP:
                // 打开应用主界面
                openMainApp();
                break;
            case NotificationUtils.ACTION_STOP_SERVICE:
                // 停止前台服务
                stopForegroundService();
                break;
            default:
                LogUtil.d(TAG, "未知的操作类型: " + action);
                // 未知操作，默认打开应用
                openMainApp();
                break;
        }
    }

    /**
     * 打开应用主界面
     */
    private void openMainApp() {
        LogUtil.d(TAG, "打开应用主界面");
        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(mainIntent);
    }

    /**
     * 停止前台服务
     */
    private void stopForegroundService() {
        LogUtil.d(TAG, "停止前台服务");
        Intent serviceIntent = new Intent(this, SmsForwardService.class);
        serviceIntent.setAction(NotificationUtils.ACTION_STOP_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogUtil.d(TAG, "NotificationActionActivity onDestroy");
    }
}