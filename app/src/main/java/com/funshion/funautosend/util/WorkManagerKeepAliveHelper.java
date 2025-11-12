package com.funshion.funautosend.util;

import android.content.Context;
import com.funshion.funautosend.util.LogUtil;

import androidx.work.Configuration;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.funshion.funautosend.service.KeepAliveWorker;

import java.util.concurrent.TimeUnit;

/**
 * WorkManager保活助手类
 * 负责管理WorkManager的定期任务调度，用于应用保活
 */
public class WorkManagerKeepAliveHelper {
    private static final String TAG = "WorkManagerKeepAliveHelper";
    private static final String WORK_NAME = "KeepAlivePeriodicWork";
    
    // WorkManager任务间隔，最小为15分钟（Android系统限制）
    private static final long MIN_INTERVAL_MINUTES = 15;
    
    private Context context;
    private WorkManager workManager;
    
    public WorkManagerKeepAliveHelper(Context context) {
        this.context = context.getApplicationContext();
        this.workManager = getWorkManagerInstance();
    }
    
    /**
     * 安全获取WorkManager实例
     * 在多进程环境下确保WorkManager正确初始化
     */
    private WorkManager getWorkManagerInstance() {
        try {
            // 尝试获取WorkManager实例
            WorkManager instance = WorkManager.getInstance(this.context);
            LogUtil.d(TAG, "成功获取WorkManager实例");
            return instance;
        } catch (IllegalStateException e) {
            LogUtil.e(TAG, "获取WorkManager实例失败，尝试手动初始化: " + e.getMessage());
            try {
                // 手动初始化WorkManager
                Configuration configuration = new Configuration.Builder()
                        .setMinimumLoggingLevel(android.util.Log.INFO)
                        .build();
                WorkManager.initialize(this.context, configuration);
                LogUtil.d(TAG, "WorkManager手动初始化成功");
                return WorkManager.getInstance(this.context);
            } catch (Exception ex) {
                LogUtil.e(TAG, "WorkManager手动初始化也失败: " + ex.getMessage());
                return null;
            }
        }
    }
    
    /**
     * 启动WorkManager定期保活任务
     */
    public void startPeriodicKeepAliveWork() {
        try {
            LogUtil.d(TAG, "启动WorkManager定期保活任务");
            
            // 检查workManager实例是否可用
            if (workManager == null) {
                LogUtil.e(TAG, "WorkManager实例不可用，尝试重新获取");
                workManager = getWorkManagerInstance();
                if (workManager == null) {
                    LogUtil.e(TAG, "重新获取WorkManager实例失败，无法启动定期保活任务");
                    return;
                }
            }
            
            // 创建约束条件
            Constraints constraints = new Constraints.Builder()
                    // 任意网络状态下都可以执行，确保即使在弱网环境下也能保活
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    // 不需要设备空闲
                    .setRequiresDeviceIdle(false)
                    // 不需要设备充电
                    .setRequiresCharging(false)
                    // 在任何电池状态下都能执行
                    .setRequiresBatteryNotLow(false)
                    .build();
            
            // 创建定期工作请求
            PeriodicWorkRequest periodicWorkRequest = new PeriodicWorkRequest.Builder(
                    KeepAliveWorker.class,
                    MIN_INTERVAL_MINUTES, TimeUnit.MINUTES,
                    // 可以设置一个灵活性窗口，让系统在这个窗口内选择合适的时间执行
                    // 这里设置为5分钟的灵活性窗口
                    5, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    // 设置重试策略
                    .setBackoffCriteria(
                            androidx.work.BackoffPolicy.LINEAR,
                            10, TimeUnit.MINUTES)
                    .build();
            
            // 调度工作，使用KEEP策略确保只有一个相同名称的工作在运行
            workManager.enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodicWorkRequest);
            
            LogUtil.d(TAG, "WorkManager定期保活任务调度成功，间隔：" + MIN_INTERVAL_MINUTES + "分钟");
        } catch (Exception e) {
            LogUtil.e(TAG, "启动WorkManager定期保活任务失败: " + e.getMessage());
        }
    }
    
    /**
     * 停止WorkManager定期保活任务
     */
    public void stopPeriodicKeepAliveWork() {
        try {
            LogUtil.d(TAG, "停止WorkManager定期保活任务");
            
            // 取消指定名称的定期工作
            workManager.cancelUniqueWork(WORK_NAME);
            
            LogUtil.d(TAG, "WorkManager定期保活任务停止成功");
        } catch (Exception e) {
            LogUtil.e(TAG, "停止WorkManager定期保活任务失败: " + e.getMessage());
        }
    }
    
    /**
     * 立即执行一次保活检查
     * 用于应用启动或特殊情况下立即触发保活检查
     */
    public void runKeepAliveCheckNow() {
        try {
            LogUtil.d(TAG, "立即执行WorkManager保活检查");
            
            // 检查workManager实例是否可用
            if (workManager == null) {
                LogUtil.e(TAG, "WorkManager实例不可用，尝试重新获取");
                workManager = getWorkManagerInstance();
                if (workManager == null) {
                    LogUtil.e(TAG, "重新获取WorkManager实例失败，无法执行立即保活检查");
                    return;
                }
            }
            
            // 创建一次性工作请求
            androidx.work.OneTimeWorkRequest oneTimeWorkRequest = new androidx.work.OneTimeWorkRequest.Builder(
                    KeepAliveWorker.class)
                    .build();
            
            // 使用enqueueUniqueWork确保同时只有一个保活检查任务在运行
            // REPLACE策略：如果已经存在相同名称的任务，则替换它
            workManager.enqueueUniqueWork(
                    "IMMEDIATE_KEEP_ALIVE_CHECK",
                    ExistingWorkPolicy.REPLACE,
                    oneTimeWorkRequest);
            
            LogUtil.d(TAG, "WorkManager立即保活检查调度成功");
        } catch (Exception e) {
            LogUtil.e(TAG, "执行WorkManager立即保活检查失败: " + e.getMessage());
        }
    }
}