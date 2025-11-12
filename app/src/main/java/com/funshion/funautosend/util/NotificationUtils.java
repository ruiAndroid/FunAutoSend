package com.funshion.funautosend.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
// 移除未使用的Log导入

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.core.app.NotificationCompat;

import com.funshion.funautosend.MainActivity;
import com.funshion.funautosend.R;
import com.funshion.funautosend.service.SmsForwardService;

/**
 * 通知工具类，用于创建和管理前台服务的通知
 */
public class NotificationUtils {

    public static final String CHANNEL_ID = "fun_forwarding_channel";
    public static final String CHANNEL_NAME = "Fun自动转发服务";
    public static final String HIGH_PRIORITY_CHANNEL_ID = "fun_forwarding_high_priority";
    public static final String HIGH_PRIORITY_CHANNEL_NAME = "Fun自动转发服务-重要";
    public static final int NOTIFICATION_ID = 1001;
    public static final int HIGH_PRIORITY_NOTIFICATION_ID = 1002;
    public static final String ACTION_STOP_SERVICE = "com.funshion.funautosend.ACTION_STOP_SERVICE";
    public static final String ACTION_OPEN_APP = "com.funshion.funautosend.ACTION_OPEN_APP";

    /**
     * 创建前台服务通知
     * @param context 上下文
     * @return 通知对象
     */
    public static Notification createForegroundServiceNotification(Context context) {
        // 创建通知渠道（Android 8.0+必需）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(context);
        }

        // 创建通知意图，点击通知时打开MainActivity
        Intent intent = new Intent(context, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        // Android 12+需要添加IMMUTABLE标志
        if (Build.VERSION.SDK_INT >= 31) { // 31对应Android 12(S)
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, flags);

        // 创建停止服务的意图
        Intent stopIntent = new Intent(context, SmsForwardService.class);
        stopIntent.setAction(ACTION_STOP_SERVICE);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                context, 
                1, 
                stopIntent, 
                flags
        );

        // 构建通知
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Fun自动转发服务")
                .setContentText("服务正在运行中，可在后台自动转发短信/邮件")
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOngoing(true) // 设置为不可滑动取消
                .addAction(R.drawable.ic_close_float_window, "停止服务", stopPendingIntent); // 添加停止服务按钮

