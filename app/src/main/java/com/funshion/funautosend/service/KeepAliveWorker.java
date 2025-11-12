package com.funshion.funautosend.service;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import com.funshion.funautosend.util.LogUtil;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * WorkManager后台保活任务
 * 用于定期检查并重启SmsForwardService，增强应用保活能力
 * WorkManager是Android官方推荐的后台任务调度解决方案，在不同Android版本上表现更加稳定
 */
public class KeepAliveWorker extends Worker {
    private static final String TAG = "KeepAliveWorker";

    public KeepAliveWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        LogUtil.d(TAG, "WorkManager保活任务执行，检查并重启前台服务");
        
        // 检查并启动SmsForwardService
        checkAndStartSmsForwardService();
        
        // 检查并启动AudioPlayerService（无声音乐服务）
        checkAndStartAudioPlayerService();
        
        // 检查并启动GuardianService（守护进程服务）
        checkAndStartGuardianService();
        
        // 返回成功结果，系统会根据我们的调度策略继续调度任务
        return Result.success();
    }

    /**
     * 检查服务是否正在运行
     * @param serviceClass 服务类
     * @return 是否正在运行
     */
    private boolean isServiceRunning(Class<?> serviceClass) {
        try {
            String serviceName = serviceClass.getName();
            android.app.ActivityManager manager = (android.app.ActivityManager) getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
            if (manager != null) {
                for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                    if (serviceName.equals(service.service.getClassName())) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            LogUtil.e(TAG, "检查服务运行状态时出错: " + e.getMessage());
            return false;
        }
    }

    /**
     * 检查并启动短信转发前台服务
     */
    private void checkAndStartSmsForwardService() {
        try {
            // 先检查服务是否已经在运行
            if (isServiceRunning(SmsForwardService.class)) {
                LogUtil.d(TAG, "SmsForwardService已经在运行，无需重复启动");
                return;
            }
            
            // 创建启动服务的Intent
            Intent serviceIntent = new Intent(getApplicationContext(), SmsForwardService.class);
            
            // 检查Android版本，使用不同的启动方式
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // Android 8.0及以上
                // Android 8.0及以上使用startForegroundService
                getApplicationContext().startForegroundService(serviceIntent);
            } else {
                // Android 8.0以下使用普通的startService
                getApplicationContext().startService(serviceIntent);
            }
            
            LogUtil.d(TAG, "SmsForwardService启动成功");
        } catch (Exception e) {
            LogUtil.e(TAG, "SmsForwardService启动失败: " + e.getMessage());
        }
    }

    /**
     * 检查并启动无声音乐播放服务
     */
    private void checkAndStartAudioPlayerService() {
        try {
            // 先检查服务是否已经在运行
            if (isServiceRunning(AudioPlayerService.class)) {
                LogUtil.d(TAG, "AudioPlayerService已经在运行，无需重复启动");
                return;
            }
            
            Intent audioServiceIntent = new Intent(getApplicationContext(), AudioPlayerService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0及以上使用startForegroundService
                getApplicationContext().startForegroundService(audioServiceIntent);
            } else {
                // Android 8.0以下使用普通的startService
                getApplicationContext().startService(audioServiceIntent);
            }
            LogUtil.d(TAG, "AudioPlayerService启动成功");
        } catch (Exception e) {
            LogUtil.e(TAG, "AudioPlayerService启动失败: " + e.getMessage());
        }
    }

    /**
     * 检查并启动守护进程服务
     */
    private void checkAndStartGuardianService() {
        try {
            // 先检查服务是否已经在运行
            if (isServiceRunning(GuardianService.class)) {
                LogUtil.d(TAG, "GuardianService已经在运行，无需重复启动");
                return;
            }
            
            Intent guardianIntent = new Intent(getApplicationContext(), GuardianService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0及以上使用startForegroundService
                getApplicationContext().startForegroundService(guardianIntent);
            } else {
                // Android 8.0以下使用普通的startService
                getApplicationContext().startService(guardianIntent);
            }
            LogUtil.d(TAG, "GuardianService启动成功");
        } catch (Exception e) {
            LogUtil.e(TAG, "GuardianService启动失败: " + e.getMessage());
        }
    }
}