package com.funshion.funautosend.util;

import android.content.Context;
import android.os.Environment;
import android.text.format.DateFormat;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 日志工具类，支持将日志记录到SD卡中
 */
public class LogUtil {
    private static final String TAG = "LogUtil";
    private static final boolean DEFAULT_LOG_ENABLED = true; // 默认启用日志
    private static final boolean DEFAULT_SAVE_TO_FILE_ENABLED = false; // 默认禁用文件保存
    private static final long MAX_LOG_FILE_SIZE = 5 * 1024 * 1024; // 单日志文件最大5MB
    private static final int MAX_LOG_FILE_COUNT = 7; // 最多保存7个日志文件
    
    private static boolean isLogEnabled = DEFAULT_LOG_ENABLED;
    private static boolean isSaveToFileEnabled = DEFAULT_SAVE_TO_FILE_ENABLED;
    private static String logDirPath = null;
    private static ExecutorService logExecutor = Executors.newSingleThreadExecutor();
    private static String currentLogFileName = null;
    private static File currentLogFile = null;
    private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
    private static SimpleDateFormat fileDateFormat = new SimpleDateFormat("yyyy-MM-dd_HH", Locale.getDefault());
    
    /**
     * 初始化日志工具
     * @param context 上下文
     */
    public static void init(Context context) {
        try {
            // 获取SD卡上的应用日志目录
            File externalDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (externalDir != null) {
                logDirPath = externalDir.getAbsolutePath() + File.separator + "logs";
                File logDir = new File(logDirPath);
                if (!logDir.exists() && !logDir.mkdirs()) {
                    Log.e(TAG, "创建日志目录失败: " + logDirPath);
                    isSaveToFileEnabled = false;
                }
                Log.d(TAG, "日志目录初始化完成: " + logDirPath);
            } else {
                Log.e(TAG, "无法获取外部存储目录");
                isSaveToFileEnabled = false;
            }
        } catch (Exception e) {
            Log.e(TAG, "初始化日志工具失败: " + e.getMessage());
            isSaveToFileEnabled = false;
        }
    }
    
    /**
     * 设置是否启用日志
     * @param enabled 是否启用
     */
    public static void setLogEnabled(boolean enabled) {
        isLogEnabled = enabled;
    }
    
    /**
     * 设置是否启用日志文件保存
     * @param enabled 是否启用
     */
    public static void setSaveToFileEnabled(boolean enabled) {
        isSaveToFileEnabled = enabled;
    }
    
    /**
     * DEBUG级别日志
     * @param tag 标签
     * @param msg 消息
     */
    public static void d(String tag, String msg) {
        if (isLogEnabled) {
            Log.d(tag, msg);
            if (isSaveToFileEnabled) {
                saveLogToFile("D", tag, msg);
            }
        }
    }
    
    /**
     * INFO级别日志
     * @param tag 标签
     * @param msg 消息
     */
    public static void i(String tag, String msg) {
        if (isLogEnabled) {
            Log.i(tag, msg);
            if (isSaveToFileEnabled) {
                saveLogToFile("I", tag, msg);
            }
        }
    }
    
    /**
     * WARNING级别日志
     * @param tag 标签
     * @param msg 消息
     */
    public static void w(String tag, String msg) {
        if (isLogEnabled) {
            Log.w(tag, msg);
            if (isSaveToFileEnabled) {
                saveLogToFile("W", tag, msg);
            }
        }
    }
    
    /**
     * ERROR级别日志
     * @param tag 标签
     * @param msg 消息
     */
    public static void e(String tag, String msg) {
        if (isLogEnabled) {
            Log.e(tag, msg);
            if (isSaveToFileEnabled) {
                saveLogToFile("E", tag, msg);
            }
        }
    }
    
    /**
     * ERROR级别日志，带异常信息
     * @param tag 标签
     * @param msg 消息
     * @param tr 异常
     */
    public static void e(String tag, String msg, Throwable tr) {
        if (isLogEnabled) {
            Log.e(tag, msg, tr);
            if (isSaveToFileEnabled) {
                StringBuilder sb = new StringBuilder(msg);
                sb.append("\n");
                if (tr != null) {
                    sb.append(tr.getMessage()).append("\n");
                    for (StackTraceElement element : tr.getStackTrace()) {
                        sb.append("    at ").append(element.toString()).append("\n");
                    }
                }
                saveLogToFile("E", tag, sb.toString());
            }
        }
    }
    
    /**
     * 保存日志到文件
     * @param level 日志级别
     * @param tag 标签
     * @param msg 消息
     */
    private static void saveLogToFile(final String level, final String tag, final String msg) {
        if (logDirPath == null) {
            return;
        }
        
        logExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // 检查并更新日志文件
                    checkAndUpdateLogFile();
                    
                    if (currentLogFile != null) {
                        // 构建日志内容
                        String timestamp = dateFormat.format(new Date());
                        String logContent = timestamp + " | " + level + " | " + tag + " | " + msg + "\n";
                        
                        // 写入日志文件
                        try (FileWriter writer = new FileWriter(currentLogFile, true)) {
                            writer.write(logContent);
                            writer.flush();
                        }
                        
                        // 检查日志文件大小，超过限制则创建新文件
                        if (currentLogFile.length() > MAX_LOG_FILE_SIZE) {
                            rotateLogFile();
                        }
                    }
                } catch (IOException e) {
                    // 避免日志写入失败导致递归调用
                    Log.e(TAG, "写入日志文件失败: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * 检查并更新日志文件
     */
    private static void checkAndUpdateLogFile() {
        String newLogFileName = "app_log_" + fileDateFormat.format(new Date()) + ".log";
        
        // 如果文件名变化或文件不存在，创建新文件
        if (currentLogFileName == null || !currentLogFileName.equals(newLogFileName) || 
                (currentLogFile != null && !currentLogFile.exists())) {
            currentLogFileName = newLogFileName;
            currentLogFile = new File(logDirPath, currentLogFileName);
            
            // 清理旧日志文件
            cleanupOldLogFiles();
        }
    }
    
    /**
     * 日志文件轮转
     */
    private static void rotateLogFile() {
        try {
            // 创建新的日志文件，添加序号后缀
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH_mm_ss", Locale.getDefault()).format(new Date());
            String rotatedFileName = "app_log_" + timestamp + ".log";
            currentLogFile = new File(logDirPath, rotatedFileName);
            currentLogFileName = rotatedFileName;
            
            // 清理旧日志文件
            cleanupOldLogFiles();
        } catch (Exception e) {
            Log.e(TAG, "日志文件轮转失败: " + e.getMessage());
        }
    }
    
    /**
     * 清理超过数量限制的旧日志文件
     */
    private static void cleanupOldLogFiles() {
        try {
            File logDir = new File(logDirPath);
            File[] files = logDir.listFiles((dir, name) -> name.startsWith("app_log_") && name.endsWith(".log"));
            
            if (files != null && files.length > MAX_LOG_FILE_COUNT) {
                // 按照修改时间排序，删除最旧的文件
                java.util.Arrays.sort(files, (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));
                
                int filesToDelete = files.length - MAX_LOG_FILE_COUNT;
                for (int i = 0; i < filesToDelete; i++) {
                    if (!files[i].delete()) {
                        Log.e(TAG, "删除旧日志文件失败: " + files[i].getName());
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "清理旧日志文件失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取日志目录路径
     * @return 日志目录路径
     */
    public static String getLogDirPath() {
        return logDirPath;
    }
    
    /**
     * 关闭日志工具，释放资源
     */
    public static void shutdown() {
        logExecutor.shutdown();
        Log.d(TAG, "日志工具已关闭");
    }
}