        // 对于Android 10及以上，使用全屏Intent来增强保活效果
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent fullScreenIntent = new Intent(context, MainActivity.class);
            PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                    context, 
                    2, 
                    fullScreenIntent, 
                    flags
            );
            builder.setFullScreenIntent(fullScreenPendingIntent, true);
        }

        return builder.build();
    }

    /**
     * 创建高优先级前台服务通知，类似微信视频电话的常驻效果
     * @param context 上下文
     * @return 通知对象
     */
    public static Notification createHighPriorityForegroundServiceNotification(Context context) {
        // 创建高优先级通知渠道（Android 8.0+必需）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createHighPriorityNotificationChannel(context);
        }

        // 创建通知意图，点击通知时打开MainActivity
        Intent intent = new Intent(context, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        // Android 12+需要添加IMMUTABLE标志
        if (Build.VERSION.SDK_INT >= 31) { // 31对应Android 12(S)
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, flags);

        // 创建停止服务的意图
        Intent stopIntent = new Intent(context, SmsForwardService.class);
        stopIntent.setAction(ACTION_STOP_SERVICE);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                context, 
                1, 
                stopIntent, 
                flags
        );

        // 创建打开应用的意图
        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.setAction(ACTION_OPEN_APP);
        PendingIntent openAppPendingIntent = PendingIntent.getActivity(
                context, 
                3, 
                openAppIntent, 
                flags
        );

        // 构建高优先级通知，类似微信视频电话的效果
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, HIGH_PRIORITY_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Fun自动转发服务")
                .setContentText("正在运行重要任务，请勿关闭")
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(Notification.CATEGORY_CALL) // 设置为通话类别，提高优先级
                .setOngoing(true) // 设置为不可滑动取消
                .setAutoCancel(false)
                .setWhen(System.currentTimeMillis())
                .setOnlyAlertOnce(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // 锁屏可见
                .addAction(R.mipmap.ic_launcher, "打开应用", openAppPendingIntent) // 添加打开应用按钮
                .addAction(R.drawable.ic_close_float_window, "停止服务", stopPendingIntent); // 添加停止服务按钮

        // 对于Android 5.0及以上，设置大图标
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // 将Drawable转换为Bitmap
            Drawable drawable = context.getResources().getDrawable(R.mipmap.ic_launcher);
            Bitmap bitmap = null;
            if (drawable instanceof BitmapDrawable) {
                bitmap = ((BitmapDrawable) drawable).getBitmap();
            } else {
                // 创建一个新的Bitmap并绘制Drawable
                bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                        drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                drawable.draw(canvas);
            }
            builder.setLargeIcon(bitmap);
        }

        // 对于Android 10及以上，使用全屏Intent来增强保活效果
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent fullScreenIntent = new Intent(context, MainActivity.class);
            PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                    context, 
                    2, 
                    fullScreenIntent, 
                    flags
            );
            builder.setFullScreenIntent(fullScreenPendingIntent, true);
        }

        return builder.build();
    }

    /**
     * 更新前台服务通知
     * @param context 上下文
     * @param content 更新后的内容
     * @return 更新后的通知对象
     */
    public static Notification updateForegroundServiceNotification(Context context, String content) {
        // 创建通知渠道（Android 8.0+必需）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(context);
        }

        // 创建通知意图，点击通知时打开MainActivity
        Intent intent = new Intent(context, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        // Android 12+需要添加IMMUTABLE标志
        if (Build.VERSION.SDK_INT >= 31) { // 31对应Android 12(S)
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, flags);

        // 创建停止服务的意图
        Intent stopIntent = new Intent(context, SmsForwardService.class);
        stopIntent.setAction(ACTION_STOP_SERVICE);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                context, 
                1, 
                stopIntent, 
                flags
        );

        // 构建通知
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Fun自动转发服务")
                .setContentText(content)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOngoing(true) // 设置为不可滑动取消
                .setWhen(System.currentTimeMillis()) // 更新时间戳
                .addAction(R.drawable.ic_close_float_window, "停止服务", stopPendingIntent); // 添加停止服务按钮

        // 对于Android 10及以上，使用全屏Intent来增强保活效果
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent fullScreenIntent = new Intent(context, MainActivity.class);
            PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                    context, 
                    2, 
                    fullScreenIntent, 
                    flags
            );
            builder.setFullScreenIntent(fullScreenPendingIntent, true);
        }

        return builder.build();
    }

    /**
     * 更新高优先级前台服务通知
     * @param context 上下文
     * @param content 更新后的内容
     * @return 更新后的通知对象
     */
    public static Notification updateHighPriorityForegroundServiceNotification(Context context, String content) {
        // 创建高优先级通知渠道（Android 8.0+必需）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createHighPriorityNotificationChannel(context);
        }

        // 创建通知意图，点击通知时打开MainActivity
        Intent intent = new Intent(context, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        // Android 12+需要添加IMMUTABLE标志
        if (Build.VERSION.SDK_INT >= 31) { // 31对应Android 12(S)
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, flags);

        // 创建停止服务的意图
        Intent stopIntent = new Intent(context, SmsForwardService.class);
        stopIntent.setAction(ACTION_STOP_SERVICE);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                context, 
                1, 
                stopIntent, 
                flags
        );

        // 创建打开应用的意图
        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.setAction(ACTION_OPEN_APP);
        PendingIntent openAppPendingIntent = PendingIntent.getActivity(
                context, 
                3, 
                openAppIntent, 
                flags
        );

        // 构建高优先级通知
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, HIGH_PRIORITY_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Fun自动转发服务")
                .setContentText(content)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(Notification.CATEGORY_CALL) // 设置为通话类别，提高优先级
                .setOngoing(true) // 设置为不可滑动取消
                .setAutoCancel(false)
                .setWhen(System.currentTimeMillis()) // 更新时间戳
                .setOnlyAlertOnce(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // 锁屏可见
                .addAction(R.mipmap.ic_launcher, "打开应用", openAppPendingIntent) // 添加打开应用按钮
                .addAction(R.drawable.ic_close_float_window, "停止服务", stopPendingIntent); // 添加停止服务按钮

        // 对于Android 5.0及以上，设置大图标
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // 将Drawable转换为Bitmap
            Drawable drawable = context.getResources().getDrawable(R.mipmap.ic_launcher);
            Bitmap bitmap = null;
            if (drawable instanceof BitmapDrawable) {
                bitmap = ((BitmapDrawable) drawable).getBitmap();
            } else {
                // 创建一个新的Bitmap并绘制Drawable
                bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                        drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                drawable.draw(canvas);
            }
            builder.setLargeIcon(bitmap);
        }

        // 对于Android 10及以上，使用全屏Intent来增强保活效果
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent fullScreenIntent = new Intent(context, MainActivity.class);
            PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                    context, 
                    2, 
                    fullScreenIntent, 
                    flags
            );
            builder.setFullScreenIntent(fullScreenPendingIntent, true);
        }

        return builder.build();
    }

    /**
     * 创建通知渠道（Android 8.0+必需）
     * @param context 上下文
     */
    @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.O)
    private static void createNotificationChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
        );
        // 设置渠道描述
        channel.setDescription("用于显示短信/邮件自动转发服务的运行状态");
        // 设置不震动
        channel.enableVibration(false);
        // 注册渠道
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * 创建高优先级通知渠道（Android 8.0+必需）
     * @param context 上下文
     */
    @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.O)
    private static void createHighPriorityNotificationChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(
                HIGH_PRIORITY_CHANNEL_ID,
                HIGH_PRIORITY_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
        );
        // 设置渠道描述
        channel.setDescription("用于显示重要的短信/邮件自动转发服务状态");
        // 设置震动
        channel.enableVibration(true);
        // 设置声音
        channel.setSound(null, null); // 不使用声音
        // 设置显示灯
        channel.enableLights(true);
        // 注册渠道
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * 显示普通通知
     * @param context 上下文
     * @param title 标题
     * @param content 内容
     * @param notificationId 通知ID
     */
    public static void showNotification(Context context, String title, String content, int notificationId) {
        // 创建通知渠道（Android 8.0+必需）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(context);
        }

        // 创建通知意图，点击通知时打开MainActivity
        Intent intent = new Intent(context, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        // Android 12+需要添加IMMUTABLE标志
        if (Build.VERSION.SDK_INT >= 31) { // 31对应Android 12(S)
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, flags);

        // 构建通知
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true); // 点击后自动取消

        // 显示通知
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.notify(notificationId, builder.build());
        }
    }

    /**
     * 显示高优先级通知
     * @param context 上下文
     * @param title 标题
     * @param content 内容
     * @param notificationId 通知ID
     */
    public static void showHighPriorityNotification(Context context, String title, String content, int notificationId) {
        // 创建高优先级通知渠道（Android 8.0+必需）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createHighPriorityNotificationChannel(context);
        }

        // 创建通知意图，点击通知时打开MainActivity
        Intent intent = new Intent(context, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        // Android 12+需要添加IMMUTABLE标志
        if (Build.VERSION.SDK_INT >= 31) { // 31对应Android 12(S)
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, flags);

        // 构建通知
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, HIGH_PRIORITY_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(Notification.CATEGORY_CALL)
                .setAutoCancel(true);

        // 显示通知
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.notify(notificationId, builder.build());
        }
    }
}