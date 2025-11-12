package com.funshion.funautosend.service;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import com.funshion.funautosend.util.LogUtil;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.funshion.funautosend.R;
import com.funshion.funautosend.MainActivity;
import com.funshion.funautosend.util.NotificationUtils;
import com.funshion.funautosend.util.FloatWindowPermissionHelper;
import com.funshion.funautosend.util.KeepAliveManager;

/**
 * 悬浮窗口服务，用于在应用后台运行时显示一个可交互的悬浮弹窗
 * 类似微信视频/语音通话时的悬浮弹窗效果
 */
public class FloatWindowService extends Service {

    private static final String TAG = "FloatWindowService";
    private static final int FLOAT_WINDOW_NOTIFICATION_ID = 1003;
    private static final int ALARM_INTERVAL = 5 * 60 * 1000; // 5分钟
    
    private WindowManager windowManager;
    private View floatView;
    private WindowManager.LayoutParams params;
    private float initialX;
    private float initialY;
    private int initialTouchX;
    private int initialTouchY;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        LogUtil.d(TAG, "悬浮窗服务创建");
        
        // 初始化悬浮窗
        initFloatWindow();
        
        // 启动前台服务，提高服务优先级
        startForeground(FLOAT_WINDOW_NOTIFICATION_ID, 
                NotificationUtils.createHighPriorityForegroundServiceNotification(this));
        
        // 启动定时检查机制
        startAlarmManager();
        
        // 触发WorkManager立即保活检查
        KeepAliveManager.getInstance(this).getWorkManagerKeepAliveHelper().runKeepAliveCheckNow();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogUtil.d(TAG, "悬浮窗服务启动");
        
        // 确保悬浮窗已显示
        showFloatWindow();
        
        // 再次触发WorkManager保活检查
        KeepAliveManager.getInstance(this).getWorkManagerKeepAliveHelper().runKeepAliveCheckNow();
        
        // START_REDELIVER_INTENT 比 START_STICKY 优先级更高，系统会尽力保证服务重启
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LogUtil.d(TAG, "悬浮窗服务销毁");
        
        // 移除悬浮窗
        removeFloatWindow();
        
        // 取消AlarmManager
        cancelAlarmManager();
        
        // 尝试自我重启服务
        restartService();
        
