package com.funshion.funautosend.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.funshion.funautosend.util.FloatWindowPermissionHelper;

/**
 * 悬浮窗服务重启广播接收器
 * 用于监听系统广播并在特定事件时重启悬浮窗服务
 */
public class FloatWindowRestartReceiver extends BroadcastReceiver {
    private static final String TAG = "FloatWindowRestartReceiver";

    public static final String ACTION_RESTART_FLOAT_WINDOW_SERVICE = "com.funshion.funautosend.action.RESTART_FLOAT_WINDOW_SERVICE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        Log.d(TAG, "接收到广播: " + action);

        // 根据不同的广播事件决定是否重启悬浮窗服务
        switch (action) {
            case ACTION_RESTART_FLOAT_WINDOW_SERVICE:
                // 自定义重启服务广播
                Log.d(TAG, "收到重启悬浮窗服务广播");
                startFloatWindowServiceIfPermissionGranted(context);
                break;
            
            case Intent.ACTION_SCREEN_ON:
                // 屏幕打开，尝试重启服务
                Log.d(TAG, "屏幕打开，尝试重启悬浮窗服务");
                startFloatWindowServiceWithDelay(context, 500); // 延迟500ms启动
                break;
            
            case Intent.ACTION_SCREEN_OFF:
                // 屏幕关闭，不需要特殊处理
                Log.d(TAG, "屏幕关闭");
                break;
            
            case Intent.ACTION_USER_PRESENT:
                // 用户解锁屏幕，这是专门针对解锁事件的广播
                Log.d(TAG, "用户解锁屏幕，重启悬浮窗服务");
                startFloatWindowServiceWithDelay(context, 1000); // 延迟1秒启动，确保系统完全恢复
                break;
            
            case Intent.ACTION_BOOT_COMPLETED:
                // 设备开机完成，重启服务
                Log.d(TAG, "设备开机完成，启动悬浮窗服务");
                startFloatWindowServiceIfPermissionGranted(context);
                break;
            
            case Intent.ACTION_MY_PACKAGE_REPLACED:
                // 应用更新后重启
                Log.d(TAG, "应用已更新，重启悬浮窗服务");
                startFloatWindowServiceIfPermissionGranted(context);
                break;
        }
    }

    /**
     * 检查是否有悬浮窗权限，如果有则启动悬浮窗服务
     * @param context 上下文
     */
    private void startFloatWindowServiceIfPermissionGranted(Context context) {
        if (FloatWindowPermissionHelper.hasFloatWindowPermission(context)) {
            Log.d(TAG, "已有悬浮窗权限，启动悬浮窗服务");
            FloatWindowService.start(context);
        } else {
            Log.d(TAG, "没有悬浮窗权限，不启动悬浮窗服务");
        }
    }
    
    /**
     * 延迟启动悬浮窗服务，给系统足够的时间完成状态转换
     * @param context 上下文
     * @param delayMs 延迟时间(毫秒)
     */
    private void startFloatWindowServiceWithDelay(final Context context, final long delayMs) {
        Log.d(TAG, "安排延迟" + delayMs + "ms后启动悬浮窗服务");
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "执行延迟启动悬浮窗服务");
                startFloatWindowServiceIfPermissionGranted(context);
            }
        }, delayMs);
    }
}