package com.funshion.funautosend.util;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.funshion.funautosend.service.KeepAliveJobService;

import java.util.concurrent.TimeUnit;

/**
 * 保活管理器，统一管理各种进程保活机制
 * 包括：JobScheduler、系统广播监听等
 */
public class KeepAliveManager {
    private static final String TAG = "KeepAliveManager";
    private static final int JOB_ID = 1001;
    private static KeepAliveManager instance;
    private Context context;
    private SystemBroadcastReceiver systemBroadcastReceiver;
    private WorkManagerKeepAliveHelper workManagerKeepAliveHelper;

    private KeepAliveManager(Context context) {
        this.context = context.getApplicationContext();
        this.systemBroadcastReceiver = new SystemBroadcastReceiver();
        this.workManagerKeepAliveHelper = new WorkManagerKeepAliveHelper(context);
    }

    /**
     * 获取单例实例
     */
    public static synchronized KeepAliveManager getInstance(Context context) {
        if (instance == null) {
            instance = new KeepAliveManager(context);
        }
        return instance;
    }

    /**
     * 启动所有保活机制
     */
    public void startAllKeepAliveMechanisms() {
        Log.d(TAG, "启动所有保活机制");
        
        // 注册系统广播接收器
        registerSystemBroadcastReceiver();
        
        // 启动JobScheduler任务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startJobScheduler();
        }
        
        // 启动WorkManager定期保活任务
        workManagerKeepAliveHelper.startPeriodicKeepAliveWork();
        
        // 立即执行一次保活检查
        workManagerKeepAliveHelper.runKeepAliveCheckNow();
    }

    /**
     * 停止所有保活机制
     */
    public void stopAllKeepAliveMechanisms() {
        Log.d(TAG, "停止所有保活机制");
        
        // 注销系统广播接收器
        unregisterSystemBroadcastReceiver();
        
        // 取消JobScheduler任务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cancelJobScheduler();
        }
        
        // 停止WorkManager定期保活任务
        workManagerKeepAliveHelper.stopPeriodicKeepAliveWork();
    }

    /**
     * 注册系统广播接收器
     */
    private void registerSystemBroadcastReceiver() {
        try {
            IntentFilter intentFilter = new IntentFilter();
            // 监听开机广播
            intentFilter.addAction(Intent.ACTION_BOOT_COMPLETED);
            // 监听网络状态变化广播
            intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            // 监听屏幕解锁广播
            intentFilter.addAction(Intent.ACTION_USER_PRESENT);
            // 监听应用更新广播
            intentFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
            intentFilter.addDataScheme("package");
            
            // 注册广播接收器
            context.registerReceiver(systemBroadcastReceiver, intentFilter);
            Log.d(TAG, "系统广播接收器注册成功");
        } catch (Exception e) {
            Log.e(TAG, "系统广播接收器注册失败: " + e.getMessage());
        }
    }

    /**
     * 注销系统广播接收器
     */
    private void unregisterSystemBroadcastReceiver() {
        try {
            if (systemBroadcastReceiver != null) {
                context.unregisterReceiver(systemBroadcastReceiver);
                Log.d(TAG, "系统广播接收器注销成功");
            }
        } catch (Exception e) {
            Log.e(TAG, "系统广播接收器注销失败: " + e.getMessage());
        }
    }

    /**
     * 启动JobScheduler任务
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startJobScheduler() {
        try {
            JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (jobScheduler == null) {
                Log.e(TAG, "JobScheduler服务获取失败");
                return;
            }
            
            // 取消已存在的任务
            jobScheduler.cancel(JOB_ID);
            
            // 创建新的任务
            JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, new ComponentName(context, KeepAliveJobService.class));
            
            // 设置任务执行条件
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7.0及以上，使用setPeriodic方法，但最小间隔为15分钟
                builder.setPeriodic(TimeUnit.MINUTES.toMillis(15));
            } else {
                // Android 7.0以下，可以设置更小的间隔
                builder.setPeriodic(TimeUnit.MINUTES.toMillis(5));
            }
            
            // 设置网络条件：任意网络类型都可以执行
            builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
            
            // 设置设备重启后是否继续执行任务
            builder.setPersisted(true);
            
            // 设置任务在空闲时执行，降低资源消耗
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setRequiresDeviceIdle(false);
            }
            
            // 设置任务在充电时执行，降低资源消耗
            builder.setRequiresCharging(false);
            
            // 提交任务
            int result = jobScheduler.schedule(builder.build());
            if (result == JobScheduler.RESULT_SUCCESS) {
                Log.d(TAG, "JobScheduler任务调度成功");
            } else {
                Log.e(TAG, "JobScheduler任务调度失败: " + result);
            }
        } catch (Exception e) {
            Log.e(TAG, "JobScheduler启动失败: " + e.getMessage());
        }
    }

    /**
     * 取消JobScheduler任务
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void cancelJobScheduler() {
        try {
            JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (jobScheduler != null) {
                jobScheduler.cancel(JOB_ID);
                Log.d(TAG, "JobScheduler任务取消成功");
            }
        } catch (Exception e) {
            Log.e(TAG, "JobScheduler任务取消失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取WorkManager保活助手实例
     * 用于特殊情况下手动触发保活检查
     */
    public WorkManagerKeepAliveHelper getWorkManagerKeepAliveHelper() {
        return workManagerKeepAliveHelper;
    }
}