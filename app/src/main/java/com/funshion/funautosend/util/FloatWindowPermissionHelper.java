package com.funshion.funautosend.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import com.funshion.funautosend.util.LogUtil;

import android.util.Log;
import android.widget.Toast;
import java.util.List;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * 悬浮窗权限辅助类，用于检查和请求悬浮窗权限
 * 提供针对不同手机厂商的特殊处理逻辑
 */
public class FloatWindowPermissionHelper {

    private static final String TAG = "FloatWindowPermissionHelper";
    private static final String MANUFACTURER_XIAOMI = "xiaomi";
    private static final String MANUFACTURER_HUAWEI = "huawei";
    private static final String MANUFACTURER_OPPO = "oppo";
    private static final String MANUFACTURER_VIVO = "vivo";
    private static final String MANUFACTURER_SAMSUNG = "samsung";
    private static final String MANUFACTURER_MEIZU = "meizu";

    /**
     * 检查是否有悬浮窗权限
     * @param context 上下文
     * @return true表示有权限，false表示没有权限
     */
    public static boolean hasFloatWindowPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0及以上版本需要检查权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0及以上版本使用canDrawOverlays
                return Settings.canDrawOverlays(context);
            } else {
                // Android 6.0-7.1版本也使用canDrawOverlays
                return Settings.canDrawOverlays(context);
            }
        } else {
            // Android 6.0以下版本默认有权限
            return true;
        }
    }

    /**
     * 请求悬浮窗权限
     * @param activity Activity对象
     * @param requestCode 请求码
     */
    public static void requestFloatWindowPermission(Activity activity, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                // 根据不同厂商跳转到对应权限设置页面
                if (requestManufacturerSpecificPermission(activity)) {
                    LogUtil.d(TAG, "已跳转到厂商特定权限设置页面");
                } else {
                    // 通用方法
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                    intent.setData(Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivityForResult(intent, requestCode);
                    LogUtil.d(TAG, "请求悬浮窗权限");
                }
            } catch (Exception e) {
                LogUtil.e(TAG, "请求悬浮窗权限失败: " + e.getMessage());
                // 如果打开设置页面失败，跳转到应用详情页
                openAppDetailSettings(activity);
                showToast(activity, "无法直接跳转到权限设置页面，请手动设置");
            }
        }
    }

    /**
     * 请求厂商特定的悬浮窗权限
     * @param activity Activity对象
     * @return true表示已处理厂商特定权限，false表示使用通用方法
     */
    private static boolean requestManufacturerSpecificPermission(Activity activity) {
        String manufacturer = Build.MANUFACTURER.toLowerCase();
        LogUtil.d(TAG, "当前手机厂商: " + manufacturer);
        
        try {
            switch (manufacturer) {
                case MANUFACTURER_XIAOMI:
                    // 小米手机
                    Intent miuiIntent = new Intent("miui.intent.action.APP_PERM_EDITOR");
                    miuiIntent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity");
                    miuiIntent.putExtra("extra_pkgname", activity.getPackageName());
                    activity.startActivity(miuiIntent);
                    return true;
                case MANUFACTURER_HUAWEI:
                    // 华为手机
                    Intent huaweiIntent = new Intent();
                    huaweiIntent.setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.addviewmonitor.AddViewMonitorActivity");
                    activity.startActivity(huaweiIntent);
                    return true;
                case MANUFACTURER_OPPO:
                    // OPPO手机
                    Intent oppoIntent = new Intent("android.intent.action.settings");
                    oppoIntent.setClassName("com.coloros.safecenter", "com.coloros.safecenter.permission.floatwindow.FloatWindowListActivity");
                    oppoIntent.putExtra("packageName", activity.getPackageName());
                    activity.startActivity(oppoIntent);
                    return true;
                case MANUFACTURER_VIVO:
                    // VIVO手机
                    Intent vivoIntent = new Intent("android.intent.action.settings");
                    vivoIntent.setClassName("com.bbk.incallui", "com.bbk.incallui.floatwindow.FloatWindowManager");
                    activity.startActivity(vivoIntent);
                    return true;
                case MANUFACTURER_SAMSUNG:
                    // 三星手机
                    Intent samsungIntent = new Intent();
                    samsungIntent.setAction(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                    samsungIntent.setData(Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivity(samsungIntent);
                    return true;
                case MANUFACTURER_MEIZU:
                    // 魅族手机
                    Intent meizuIntent = new Intent("com.meizu.safe.security.SHOW_APPSEC");
                    meizuIntent.putExtra("packageName", activity.getPackageName());
                    meizuIntent.setClassName("com.meizu.safe", "com.meizu.safe.security.AppSecActivity");
                    activity.startActivity(meizuIntent);
                    return true;
                default:
                    // 其他厂商，使用通用方法
                    return false;
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "厂商特定权限请求失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 打开应用详情设置页面
     * @param context 上下文
     */
    public static void openAppDetailSettings(Context context) {
        try {
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            LogUtil.d(TAG, "打开应用详情设置页面");
        } catch (Exception e) {
            LogUtil.e(TAG, "打开应用详情设置页面失败: " + e.getMessage());
            showToast(context, "无法打开应用详情页面");
        }
    }

    /**
     * 显示权限说明对话框，解释为什么需要悬浮窗权限
     * @param activity Activity对象
     * @param requestCode 请求码
     */
    public static void showPermissionExplanationDialog(final Activity activity, final int requestCode) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("需要悬浮窗权限")
                .setMessage("为了确保应用在后台稳定运行并及时接收和转发短信，需要开启悬浮窗权限。\n\n" +
                        "开启后，应用将显示一个类似微信的悬浮弹窗，表明应用正在运行中。\n\n" +
                        "请在设置页面中允许\"显示在其他应用上层\"权限。")
                .setPositiveButton("去设置", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        requestFloatWindowPermission(activity, requestCode);
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setCancelable(false)
                .show();
    }

    /**
     * 检查并请求悬浮窗权限
     * @param activity Activity对象
     * @param requestCode 请求码
     * @return true表示已有权限，false表示需要请求权限
     */
    public static boolean checkAndRequestFloatWindowPermission(Activity activity, int requestCode) {
        if (hasFloatWindowPermission(activity)) {
            LogUtil.d(TAG, "已获得悬浮窗权限");
            return true;
        } else {
            LogUtil.d(TAG, "未获得悬浮窗权限，显示权限说明");
            showPermissionExplanationDialog(activity, requestCode);
            return false;
        }
    }

    /**
     * 显示Toast提示
     * @param context 上下文
     * @param message 提示消息
     */
    private static void showToast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * 获取当前手机的ROM版本
     * @return ROM版本名称
     */
    public static String getRomVersion() {
        String rom = "Unknown";
        try {
            Process process = Runtime.getRuntime().exec("getprop ro.build.version.emui");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            if (line != null) {
                rom = line.trim();
            }
        } catch (IOException e) {
            LogUtil.e(TAG, "获取ROM版本失败: " + e.getMessage());
        }
        return rom;
    }

    /**
     * 检查设备是否是MIUI系统
     * @return true表示是MIUI系统
     */
    public static boolean isMIUI() {
        try {
            Class<?> cls = Class.forName("miui.os.Build");
            // if it's miui, cls will be loaded
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 检查设备是否是EMUI系统
     * @return true表示是EMUI系统
     */
    public static boolean isEMUI() {
        return getRomVersion().contains("EMUI");
    }

    /**
     * 获取当前应用是否在前台运行
     * @param context 上下文
     * @return true表示在前台运行
     */
    public static boolean isAppForeground(Context context) {
        try {
            // 使用ActivityManager来检查应用是否在前台，这是正确的方式
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager == null) {
                return false;
            }
            
            // 获取正在运行的应用进程列表
            List<ActivityManager.RunningAppProcessInfo> processes = activityManager.getRunningAppProcesses();
            if (processes == null) {
                return false;
            }
            
            String packageName = context.getPackageName();
            for (ActivityManager.RunningAppProcessInfo process : processes) {
                // 检查是否有当前应用的进程，并且该进程在前台
                if (process.processName.equals(packageName) && 
                    process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            LogUtil.e(TAG, "检查应用前台状态失败: " + e.getMessage());
            return false;
        }
    }
}