        // 触发WorkManager保活检查作为最终保障
        KeepAliveManager.getInstance(this).getWorkManagerKeepAliveHelper().runKeepAliveCheckNow();
    }

    /**
     * 初始化悬浮窗
     */
    @SuppressLint("InflateParams")
    private void initFloatWindow() {
        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            
            if (windowManager == null) {
                LogUtil.e(TAG, "获取WindowManager服务失败");
                return;
            }
            
            // 加载微信风格的悬浮窗布局
            LayoutInflater inflater = LayoutInflater.from(this);
            if (inflater == null) {
                LogUtil.e(TAG, "LayoutInflater初始化失败");
                return;
            }
            
            floatView = inflater.inflate(R.layout.layout_float_window_chat_style, null);
            
            if (floatView == null) {
                LogUtil.e(TAG, "加载悬浮窗布局失败");
                return;
            }
            
            // 设置悬浮窗参数
            params = new WindowManager.LayoutParams();
        } catch (Exception e) {
            LogUtil.e(TAG, "初始化悬浮窗时发生异常: " + e.getMessage());
            floatView = null;
            params = null;
        }
        
        if (params == null) {
            LogUtil.e(TAG, "悬浮窗参数初始化失败，无法继续设置参数");
            return;
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8.0及以上需要使用TYPE_APPLICATION_OVERLAY
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            // Android 8.0以下使用TYPE_PHONE
            params.type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        
        // 设置悬浮窗的行为
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON; // 保持屏幕唤醒
        
        // 设置悬浮窗的格式
        params.format = PixelFormat.RGBA_8888;
        
        // 设置悬浮窗的位置和大小
        params.gravity = Gravity.START | Gravity.TOP;
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.x = 100; // 初始x坐标
        params.y = 300; // 初始y坐标
        
        // 获取关闭按钮引用
        final ImageView closeButton = floatView.findViewById(R.id.close_button);
        
        // 设置悬浮窗的触摸事件（处理拖动和点击）
        floatView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // 检查事件是否发生在关闭按钮上
                if (closeButton != null && event.getAction() == MotionEvent.ACTION_DOWN) {
                    // 获取关闭按钮的位置信息
                    int[] location = new int[2];
                    closeButton.getLocationOnScreen(location);
                    int btnX = location[0];
                    int btnY = location[1];
                    int btnWidth = closeButton.getWidth();
                    int btnHeight = closeButton.getHeight();
                    
                    // 获取触摸点相对于屏幕的坐标
                    float touchX = event.getRawX();
                    float touchY = event.getRawY();
                    
                    // 判断是否点击了关闭按钮
                    if (touchX >= btnX && touchX <= btnX + btnWidth && 
                        touchY >= btnY && touchY <= btnY + btnHeight) {
                        // 点击了关闭按钮，不进行拖动处理
                        return false;
                    }
                }
                
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // 记录手指按下的位置
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = (int) event.getRawX();
                        initialTouchY = (int) event.getRawY();
                        return true; // 返回true以便接收后续的MOVE和UP事件
                    case MotionEvent.ACTION_MOVE:
                        // 计算手指移动的距离
                        float dx = event.getRawX() - initialTouchX;
                        float dy = event.getRawY() - initialTouchY;
                        // 更新悬浮窗的位置
                        params.x = (int) (initialX + dx);
                        params.y = (int) (initialY + dy);
                        // 刷新悬浮窗位置
                        windowManager.updateViewLayout(floatView, params);
                        return true; // 消费移动事件，防止触发点击
                    case MotionEvent.ACTION_UP:
                        // 如果移动距离很小，视为点击事件
                        float moveDistance = (float) Math.sqrt(
                                Math.pow(event.getRawX() - initialTouchX, 2) +
                                        Math.pow(event.getRawY() - initialTouchY, 2)
                        );
                        if (moveDistance < 10) {
                            // 点击悬浮窗，打开主应用
                            openMainApp();
                        }
                        return true; // 返回true保持事件处理一致性，确保下次滑动正常工作
                }
                return false;
            }
        });
        
        // 单独设置关闭按钮的点击事件，保持原有功能
        if (closeButton != null) {
            closeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // 关闭悬浮窗
                    stopSelf();
                }
            });
        }
        
        // 设置标题和内容
        TextView titleView = floatView.findViewById(R.id.float_window_title);
        TextView contentView = floatView.findViewById(R.id.float_window_content);
        if (titleView != null) {
            titleView.setText("Fun自动转发");
        }
        if (contentView != null) {
            contentView.setText("正在后台运行中");
        }
    }

    /**
     * 显示悬浮窗
     */
    private void showFloatWindow() {
        try {
            // 检查是否有悬浮窗权限
            if (!FloatWindowPermissionHelper.hasFloatWindowPermission(this)) {
                LogUtil.w(TAG, "没有悬浮窗权限，无法显示悬浮窗");
                return;
            }
            
            // 确保windowManager已初始化
            if (windowManager == null) {
                windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
                if (windowManager == null) {
                    LogUtil.e(TAG, "WindowManager初始化失败");
                    return;
                }
            }
            
            // 确保浮窗视图已初始化
            if (floatView == null) {
                initFloatWindow();
                if (floatView == null) {
                    LogUtil.e(TAG, "浮窗视图初始化失败");
                    return;
                }
            }
            
            // 确保params已初始化
            if (params == null) {
                LogUtil.e(TAG, "LayoutParams未初始化");
                return;
            }
            
            try {
                // 移除已存在的悬浮窗（使用安全的移除方法）
                try {
                    if (floatView != null && windowManager != null) {
                        windowManager.removeViewImmediate(floatView);
                    }
                } catch (Exception ignored) {
                    // 忽略移除失败的异常
                }
                
                // 再次检查floatView是否为null（防止移除过程中被设为null）
                if (floatView == null) {
                    LogUtil.e(TAG, "浮窗视图在移除后变为null");
                    initFloatWindow();
                    if (floatView == null) {
                        LogUtil.e(TAG, "浮窗视图重新初始化失败");
                        return;
                    }
                }
                
                // 添加新的悬浮窗
                windowManager.addView(floatView, params);
                LogUtil.d(TAG, "悬浮窗显示成功");
            } catch (SecurityException se) {
                // 处理安全异常，这可能是由于锁屏后系统限制了后台应用显示悬浮窗
                LogUtil.e(TAG, "显示悬浮窗安全异常: " + se.getMessage() + ", 将尝试使用前台方式重新添加");
                
                // 重新设置浮窗参数，确保使用合适的类型
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
                } else {
                    params.type = WindowManager.LayoutParams.TYPE_PHONE;
                }
                
                // 尝试再次添加悬浮窗
                try {
                    if (floatView != null && windowManager != null) {
                        windowManager.addView(floatView, params);
                        LogUtil.d(TAG, "悬浮窗使用备用参数添加成功");
                    }
                } catch (Exception e2) {
                    LogUtil.e(TAG, "尝试备用参数添加悬浮窗也失败: " + e2.getMessage());
                    // 安排延迟重试
                    scheduleShowFloatWindowRetry();
                }
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "显示悬浮窗失败: " + e.getMessage());
            // 打印详细的堆栈信息，帮助调试
            e.printStackTrace();
            // 重置floatView，避免使用损坏的视图
            floatView = null;
            // 失败时尝试使用AlarmManager重启服务
            scheduleRestartService();
        }
    }
    
    /**
     * 安排延迟重试显示悬浮窗
     */
    private void scheduleShowFloatWindowRetry() {
        LogUtil.d(TAG, "安排延迟重试显示悬浮窗");
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                LogUtil.d(TAG, "执行延迟重试显示悬浮窗");
                showFloatWindow();
            }
        }, 1000); // 延迟1秒后重试
    }

    /**
     * 移除悬浮窗
     */
    private void removeFloatWindow() {
        try {
            if (floatView != null && windowManager != null) {
                try {
                    windowManager.removeViewImmediate(floatView);
                } catch (IllegalArgumentException iae) {
                    // 处理视图未附加到窗口管理器的异常
                    LogUtil.w(TAG, "视图未附加到窗口管理器: " + iae.getMessage());
                } finally {
                    // 无论移除是否成功，都将floatView设为null，避免重复使用
                    floatView = null;
                }
                LogUtil.d(TAG, "悬浮窗移除成功");
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "移除悬浮窗失败: " + e.getMessage());
            // 确保即使出现异常，floatView也会被重置
            floatView = null;
        }
    }

    /**
     * 打开主应用
     */
    private void openMainApp() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    /**
     * 启动悬浮窗服务
     * @param context 上下文
     */
    public static void start(Context context) {
        // 检查是否有悬浮窗权限
        if (!FloatWindowPermissionHelper.hasFloatWindowPermission(context)) {
            LogUtil.w(TAG, "没有悬浮窗权限，无法启动悬浮窗服务");
            return;
        }
        
        Intent intent = new Intent(context, FloatWindowService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }
    
    /**
     * 启动AlarmManager定时检查
     */
    private void startAlarmManager() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent intent = new Intent(FloatWindowRestartReceiver.ACTION_RESTART_FLOAT_WINDOW_SERVICE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        
        // 设置定时任务，每5分钟检查一次服务状态
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + ALARM_INTERVAL,
                    pendingIntent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + ALARM_INTERVAL,
                    pendingIntent);
        } else {
            alarmManager.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + ALARM_INTERVAL,
                    pendingIntent);
        }
    }
    
    /**
     * 取消AlarmManager
     */
    private void cancelAlarmManager() {
        try {
            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            Intent intent = new Intent(FloatWindowRestartReceiver.ACTION_RESTART_FLOAT_WINDOW_SERVICE);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            alarmManager.cancel(pendingIntent);
        } catch (Exception e) {
            LogUtil.e(TAG, "取消AlarmManager失败: " + e.getMessage());
        }
    }
    
    /**
     * 尝试自我重启服务
     */
    private void restartService() {
        LogUtil.d(TAG, "尝试自我重启服务");
        // 立即重启
        start(this);
        // 同时安排延迟重启，以防立即重启失败
        scheduleRestartService();
    }
    
    /**
     * 安排延迟重启服务
     */
    private void scheduleRestartService() {
        LogUtil.d(TAG, "安排延迟重启服务");
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent intent = new Intent(FloatWindowRestartReceiver.ACTION_RESTART_FLOAT_WINDOW_SERVICE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        
        // 设置10秒后重启服务
        long delayMillis = 10000;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + delayMillis,
                    pendingIntent);
        } else {
            alarmManager.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + delayMillis,
                    pendingIntent);
        }
    }

    /**
     * 停止悬浮窗服务
     * @param context 上下文
     */
    public static void stop(Context context) {
        Intent intent = new Intent(context, FloatWindowService.class);
        context.stopService(intent);
    }
}