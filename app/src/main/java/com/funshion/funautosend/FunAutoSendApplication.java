package com.funshion.funautosend;

import android.app.Application;
import android.content.Context;
import android.os.Process;
import android.util.Log;

import androidx.work.Configuration;
import androidx.work.WorkManager;

import com.funshion.funautosend.util.LogUtil;

/**
 * 应用程序主类
 * 负责初始化WorkManager和其他全局组件
 */
public class FunAutoSendApplication extends Application implements Configuration.Provider {
    private static final String TAG = "FunAutoSendApplication";
    
    @Override
    public void onCreate() {
        super.onCreate();
        // 初始化日志工具
        LogUtil.init(this);
        LogUtil.setLogEnabled(true);
        LogUtil.d(TAG, "应用启动，进程ID: " + Process.myPid() + ", 进程名: " + getCurrentProcessName());
        
        // 不需要手动初始化WorkManager，因为我们实现了Configuration.Provider接口
        // 系统会自动使用我们提供的配置进行初始化
    }
    
    @Override
    public Configuration getWorkManagerConfiguration() {
        // 配置WorkManager
        return new Configuration.Builder()
                // 设置日志级别
                .setMinimumLoggingLevel(Log.INFO)
                // 使用默认线程池执行器，避免并发数据库访问冲突
                // 移除自定义Executor，让WorkManager使用内部优化的线程池
                .build();
    }
    
    /**
     * 获取当前进程名
     */
    private String getCurrentProcessName() {
        try {
            int pid = Process.myPid();
            java.io.FileInputStream fis = new java.io.FileInputStream("/proc/" + pid + "/cmdline");
            byte[] buffer = new byte[1024];
            int len = fis.read(buffer);
            fis.close();
            // 移除终止符
            if (len > 0) {
                int index = 0;
                for (; index < len; index++) {
                    if (buffer[index] == 0) break;
                }
                return new String(buffer, 0, index);
            }
        } catch (Exception e) {
            Log.e(TAG, "获取进程名失败: " + e.getMessage());
        }
        return "unknown";
    }
}