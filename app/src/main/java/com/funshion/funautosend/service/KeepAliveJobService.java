package com.funshion.funautosend.service;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import com.funshion.funautosend.util.LogUtil;

/**
 * JobScheduler服务，用于定期检查并重启SmsForwardService
 * 这是Android官方推荐的后台任务调度方式，系统会在合适的时机执行任务
 */
public class KeepAliveJobService extends JobService {
    private static final String TAG = "KeepAliveJobService";

    @Override
    public boolean onStartJob(JobParameters params) {
        LogUtil.d(TAG, "JobService启动，检查并重启前台服务");
        
        // 检查并启动SmsForwardService
        checkAndStartSmsForwardService();
        
        // 任务执行完毕，不需要在后台线程继续处理
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LogUtil.d(TAG, "JobService被停止");
        // 当任务被停止时，返回true表示希望系统重新安排这个任务
        return true;
    }

    /**
     * 检查并启动短信转发前台服务
     */
    private void checkAndStartSmsForwardService() {
        try {
            // 创建启动服务的Intent
            Intent serviceIntent = new Intent(this, SmsForwardService.class);
            
            // 检查Android版本，使用不同的启动方式
            if (android.os.Build.VERSION.SDK_INT >= 26) { // 26对应Android 8.0(O)
                // Android 8.0及以上使用startForegroundService
                startForegroundService(serviceIntent);
            } else {
                // Android 8.0以下使用普通的startService
                startService(serviceIntent);
            }
            
            LogUtil.d(TAG, "SmsForwardService启动成功");
        } catch (Exception e) {
            LogUtil.e(TAG, "SmsForwardService启动失败: " + e.getMessage());
        }
    }
}