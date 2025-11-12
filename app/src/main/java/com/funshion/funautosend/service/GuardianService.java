package com.funshion.funautosend.service;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.funshion.funautosend.IKeepAliveAidlInterface;
import com.funshion.funautosend.util.KeepAliveManager;
import com.funshion.funautosend.util.NotificationUtils;

import java.util.concurrent.TimeUnit;

/**
 * 守护进程服务，运行在单独的进程中
 * 用于监控主进程服务并在其崩溃时重启
 */
public class GuardianService extends Service {
    private static final String TAG = "GuardianService";
    private static final String PROCESS_NAME = ":guardian";
    
    // 主进程服务的绑定连接
    private ServiceConnection mMainServiceConnection = null;
    // AIDL接口实例
    private IKeepAliveAidlInterface mMainServiceAidl = null;
    // 用于检查主服务是否存活的Handler
    private Handler mCheckHandler = new Handler();
    // 检查间隔（毫秒）
    private static final long CHECK_INTERVAL = 5000;
    // 上次成功连接时间
    private long mLastConnectTime = 0;
    // 服务是否已绑定的标志
    private boolean mIsServiceBound = false;
    
    // AIDL接口实现
    private IKeepAliveAidlInterface.Stub mBinder = new IKeepAliveAidlInterface.Stub() {
        @Override
        public boolean isProcessAlive() throws RemoteException {
            return true;
        }
        
        @Override
        public String getProcessName() throws RemoteException {
            return PROCESS_NAME;
        }
    };
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "守护进程服务创建");
        
        // 启动前台服务，使用完全独立的通知ID避免与主服务冲突
        startForeground(NotificationUtils.HIGH_PRIORITY_NOTIFICATION_ID + 1, 
                NotificationUtils.createForegroundServiceNotification(this));
        
        // 开始监控主进程服务
        startMonitoringMainService();
        
        // 移除不必要的保活检查，避免循环触发
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "守护进程服务启动");
        
        return START_STICKY;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "守护进程服务销毁");
        
        // 移除检查任务
        mCheckHandler.removeCallbacksAndMessages(null);
        
        // 解绑主服务
        if (mMainServiceConnection != null && mIsServiceBound) {
            try {
                unbindService(mMainServiceConnection);
                mIsServiceBound = false;
            } catch (Exception e) {
                Log.e(TAG, "解绑主服务失败", e);
            }
        }
        
        // 尝试重启自己
        try {
            Intent restartIntent = new Intent(this, GuardianService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restartIntent);
            } else {
                startService(restartIntent);
            }
            Log.d(TAG, "尝试重启守护进程服务");
        } catch (Exception e) {
            Log.e(TAG, "重启守护进程服务失败", e);
        }
        
        // 不再在销毁时触发保活检查，避免循环重启
    }
    
    /**
     * 开始监控主进程服务
     */
    private void startMonitoringMainService() {
        // 初始化服务连接
        if (mMainServiceConnection == null) {
            mMainServiceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    Log.d(TAG, "成功连接到主进程服务");
                    mMainServiceAidl = IKeepAliveAidlInterface.Stub.asInterface(service);
                    mLastConnectTime = System.currentTimeMillis();
                    mIsServiceBound = true;
                }
                
                @Override
                public void onServiceDisconnected(ComponentName name) {
                    Log.d(TAG, "与主进程服务断开连接");
                    mMainServiceAidl = null;
                    mIsServiceBound = false;
                    // 尝试重连并重启主服务
                    reconnectAndRestartMainService();
                }
                
                @Override
                public void onBindingDied(ComponentName name) {
                    Log.d(TAG, "主进程服务绑定死亡");
                    mMainServiceAidl = null;
                    mIsServiceBound = false;
                    // 尝试重连并重启主服务
                    reconnectAndRestartMainService();
                }
            };
        }
        
        // 开始定期检查任务
        mCheckHandler.postDelayed(mCheckRunnable, CHECK_INTERVAL);
    }
    
    /**
     * 检查主服务是否存活的Runnable
     */
    private Runnable mCheckRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                // 检查主服务是否存活
                if (mMainServiceAidl != null) {
                    boolean isAlive = mMainServiceAidl.isProcessAlive();
                    if (isAlive) {
                        Log.d(TAG, "主进程服务存活");
                        mLastConnectTime = System.currentTimeMillis();
                    } else {
                        Log.d(TAG, "主进程服务不存活，尝试重启");
                        reconnectAndRestartMainService();
                    }
                } else {
                    // 如果超过10秒未连接成功，尝试重启主服务
                    if (System.currentTimeMillis() - mLastConnectTime > 10000) {
                        Log.d(TAG, "长时间未连接到主进程服务，尝试重启");
                        reconnectAndRestartMainService();
                    }
                }
            } catch (RemoteException e) {
                Log.d(TAG, "与主进程服务通信异常，尝试重启");
                mMainServiceAidl = null;
                reconnectAndRestartMainService();
            } finally {
                // 继续定期检查
                mCheckHandler.postDelayed(this, CHECK_INTERVAL);
            }
        }
    };
    
    /**
     * 重连并重启主服务
     */
    private void reconnectAndRestartMainService() {
        try {
            // 解绑旧的连接
            if (mMainServiceConnection != null && mIsServiceBound) {
                try {
                    unbindService(mMainServiceConnection);
                    mIsServiceBound = false;
                } catch (Exception e) {
                    Log.e(TAG, "解绑主服务失败", e);
                    mIsServiceBound = false; // 无论如何都重置绑定状态
                }
            }
            
            // 启动主服务
            Intent mainServiceIntent = new Intent(this, SmsForwardService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(mainServiceIntent);
            } else {
                startService(mainServiceIntent);
            }
            Log.d(TAG, "尝试重启主进程服务");
            
            // 延迟绑定，确保服务已启动
            mCheckHandler.postDelayed(() -> {
                try {
                    Intent bindIntent = new Intent(this, SmsForwardService.class);
                    bindService(bindIntent, mMainServiceConnection, Context.BIND_AUTO_CREATE);
                    Log.d(TAG, "尝试绑定主进程服务");
                } catch (Exception e) {
                    Log.e(TAG, "绑定主进程服务失败", e);
                    mIsServiceBound = false;
                }
            }, 1000);
        } catch (Exception e) {
            Log.e(TAG, "重启主进程服务失败", e);
        }
    }
    
    /**
     * 启动守护进程服务
     */
    public static void start(Context context) {
        try {
            Intent intent = new Intent(context, GuardianService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            Log.d(TAG, "启动守护进程服务");
        } catch (Exception e) {
            Log.e(TAG, "启动守护进程服务失败", e);
        }
    }
    
    /**
     * 停止守护进程服务
     */
    public static void stop(Context context) {
        try {
            Intent intent = new Intent(context, GuardianService.class);
            context.stopService(intent);
            Log.d(TAG, "停止守护进程服务");
        } catch (Exception e) {
            Log.e(TAG, "停止守护进程服务失败", e);
        }
    }
}