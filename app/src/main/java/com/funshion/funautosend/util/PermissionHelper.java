package com.funshion.funautosend.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import com.funshion.funautosend.util.LogUtil;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * 权限助手类，负责处理权限请求和检查
 */
public class PermissionHelper {
    private static final String TAG = "PermissionHelper";
    public static final int PERMISSION_REQUEST_CODE = 1;

    /**
     * 需要申请的权限
     */
    public static final String[] REQUIRED_PERMISSIONS = {
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.INTERNET,
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.READ_PHONE_NUMBERS
    };
    
    /**
     * 获取设备当前电量百分比
     * @param context 上下文
     * @return 电池电量百分比（0-100），出错时返回-1
     */
    public static int getBatteryLevel(Context context) {
        try {
            BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            int batteryPercentage = -1;
            
            if (batteryManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    // Android 5.0及以上版本使用getIntProperty方法
                    batteryPercentage = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                } else {
                    // 旧版本使用IntentFilter方式
                    IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                    Intent batteryStatus = context.registerReceiver(null, ifilter);
                    if (batteryStatus != null) {
                        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                        if (level > 0 && scale > 0) {
                            batteryPercentage = (int) ((level / (float) scale) * 100);
                        }
                    }
                }
                
                // 获取充电状态
                int status = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS);
                boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                                     status == BatteryManager.BATTERY_STATUS_FULL;
                
                LogUtil.d(TAG, "电池电量: " + batteryPercentage + "%，充电状态: " + isCharging);
            }
            
            return batteryPercentage;
        } catch (Exception e) {
            LogUtil.e(TAG, "获取电池电量失败: " + e.getMessage());
            e.printStackTrace();
            return -1; // 出错时返回-1
        }
    };
    
    // Android 13及以上需要的通知权限
    // 使用字符串字面量替代SDK常量，以避免编译错误
    public static final String NOTIFICATION_PERMISSION = "android.permission.POST_NOTIFICATIONS";

    /**
     * 检查并申请所有必要的权限
     */
    public static void checkAndRequestPermissions(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            java.util.List<String> permissionsToRequest = new java.util.ArrayList<>();

            // 检查所有需要的权限
            for (String permission : REQUIRED_PERMISSIONS) {
                if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(permission);
                }
            }
            
            // Android 13及以上需要的通知权限
        if (Build.VERSION.SDK_INT >= 33) { // 33对应Android 13(TIRAMISU)
            if (ContextCompat.checkSelfPermission(activity, NOTIFICATION_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(NOTIFICATION_PERMISSION);
            }
        }

            // 如果有未授权的权限，则请求权限
            if (!permissionsToRequest.isEmpty()) {
                ActivityCompat.requestPermissions(activity, permissionsToRequest.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            }
        }
    }

    /**
     * 处理权限请求结果
     */
    public static boolean handlePermissionResult(int requestCode, int[] grantResults, Activity activity) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allPermissionsGranted = true;

            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (allPermissionsGranted) {
                if (activity != null) {
                    Toast.makeText(activity, "所有权限已获得", Toast.LENGTH_SHORT).show();
                }
            } else {
                if (activity != null) {
                    Toast.makeText(activity, "部分权限未获得，可能影响功能使用", Toast.LENGTH_SHORT).show();
                }
            }
            return allPermissionsGranted;
        }
        return false;
    }

    /**
     * 检查单个权限是否已授权
     */
    public static boolean hasPermission(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 请求单个权限
     */
    public static void requestPermission(Activity activity, String permission, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(activity, new String[]{permission}, requestCode);
        }
    }

    /**
     * 检查应用是否在电池优化白名单中
     */
    public static boolean isIgnoringBatteryOptimizations(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                return powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
            }
        }
        return true;
    }

    /**
     * 请求忽略电池优化
     */
    public static void requestIgnoreBatteryOptimizations(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(activity, "请求忽略电池优化失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 引导用户将应用加入白名单
     */
    public static void guideUserToWhiteList(Activity activity) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("优化提示")
                .setMessage("为了保证应用稳定运行，请将应用加入系统白名单并开启自启动权限。\n\n" +
                        "1. 忽略电池优化\n" +
                        "2. 允许自启动\n" +
                        "3. 允许后台运行")
                .setPositiveButton("立即设置", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 1. 首先请求忽略电池优化
                        if (!isIgnoringBatteryOptimizations(activity)) {
                            requestIgnoreBatteryOptimizations(activity);
                        }
                        
                        // 2. 然后跳转到应用详情页，引导用户设置自启动等权限
                        try {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(Uri.parse("package:" + activity.getPackageName()));
                            activity.startActivity(intent);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                })
                .setNegativeButton("稍后设置", null)
                .show();
    }
}