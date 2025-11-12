package com.funshion.funautosend.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.provider.Settings;
import com.funshion.funautosend.util.LogUtil;

import androidx.annotation.Nullable;

import com.funshion.funautosend.IKeepAliveAidlInterface;
import com.funshion.funautosend.activity.OnePixelActivity;
import com.funshion.funautosend.service.FloatWindowService;
import com.funshion.funautosend.util.ApiClient;
import com.funshion.funautosend.util.NotificationUtils;
import com.funshion.funautosend.util.PermissionHelper;
import com.funshion.funautosend.util.PreferencesHelper;
import com.funshion.funautosend.util.SystemBroadcastReceiver;
import com.funshion.funautosend.util.KeepAliveManager;
import com.funshion.funautosend.util.SmsHelper;

import android.app.Notification;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 短信转发前台服务，用于保证应用在后台长时间稳定运行
 */
public class SmsForwardService extends Service {

    private static final String TAG = "SmsForwardService";
    private Handler refreshHandler; // 用于后台自动刷新的Handler
    private HandlerThread refreshHandlerThread; // 用于刷新任务的HandlerThread
    private Runnable refreshRunnable; // 自动刷新任务
    private static final int REFRESH_INTERVAL = 1*60 * 1000; // 刷新间隔时间（1分钟，单位：毫秒）
    private static final int HIGH_PRIORITY_INTERVAL = 1800 * 1000; // 30分钟切换到高优先级通知一次
    private static final long WAKELOCK_TIMEOUT = 30 * 1000; // WakeLock超时时间，30秒
    private static final long NETWORK_RETRY_DELAY = 10 * 1000; // 网络重连延迟，10秒
    private PowerManager.WakeLock wakeLock; // 用于保持CPU唤醒的WakeLock
    private ConnectivityManager.NetworkCallback networkCallback; // 网络状态监听回调
    private boolean isNetworkAvailable = true; // 网络可用性标志
    
    // 短信扫描相关
    private Handler smsScanHandler; // 用于定时扫描短信的Handler
    private Runnable smsScanRunnable; // 定时扫描短信任务
    private static final int SMS_SCAN_INTERVAL = 1 * 30 * 1000; // 短信扫描间隔（30秒，单位：毫秒）
    private Handler smsTriggerScanHandler; // 用于短信触发扫描的Handler
    private Runnable smsTriggerScanRunnable; // 短信触发扫描任务
    private static final long SMS_DELAY_SCAN_INTERVAL = 3000; // 短信延迟扫描间隔（3秒，单位：毫秒）
    
    // 用于监听屏幕关闭和开启广播
    private BroadcastReceiver screenStateReceiver;
    
    // 记录上次切换到高优先级通知的时间
    private long lastHighPriorityTime = 0;
    
    // 当前是否使用高优先级通知
    private boolean isUsingHighPriorityNotification = false;
    
    // 守护进程服务的绑定连接
    private ServiceConnection mGuardianServiceConnection = null;
    // 守护进程AIDL接口实例
    private IKeepAliveAidlInterface mGuardianServiceAidl = null;
    // 用于检查守护进程是否存活的Handler
    private Handler mCheckHandler = new Handler();
    // 检查间隔（毫秒）
    private static final long CHECK_INTERVAL = 5000;
    // 上次成功连接守护进程的时间
    private long mLastGuardianConnectTime = 0;
    // 守护进程服务是否已绑定的标志
    private boolean mIsGuardianServiceBound = false;

    @Override
    public void onCreate() {
        super.onCreate();
        LogUtil.d(TAG, "前台服务创建");

        // 初始化电源管理器和WakeLock
        initPowerManager();
        
        // 初始化网络状态监听
        initNetworkMonitor();
        
        // 检查并申请忽略电池优化
        checkBatteryOptimization();
        
        // 初始化屏幕状态广播接收器
        initScreenStateReceiver();

        // 初始化后台自动刷新机制
        initBackgroundRefresh();
        
        // 初始化短信扫描机制
        initSmsScan();

        // 启动前台服务
        startForeground(NotificationUtils.NOTIFICATION_ID, 
                NotificationUtils.createForegroundServiceNotification(this));

        // 首次启动时立即执行一次刷新
        handleBackgroundRefresh();
        
        // 首次启动时立即扫描一次短信
        scanAllSms();
        
        // 记录当前时间作为首次启动时间
        lastHighPriorityTime = System.currentTimeMillis();
        
        // 启动悬浮窗服务
        FloatWindowService.start(this);
        
        // 启动无声音乐播放服务以增强保活效果
        startAudioPlayerService();
        
        // 启动守护进程服务并开始监控
        startGuardianService();
        
        // 确保AlarmManager定时任务被设置
        setupAlarmManagerForRefresh();
        
        // 移除不必要的保活检查，避免循环触发
    }

