package com.funshion.funautosend.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.funshion.funautosend.service.SmsForwardService;

/**
 * 系统广播接收器，用于监听各种系统广播事件
 * 当接收到特定广播时，自动重启前台服务，提高应用在后台的存活率
 */
public class SystemBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "SystemBroadcastReceiver";

    private static final String ACTION_SERVICE_RESTART = "com.funshion.funautosend.SERVICE_RESTART";
    private static final String ACTION_REFRESH_RETRY = "com.funshion.funautosend.ACTION_REFRESH_RETRY";
    private static final String ACTION_REFRESH_DATA = "com.funshion.funautosend.ACTION_REFRESH_DATA";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        Log.d(TAG, "接收到广播: " + action);

        // 根据不同的广播事件，决定是否需要重启服务
        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
                // 设备开机完成，重启服务
                Log.d(TAG, "设备开机完成，启动前台服务");
                startSmsForwardService(context);
                break;
            
            case ConnectivityManager.CONNECTIVITY_ACTION:
                // 网络连接状态变化，检查是否连接到网络，如果是则重启服务
                if (isNetworkConnected(context)) {
                    Log.d(TAG, "网络已连接，检查并启动前台服务");
                    startSmsForwardService(context);
                }
                break;
            
            case Intent.ACTION_USER_PRESENT:
                // 用户解锁屏幕，重启服务
                Log.d(TAG, "用户解锁屏幕，检查并启动前台服务");
                startSmsForwardService(context);
                break;
            
            case Intent.ACTION_PACKAGE_REPLACED:
                // 应用更新后重启
                if (context.getPackageName().equals(intent.getData().getSchemeSpecificPart())) {
                    Log.d(TAG, "应用已更新，重启前台服务");
                    startSmsForwardService(context);
                }
                break;
            
            case ACTION_SERVICE_RESTART:
                // 自定义服务重启广播
                Log.d(TAG, "收到服务重启广播，启动前台服务");
                startSmsForwardService(context);
                break;
                
            case ACTION_REFRESH_RETRY:
                // 刷新重试广播
                Log.d(TAG, "收到刷新重试广播，触发服务刷新操作");
                // 直接启动服务，服务内部会处理刷新逻辑
                startSmsForwardService(context);
                break;
                
            case ACTION_REFRESH_DATA:
                // 定时刷新数据广播（来自AlarmManager）
                Log.d(TAG, "收到定时刷新数据广播，触发服务刷新操作");
                // 启动服务并传递刷新数据的意图
                Intent refreshIntent = new Intent(context, SmsForwardService.class);
                // 设置一个标记，让服务知道这是来自AlarmManager的刷新请求
                refreshIntent.putExtra("FROM_ALARM_MANAGER", true);
                
                // 根据Android版本使用不同的启动方式
                if (android.os.Build.VERSION.SDK_INT >= 26) { // 26对应Android 8.0(O)
                    // Android 8.0及以上使用startForegroundService
                    context.startForegroundService(refreshIntent);
                } else {
                    // Android 8.0以下使用普通的startService
                    context.startService(refreshIntent);
                }
                
                Log.d(TAG, "通过AlarmManager触发的刷新服务已启动");
                break;
        }
    }

    /**
     * 启动短信转发前台服务
     */
    private void startSmsForwardService(Context context) {
        try {
            Intent serviceIntent = new Intent(context, SmsForwardService.class);
            
            // 检查Android版本，使用不同的启动方式
            if (android.os.Build.VERSION.SDK_INT >= 26) { // 26对应Android 8.0(O)
                // Android 8.0及以上使用startForegroundService
                context.startForegroundService(serviceIntent);
            } else {
                // Android 8.0以下使用普通的startService
                context.startService(serviceIntent);
            }
            
            Log.d(TAG, "SmsForwardService启动成功");
        } catch (Exception e) {
            Log.e(TAG, "SmsForwardService启动失败: " + e.getMessage());
        }
    }

    /**
     * 检查网络是否连接
     */
    private boolean isNetworkConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        }
        return false;
    }
}