    // 短信扫描动作常量
    private static final String ACTION_SCAN_SMS = "ACTION_SCAN_SMS";
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogUtil.d(TAG, "前台服务启动");
        
        // 检查是否是触发短信扫描的意图
        if (intent != null && intent.hasExtra(ACTION_SCAN_SMS)) {
            LogUtil.d(TAG, "接收到触发短信扫描的请求");
            // 触发延迟短信扫描
            triggerDelayedSmsScan();
        }
        
        // 检查是否是来自AlarmManager的刷新请求
        if (intent != null && intent.getBooleanExtra("FROM_ALARM_MANAGER", false)) {
            LogUtil.d(TAG, "接收到来自AlarmManager的刷新请求，立即执行刷新");
            // 立即执行一次刷新操作
            handleBackgroundRefresh();
        }
        
        // 返回START_STICKY，系统在内存不足杀死服务后，会尝试重建服务
        return START_STICKY;
    }

    // AIDL接口实现
    private IKeepAliveAidlInterface.Stub mBinder = new IKeepAliveAidlInterface.Stub() {
        @Override
        public boolean isProcessAlive() throws RemoteException {
            return true;
        }
        
        @Override
        public String getProcessName() throws RemoteException {
            return ":main";
        }
    };
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * 清理资源
     */
    private void cleanupResources() {
        // 释放WakeLock
        releaseWakeLock();
        
        // 注销网络监听
        if (networkCallback != null) {
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager != null) {
                try {
                    connectivityManager.unregisterNetworkCallback(networkCallback);
                    LogUtil.d(TAG, "网络状态监听已注销");
                } catch (Exception e) {
                    LogUtil.e(TAG, "注销网络监听失败: " + e.getMessage());
                }
            }
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        LogUtil.d(TAG, "前台服务销毁");
        
        // 注销屏幕状态广播接收器
        if (screenStateReceiver != null) {
            try {
                unregisterReceiver(screenStateReceiver);
                screenStateReceiver = null;
                LogUtil.d(TAG, "屏幕状态广播接收器已注销");
            } catch (Exception e) {
                LogUtil.e(TAG, "注销屏幕状态广播接收器失败", e);
            }
        }
        
        // 关闭1像素Activity
        OnePixelActivity.finish(this);
        
        // 停止悬浮窗服务
        FloatWindowService.stop(this);
        
        // 停止后台自动刷新
        stopBackgroundRefresh();
        
        // 停止短信扫描
        stopSmsScan();
        
        // 停止无声音乐播放服务
        stopAudioPlayerService();
        
        // 清理守护进程监控
        cleanupGuardianMonitoring();
        
        // 清理其他资源
        cleanupResources();

        // 尝试使用多种方式重启服务
        // 1. 直接重启服务
        try {
            Intent restartServiceIntent = new Intent(getApplicationContext(), SmsForwardService.class);
            if (Build.VERSION.SDK_INT < 26) { // 26对应Android 8.0(O)
                // Android 8.0以下可以直接重启
                startService(restartServiceIntent);
            } else {
                // Android 8.0及以上需要使用前台服务启动方式
                startForegroundService(restartServiceIntent);
            }
            LogUtil.d(TAG, "服务重启尝试完成");
        } catch (Exception e) {
            LogUtil.e(TAG, "服务直接重启失败: " + e.getMessage());
            
            // 2. 如果直接重启失败，尝试使用AlarmManager延迟重启
            try {
                android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
                Intent alarmIntent = new Intent(getApplicationContext(), SystemBroadcastReceiver.class);
                alarmIntent.setAction("com.funshion.funautosend.SERVICE_RESTART");
                android.app.PendingIntent pendingIntent = android.app.PendingIntent.getBroadcast(
                        getApplicationContext(),
                        0,
                        alarmIntent,
                        Build.VERSION.SDK_INT >= 23 ? android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE : android.app.PendingIntent.FLAG_UPDATE_CURRENT
                );
                
                // 设置10秒后重启服务
                long triggerAtMillis = System.currentTimeMillis() + 10000;
                if (Build.VERSION.SDK_INT >= 23) {
                    alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                } else if (Build.VERSION.SDK_INT >= 19) {
                    alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                } else {
                    alarmManager.set(android.app.AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                }
                LogUtil.d(TAG, "已设置AlarmManager延迟重启服务");
            } catch (Exception ex) {
                LogUtil.e(TAG, "AlarmManager重启服务失败: " + ex.getMessage());
            }
        }
        
        // 注意：在onDestroy中不要停止WorkManager保活机制，让它继续运行以重启服务
    }

    /**
     * 初始化屏幕状态广播接收器
     */
    private void initScreenStateReceiver() {
        screenStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    // 屏幕关闭时，启动1像素Activity
                    LogUtil.d(TAG, "屏幕关闭，启动1像素Activity");
                    OnePixelActivity.start(context);
                    
                    // 切换到高优先级通知
                    switchToHighPriorityNotification();
                    
                    // 确保悬浮窗服务运行
                    FloatWindowService.start(context);
                } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    // 屏幕开启时，关闭1像素Activity
                    LogUtil.d(TAG, "屏幕开启，关闭1像素Activity");
                    OnePixelActivity.finish(context);
                    
                    // 切换回普通优先级通知
                    switchToNormalPriorityNotification();
                    
                    // 保持悬浮窗服务运行
                    FloatWindowService.start(context);
                }
            }
        };
        
        // 注册广播接收器
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenStateReceiver, filter);
        LogUtil.d(TAG, "屏幕状态广播接收器初始化完成");
    }

    /**
     * 初始化电源管理器和WakeLock
     */
    private void initPowerManager() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            // 使用PARTIAL_WAKE_LOCK确保CPU在设备休眠时仍能工作
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG + ":WakeLock");
                    LogUtil.d(TAG, "WakeLock初始化完成");
        }
    }
    
    /**
     * 初始化网络状态监听
     */
    private void initNetworkMonitor() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    super.onAvailable(network);
                    isNetworkAvailable = true;
                    LogUtil.d(TAG, "网络连接可用");
                    // 网络恢复时立即尝试刷新
                    if (refreshHandler != null) {
                        refreshHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                handleBackgroundRefresh();
                            }
                        });
                    }
                }
                
                @Override
                public void onLost(Network network) {
                    super.onLost(network);
                    isNetworkAvailable = false;
                    LogUtil.d(TAG, "网络连接丢失");
                }
            };
            
            // 请求网络监听
            NetworkRequest networkRequest = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
            LogUtil.d(TAG, "网络状态监听初始化完成");
        }
    }
    
    /**
     * 检查并申请忽略电池优化
     */
    private void checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            String packageName = getPackageName();
            
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
                LogUtil.d(TAG, "应用未被忽略电池优化，尝试请求");
                // 这里可以选择启动一个Activity来引导用户设置，或者记录日志让用户知道需要手动设置
                // 由于在Service中无法直接启动Activity，我们记录日志供开发者参考
                LogUtil.w(TAG, "请在系统设置中为应用设置忽略电池优化：设置 -> 电池 -> 电池优化 -> 不允许 -> 找到应用并设置为允许");
            } else {
                LogUtil.d(TAG, "应用已被忽略电池优化");
            }
        }
    }
    
    /**
     * 获取并持有WakeLock
     */
    private void acquireWakeLock() {
        if (wakeLock != null && !wakeLock.isHeld()) {
            try {
                wakeLock.acquire(WAKELOCK_TIMEOUT);
                LogUtil.d(TAG, "WakeLock已获取");
            } catch (Exception e) {
                LogUtil.e(TAG, "获取WakeLock失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 释放WakeLock
     */
    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
                LogUtil.d(TAG, "WakeLock已释放");
            } catch (Exception e) {
                LogUtil.e(TAG, "释放WakeLock失败: " + e.getMessage());
            }
        }
    }
    
    // 刷新相关常量
    private static final String ACTION_REFRESH_DATA = "com.funshion.funautosend.ACTION_REFRESH_DATA";
    
    /**
     * 初始化后台自动刷新机制
     */
    private void initBackgroundRefresh() {
        // 先停止可能存在的刷新线程
        stopBackgroundRefresh();
        
        // 创建HandlerThread以避免在主线程上执行长时间操作
        refreshHandlerThread = new HandlerThread("RefreshHandlerThread");
        // 设置线程优先级为最高
        refreshHandlerThread.setPriority(Thread.MAX_PRIORITY);
        refreshHandlerThread.start();
        
        // 使用HandlerThread的Looper创建Handler，避免内存泄漏
        refreshHandler = new Handler(refreshHandlerThread.getLooper());
        
        // 创建定时刷新任务
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    // 执行后台刷新操作
                    handleBackgroundRefresh();
                    
                    // 检查是否需要切换到高优先级通知
                    checkAndSwitchToHighPriorityNotification();
                } catch (Exception e) {
                    LogUtil.e(TAG, "刷新任务执行异常: " + e.getMessage(), e);
                } finally {
                    // 即使发生异常，也要确保安排下一次刷新
                    try {
                        if (refreshHandler != null) {
                            LogUtil.d(TAG, "安排下一次刷新，间隔: " + (REFRESH_INTERVAL / 1000) + "秒");
                            refreshHandler.postDelayed(this, REFRESH_INTERVAL);
                        }
                        // 同时设置AlarmManager作为备用机制
                        setupAlarmManagerForRefresh();
                    } catch (Exception e) {
                        LogUtil.e(TAG, "安排下一次刷新任务失败: " + e.getMessage(), e);
                    }
                }
            }
        };
        
        // 设置AlarmManager作为可靠的定时机制
        setupAlarmManagerForRefresh();
        
        // 启动定时刷新
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL);
        LogUtil.d(TAG, "后台自动刷新机制初始化完成，刷新间隔: " + (REFRESH_INTERVAL / 1000) + "秒");
    }
    
    /**
     * 设置AlarmManager用于定时刷新，确保在设备休眠时也能正常触发
     */
    private void setupAlarmManagerForRefresh() {
        try {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                Intent intent = new Intent(this, SystemBroadcastReceiver.class);
                intent.setAction(ACTION_REFRESH_DATA);
                
                int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags |= android.app.PendingIntent.FLAG_IMMUTABLE;
                }
                
                android.app.PendingIntent pendingIntent = android.app.PendingIntent.getBroadcast(this, 0, intent, flags);
                
                // 取消之前可能存在的闹钟
                alarmManager.cancel(pendingIntent);
                
                long triggerAtMillis = System.currentTimeMillis() + REFRESH_INTERVAL;
                
                // 根据Android版本选择不同的设置方式，确保在Doze模式下也能触发
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // Android 6.0及以上，使用setExactAndAllowWhileIdle确保在Doze模式下也能触发
                    alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                    LogUtil.d(TAG, "已设置AlarmManager(Doze兼容)用于定时刷新");
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    // Android 4.4及以上，使用setExact保证精确性
                    alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                    LogUtil.d(TAG, "已设置AlarmManager(setExact)用于定时刷新");
                } else {
                    // 早期版本使用普通的set方法
                    alarmManager.set(android.app.AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                    LogUtil.d(TAG, "已设置AlarmManager用于定时刷新");
                }
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "设置AlarmManager定时刷新失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 处理后台自动刷新操作
     */
    private void handleBackgroundRefresh() {
        LogUtil.d(TAG, "开始后台刷新任务");
        
        // 获取本机电量信息并打印，不影响正常刷新逻辑
        int batteryPercentage = PermissionHelper.getBatteryLevel(this);
        
        // 获取WakeLock确保在刷新过程中CPU不会休眠
        acquireWakeLock();
        
        try {
            // 检查是否有网络权限
            if (PermissionHelper.hasPermission(this, android.Manifest.permission.INTERNET)) {
                // 检查网络状态
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkCapabilities capabilities = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Network activeNetwork = cm.getActiveNetwork();
                    if (activeNetwork != null) {
                        capabilities = cm.getNetworkCapabilities(activeNetwork);
                    }
                }
                
                if (capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    LogUtil.d(TAG, "网络可用，执行刷新请求");
                    
                    // 从服务器获取最新的配置数据，传入电量参数
                            ApiClient.fetchApiData(SmsForwardService.this, batteryPercentage, new ApiClient.ApiCallback() {
                        @Override
                        public void onSuccess(String result) {
                            LogUtil.d(TAG, "后台刷新成功: 获取到最新配置数据");
                                
                            // 解析API结果
                            String savedPhone1 = PreferencesHelper.getPhoneNumber1(SmsForwardService.this);
                            String savedPhone2 = PreferencesHelper.getPhoneNumber2(SmsForwardService.this);
                            ApiClient.ParseResult parseResult = ApiClient.parseApiResult(result, savedPhone1, savedPhone2);
                            
                            // 保存解析结果到本地存储
                            PreferencesHelper.saveApiResultList(SmsForwardService.this, parseResult.getApiResultList());
                            PreferencesHelper.saveTargetList(SmsForwardService.this, parseResult.getTargetList());
                            
                            LogUtil.d(TAG, "解析结果 - 目标列表数量: " + parseResult.getTargetList().size());
                            LogUtil.d(TAG, "数据已保存到本地存储");
                            
                            // 刷新成功后更新通知，保持服务活跃
                            String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
                            updateServiceNotification("数据刷新成功 - " + time);
                            
                            // 确保AlarmManager定时任务继续运行
                            setupAlarmManagerForRefresh();
                        }
                        
                        @Override
                        public void onFailure(String error) {
                            LogUtil.e(TAG, "后台刷新失败: " + error);
                            // 失败时记录日志并尝试再次刷新
                            // 使用AlarmManager确保即使在休眠状态也能唤醒系统执行刷新
                            scheduleRetryRefresh();
                        }
                    });
                } else {
                    LogUtil.w(TAG, "网络不可用，无法执行后台刷新");
                    // 网络不可用时，使用AlarmManager安排重试
                    scheduleRetryRefresh();
                }
            } else {
                LogUtil.w(TAG, "没有网络权限，无法执行后台刷新");
                // 没有网络权限也尝试更新通知，保持服务活跃
                String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
                updateServiceNotification("数据刷新成功 - " + time);
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "后台刷新任务失败: " + e.getMessage(), e);
            // 发生异常时也尝试重试
            scheduleRetryRefresh();
        } finally {
            // 确保释放WakeLock
            releaseWakeLock();
        }
        LogUtil.d(TAG, "后台刷新任务完成");
    }
    
    /**
     * 更新服务通知，保持服务在后台的活跃状态
     */
    private void updateServiceNotification(String content) {
        try {
            Notification notification = NotificationUtils.updateForegroundServiceNotification(this, content);
            startForeground(NotificationUtils.NOTIFICATION_ID, notification);
                LogUtil.d(TAG, "服务通知已更新");
        } catch (Exception e) {
                LogUtil.e(TAG, "更新服务通知失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 使用AlarmManager安排重试刷新
     */
    private void scheduleRetryRefresh() {
        try {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                Intent intent = new Intent(this, SystemBroadcastReceiver.class);
                intent.setAction("com.funshion.funautosend.ACTION_REFRESH_RETRY");
                
                int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags |= android.app.PendingIntent.FLAG_IMMUTABLE;
                }
                
                android.app.PendingIntent pendingIntent = android.app.PendingIntent.getBroadcast(this, 0, intent, flags);
                
                long triggerAtMillis = System.currentTimeMillis() + NETWORK_RETRY_DELAY;
                
                // 使用setExactAndAllowWhileIdle确保在Doze模式下也能触发
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                } else {
                    alarmManager.set(android.app.AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                }
                
                LogUtil.d(TAG, "已使用AlarmManager安排刷新重试");
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "安排刷新重试失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查并切换到高优先级通知
     */
    private void checkAndSwitchToHighPriorityNotification() {
        long currentTime = System.currentTimeMillis();
        // 如果距离上次切换到高优先级通知已经超过指定时间，并且当前不是高优先级通知，则切换
        if (currentTime - lastHighPriorityTime >= HIGH_PRIORITY_INTERVAL && !isUsingHighPriorityNotification) {
            switchToHighPriorityNotification();
            // 30秒后切换回普通通知
            scheduleSwitchBackToNormalNotification(30000);
        }
    }

    /**
     * 切换到高优先级通知
     */
    private void switchToHighPriorityNotification() {
        if (isUsingHighPriorityNotification) return;
        
        LogUtil.d(TAG, "切换到高优先级通知");
        try {
            // 先停止前台服务
            stopForeground(true);
            // 然后使用高优先级通知重新启动前台服务
            startForeground(NotificationUtils.HIGH_PRIORITY_NOTIFICATION_ID, 
                    NotificationUtils.createHighPriorityForegroundServiceNotification(this));
            isUsingHighPriorityNotification = true;
            lastHighPriorityTime = System.currentTimeMillis();
        } catch (Exception e) {
            LogUtil.e(TAG, "切换到高优先级通知失败", e);
        }
    }

    /**
     * 切换回普通优先级通知
     */
    // 电池电量获取逻辑已移至PermissionHelper类中
    // 调用方式: PermissionHelper.getBatteryLevel(context)
    
    private void switchToNormalPriorityNotification() {
        if (!isUsingHighPriorityNotification) return;
        
        LogUtil.d(TAG, "切换回普通优先级通知");
        try {
            // 先停止前台服务
            stopForeground(true);
            // 然后使用普通优先级通知重新启动前台服务
            startForeground(NotificationUtils.NOTIFICATION_ID, 
                    NotificationUtils.createForegroundServiceNotification(this));
            isUsingHighPriorityNotification = false;
        } catch (Exception e) {
            LogUtil.e(TAG, "切换回普通优先级通知失败", e);
        }
    }

    /**
     * 安排一段时间后切换回普通通知
     * @param delayMs 延迟时间（毫秒）
     */
    private void scheduleSwitchBackToNormalNotification(final long delayMs) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(delayMs);
                    // 在UI线程中执行
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            switchToNormalPriorityNotification();
                        }
                    });
                } catch (InterruptedException e) {
                    LogUtil.e(TAG, "切换回普通通知的任务被中断", e);
                    Thread.currentThread().interrupt();
                }
            }
        }).start();
    }

    /**
     * 停止后台自动刷新
     */
    private void stopBackgroundRefresh() {
        // 取消AlarmManager闹钟
        try {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                Intent intent = new Intent(this, SystemBroadcastReceiver.class);
                intent.setAction(ACTION_REFRESH_DATA);
                
                int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags |= android.app.PendingIntent.FLAG_IMMUTABLE;
                }
                
                android.app.PendingIntent pendingIntent = android.app.PendingIntent.getBroadcast(this, 0, intent, flags);
                alarmManager.cancel(pendingIntent);
                LogUtil.d(TAG, "已取消AlarmManager定时刷新");
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "取消AlarmManager定时刷新失败: " + e.getMessage(), e);
        }
        
        if (refreshHandler != null) {
            // 移除所有回调和消息
            refreshHandler.removeCallbacksAndMessages(null);
            refreshHandler = null;
        }
        
        // 直接使用成员变量安全停止HandlerThread
        if (refreshHandlerThread != null) {
            try {
                refreshHandlerThread.quitSafely(); // 使用quitSafely更安全地停止线程
                refreshHandlerThread.join(2000); // 等待线程结束，最多2秒
                LogUtil.d(TAG, "刷新线程已安全停止");
            } catch (InterruptedException e) {
                LogUtil.e(TAG, "等待刷新线程结束时被中断", e);
                Thread.currentThread().interrupt();
            } finally {
                refreshHandlerThread = null;
            }
        }
        
        // 清理Runnable引用
        if (refreshRunnable != null) {
            refreshRunnable = null;
        }
        
        LogUtil.d(TAG, "后台自动刷新已停止");
    }
    
    /**
     * 初始化短信扫描机制
     */
    private void initSmsScan() {
        // 创建定时扫描Handler
        smsScanHandler = new Handler();
        
        // 创建定时扫描短信任务
        smsScanRunnable = new Runnable() {
            @Override
            public void run() {
                // 执行短信扫描操作
                scanAllSms();
                
                // 安排下一次扫描
                smsScanHandler.postDelayed(this, SMS_SCAN_INTERVAL);
            }
        };
        
        // 启动定时扫描
        smsScanHandler.postDelayed(smsScanRunnable, SMS_SCAN_INTERVAL);
        LogUtil.d(TAG, "短信扫描机制初始化完成，扫描间隔: " + (SMS_SCAN_INTERVAL / 1000) + "秒");
        
        // 初始化短信触发扫描相关
        smsTriggerScanHandler = new Handler();
        smsTriggerScanRunnable = new Runnable() {
            @Override
            public void run() {
                LogUtil.d(TAG, "执行短信触发的延迟扫描");
                scanAllSms();
            }
        };
    }
    
    /**
     * 扫描所有短信内容
     */
    private void scanAllSms() {
        LogUtil.d(TAG, "服务中执行短信扫描");
        
        // 检查是否有读取短信权限
        if (SmsHelper.hasReadSmsPermission(this)) {
            // 在后台线程中执行扫描，避免阻塞服务线程
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // 使用SmsHelper的静态方法扫描并保存短信到本地存储
                        SmsHelper.scanAndSaveSmsToStorage(SmsForwardService.this);
                    } catch (Exception e) {
                        LogUtil.e(TAG, "短信扫描过程中出现异常: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }).start();
        } else {
            LogUtil.d(TAG, "服务中没有读取短信权限，无法扫描短信");
        }
    }
    
    /**
     * 停止短信扫描
     */
    private void stopSmsScan() {
        if (smsScanHandler != null && smsScanRunnable != null) {
            smsScanHandler.removeCallbacks(smsScanRunnable);
            smsScanHandler = null;
            smsScanRunnable = null;
            LogUtil.d(TAG, "定时短信扫描已停止");
        }
        
        if (smsTriggerScanHandler != null && smsTriggerScanRunnable != null) {
            smsTriggerScanHandler.removeCallbacks(smsTriggerScanRunnable);
            smsTriggerScanHandler = null;
            smsTriggerScanRunnable = null;
            LogUtil.d(TAG, "短信触发扫描已停止");
        }
    }
    
    /**
     * 触发短信延迟扫描，3秒后执行扫描
     * 如果在3秒内多次调用，只有最后一次会生效
     */
    public void triggerDelayedSmsScan() {
        if (smsTriggerScanHandler != null && smsTriggerScanRunnable != null) {
            // 先移除之前的扫描请求，确保只执行最后一次
            smsTriggerScanHandler.removeCallbacks(smsTriggerScanRunnable);
            // 重新安排扫描请求
            smsTriggerScanHandler.postDelayed(smsTriggerScanRunnable, SMS_DELAY_SCAN_INTERVAL);
            LogUtil.d(TAG, "已安排短信延迟扫描，将在3秒后执行");
        }
    }
    
    /**
     * 启动无声音乐播放服务
     */
    private void startAudioPlayerService() {
        try {
            Intent audioServiceIntent = new Intent(this, AudioPlayerService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0及以上使用startForegroundService
                startForegroundService(audioServiceIntent);
            } else {
                // Android 8.0以下使用普通的startService
                startService(audioServiceIntent);
            }
            LogUtil.d(TAG, "无声音乐播放服务启动成功");
        } catch (Exception e) {
            LogUtil.e(TAG, "无声音乐播放服务启动失败: " + e.getMessage());
        }
    }
    
    /**
     * 停止无声音乐播放服务
     */
    private void stopAudioPlayerService() {
        try {
            Intent audioServiceIntent = new Intent(this, AudioPlayerService.class);
            stopService(audioServiceIntent);
            LogUtil.d(TAG, "无声音乐播放服务停止成功");
        } catch (Exception e) {
            LogUtil.e(TAG, "无声音乐播放服务停止失败: " + e.getMessage());
        }
    }
    
    /**
     * 启动守护进程服务并开始监控
     */
    private void startGuardianService() {
        // 启动守护进程服务
        GuardianService.start(this);
        
        // 初始化服务连接
        if (mGuardianServiceConnection == null) {
            mGuardianServiceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    LogUtil.d(TAG, "成功连接到守护进程服务");
                    mGuardianServiceAidl = IKeepAliveAidlInterface.Stub.asInterface(service);
                    mLastGuardianConnectTime = System.currentTimeMillis();
                    mIsGuardianServiceBound = true;
                }
                
                @Override
                public void onServiceDisconnected(ComponentName name) {
                    LogUtil.d(TAG, "与守护进程服务断开连接");
                    mGuardianServiceAidl = null;
                    mIsGuardianServiceBound = false;
                    // 尝试重连守护进程
                    reconnectGuardianService();
                }
                
                @Override
                public void onBindingDied(ComponentName name) {
                    LogUtil.d(TAG, "守护进程服务绑定死亡");
                    mGuardianServiceAidl = null;
                    mIsGuardianServiceBound = false;
                    // 尝试重连守护进程
                    reconnectGuardianService();
                }
            };
        }
        
        // 绑定守护进程服务
        try {
            Intent bindIntent = new Intent(this, GuardianService.class);
            bindService(bindIntent, mGuardianServiceConnection, Context.BIND_AUTO_CREATE);
            LogUtil.d(TAG, "绑定守护进程服务");
        } catch (Exception e) {
            LogUtil.e(TAG, "绑定守护进程服务失败", e);
        }
        
        // 开始定期检查守护进程是否存活
        mCheckHandler.postDelayed(mGuardianCheckRunnable, CHECK_INTERVAL);
    }
    
    /**
     * 检查守护进程是否存活的Runnable
     */
    private Runnable mGuardianCheckRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                // 检查守护进程是否存活
                if (mGuardianServiceAidl != null) {
                    boolean isAlive = mGuardianServiceAidl.isProcessAlive();
                    if (isAlive) {
                        LogUtil.d(TAG, "守护进程服务存活");
                        mLastGuardianConnectTime = System.currentTimeMillis();
                    } else {
                        LogUtil.d(TAG, "守护进程服务不存活，尝试重启");
                        reconnectGuardianService();
                    }
                } else {
                    // 如果超过10秒未连接成功，尝试重启守护进程
                    if (System.currentTimeMillis() - mLastGuardianConnectTime > 10000) {
                        LogUtil.d(TAG, "长时间未连接到守护进程服务，尝试重启");
                        reconnectGuardianService();
                    }
                }
            } catch (RemoteException e) {
                    LogUtil.d(TAG, "与守护进程服务通信异常，尝试重启");
                mGuardianServiceAidl = null;
                reconnectGuardianService();
            } finally {
                // 继续定期检查
                mCheckHandler.postDelayed(this, CHECK_INTERVAL);
            }
        }
    };
    
    /**
     * 重新连接守护进程服务
     */
    private void reconnectGuardianService() {
        try {
            // 解绑旧的连接
            if (mGuardianServiceConnection != null && mIsGuardianServiceBound) {
                try {
                    unbindService(mGuardianServiceConnection);
                    mIsGuardianServiceBound = false;
                } catch (Exception e) {
                    LogUtil.e(TAG, "解绑守护进程服务失败", e);
                    mIsGuardianServiceBound = false; // 无论如何都重置绑定状态
                }
            }
            
            // 重新启动守护进程服务
            GuardianService.start(this);
            
            // 延迟绑定，确保服务已启动
            mCheckHandler.postDelayed(() -> {
                try {
                    Intent bindIntent = new Intent(this, GuardianService.class);
                    bindService(bindIntent, mGuardianServiceConnection, Context.BIND_AUTO_CREATE);
                    LogUtil.d(TAG, "重新绑定守护进程服务");
                } catch (Exception e) {
                    LogUtil.e(TAG, "重新绑定守护进程服务失败", e);
                    mIsGuardianServiceBound = false;
                }
            }, 1000);
        } catch (Exception e) {
            LogUtil.e(TAG, "重启守护进程服务失败", e);
        }
    }
    
    /**
     * 清理守护进程监控相关资源
     */
    private void cleanupGuardianMonitoring() {
        // 移除检查任务
        mCheckHandler.removeCallbacksAndMessages(null);
        
        // 解绑守护进程服务
        if (mGuardianServiceConnection != null && mIsGuardianServiceBound) {
            try {
                unbindService(mGuardianServiceConnection);
                mIsGuardianServiceBound = false;
            } catch (Exception e) {
                    LogUtil.e(TAG, "解绑守护进程服务失败", e);
                mIsGuardianServiceBound = false; // 无论如何都重置绑定状态
            }
            mGuardianServiceConnection = null;
        }
        
        mGuardianServiceAidl = null;
    }
    
    /**
     * 在UI线程中运行代码
     * @param runnable 需要在UI线程中运行的Runnable
     */
    private void runOnUiThread(Runnable runnable) {
        if (runnable != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Android 9.0及以上，可以直接使用getMainExecutor
                getMainExecutor().execute(runnable);
            } else {
                // 早期版本，使用Handler.post
                if (refreshHandler != null) {
                    refreshHandler.post(runnable);
                }
            }
        }
    }
}