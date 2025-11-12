package com.funshion.funautosend;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.funshion.funautosend.model.PhoneNumberData;
import com.funshion.funautosend.model.SmsMessage;
import com.funshion.funautosend.service.SmsForwardService;
import com.funshion.funautosend.ui.MainAdapter;
import com.funshion.funautosend.util.ApiClient;
import com.funshion.funautosend.util.CountdownHelper;
import com.funshion.funautosend.util.EmailHelper;
import com.funshion.funautosend.util.ForwardedSmsManager;
import com.funshion.funautosend.util.FloatWindowPermissionHelper;
import com.funshion.funautosend.util.KeepAliveManager;
import com.funshion.funautosend.util.NotificationUtils;
import com.funshion.funautosend.util.PermissionHelper;
import com.funshion.funautosend.util.PreferencesHelper;
import com.funshion.funautosend.util.SmsHelper;
import com.funshion.funautosend.util.SmsStorageHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private Button btnSetPhoneNumber;
    private Button btnRefreshApi;
    private Button btnDeleteAllSms;
    private TextView tvNoData;
    private RecyclerView rvMainContent;
    // private TextView tvCountdown;
    private MainAdapter mainAdapter;
    private List<Map<String, Object>> apiResultList = new ArrayList<>();
    // 保存匹配到的item，用作后续逻辑使用
    private List<Map<String, Object>> targetList = new ArrayList<>();
//    private CountdownHelper countdownHelper;
    private static final int REQUEST_FLOAT_WINDOW_PERMISSION = 100;
    private List<MainAdapter.ItemData> itemDataList = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化控件
        initViews();

        // 检查并申请权限
        PermissionHelper.checkAndRequestPermissions(this);
        
        // 检查并请求悬浮窗权限
        checkAndRequestFloatWindowPermission();
        
        // 初始化RecyclerView数据
        initRecyclerViewData();
        
        // 加载并显示已保存的手机号
        loadAndShowSavedPhoneNumbers();
        
        // 检查是否需要引导用户将应用加入白名单
        checkAndGuideWhiteList();
        
        // 初始化短信接收器 - 由于我们在服务中也注册了接收器，这里可以保留但要注意不要重复处理
        initSmsReceiver();
        
        // 初始化倒计时
        // initCountdown();
        
        // 注意：定时扫描短信功能已移至SmsForwardService中统一处理，避免重复扫描
        
        // 启动前台服务，保证后台稳定运行
        startForegroundService();
        
        // 启动保活机制
        KeepAliveManager.getInstance(this).startAllKeepAliveMechanisms();
        
        // 首次启动时刷新数据
        handleRefreshApi();
    }

    // 扫描间隔时间，参考SmsForwardService中的CHECK_INTERVAL常量
    // 这里设置为5分钟，可根据需要调整
    private static final int SMS_SCAN_INTERVAL = 1 * 60 * 1000; // 5分钟，单位：毫秒
    private Handler smsScanHandler;
    private Runnable smsScanRunnable;
    
    /**
     * 扫描所有短信内容
     */
    private void scanAllSms() {
        Log.d("MainActivity", "[手动触发]开始扫描所有短信");
        
        // 检查是否有读取短信权限
        if (SmsHelper.hasReadSmsPermission(this)) {
            // 在后台线程中执行扫描，避免阻塞主线程
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // 使用SmsHelper的静态方法扫描并保存短信到本地存储
                        SmsHelper.scanAndSaveSmsToStorage(MainActivity.this);
                        
                        // 在主线程中更新UI并显示提示
                        // runOnUiThread(new Runnable() {
                        //     @Override
                        //     public void run() {
                        //         Toast.makeText(MainActivity.this, "短信扫描完成", Toast.LENGTH_SHORT).show();
                        //     }
                        // });
                        
                        Log.d("MainActivity", "[手动触发]短信扫描完成");
                    } catch (Exception e) {
                        Log.e("MainActivity", "[手动触发]短信扫描过程中出现异常: " + e.getMessage());
                        e.printStackTrace();
                        
                        // 在主线程中显示错误提示
                        // runOnUiThread(new Runnable() {
                        //     @Override
                        //     public void run() {
                        //         Toast.makeText(MainActivity.this, "短信扫描失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        //     }
                        // });
                    }
                }
            }).start();
        } else {
            // 请求读取短信权限
            Log.d("MainActivity", "请求读取短信权限");
            SmsHelper.requestReadSmsPermission(this);
        }
    }
    
    /**
     * 初始化定时扫描短信功能
     */
    private void initSmsAutoScan() {
        smsScanHandler = new Handler();
        smsScanRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d("MainActivity", "执行定时短信扫描");
                scanAllSms();
                
                // 安排下一次扫描
                smsScanHandler.postDelayed(this, SMS_SCAN_INTERVAL);
            }
        };
        
        // 启动定时扫描
        smsScanHandler.postDelayed(smsScanRunnable, SMS_SCAN_INTERVAL);
        Log.d("MainActivity", "定时短信扫描功能已初始化，间隔: " + SMS_SCAN_INTERVAL / 1000 + "秒");
    }
    
    /**
     * 停止定时扫描短信
     */
    private void stopSmsAutoScan() {
        if (smsScanHandler != null && smsScanRunnable != null) {
            smsScanHandler.removeCallbacks(smsScanRunnable);
            Log.d("MainActivity", "定时短信扫描已停止");
        }
    }
    
    // 注意：onRequestPermissionsResult方法已在类中其他位置定义，
    // 这里不再重复定义，而是在现有方法中添加对读取短信权限的处理逻辑
    
    /**
     * 启动前台服务
     */
    private void startForegroundService() {
        try {
            Intent serviceIntent = new Intent(this, SmsForwardService.class);
            
            // 检查Android版本，使用不同的启动方式
            if (android.os.Build.VERSION.SDK_INT >= 26) { // 26对应Android 8.0(O)
                // Android 8.0及以上使用startForegroundService
                startForegroundService(serviceIntent);
            } else {
                // Android 8.0以下使用普通的startService
                startService(serviceIntent);
            }
            
            Log.d("MainActivity", "前台服务启动成功");
        } catch (Exception e) {
            Log.e("MainActivity", "前台服务启动失败: " + e.getMessage());
            Toast.makeText(this, "服务启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 初始化界面控件
     */
    private void initViews() {
        btnSetPhoneNumber = findViewById(R.id.btn_set_phone_number);
        btnRefreshApi = findViewById(R.id.btn_refresh_api);
        btnDeleteAllSms = findViewById(R.id.btn_delete_all_sms);
        tvNoData = findViewById(R.id.tv_no_data);
        rvMainContent = findViewById(R.id.rv_main_content);
        // tvCountdown = findViewById(R.id.tv_countdown);
        
        // 设置手机号按钮点击事件
        btnSetPhoneNumber.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSetPhoneNumberDialog();
            }
        });
        
       // 刷新接口按钮点击事件
       btnRefreshApi.setOnClickListener(new View.OnClickListener() {
           @Override
           public void onClick(View v) {
               handleRefreshApi();
           }
       });
       
       // 删除所有短信按钮点击事件
       btnDeleteAllSms.setOnClickListener(new View.OnClickListener() {
           @Override
           public void onClick(View v) {
               handleDeleteAllSms();
           }
       });
        

    }
    
    /**
     * 初始化RecyclerView数据
     */
    private void initRecyclerViewData() {
        // 初始化RecyclerView
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvMainContent.setLayoutManager(layoutManager);
        
        // 创建并设置适配器
        mainAdapter = new MainAdapter(this, itemDataList);
        rvMainContent.setAdapter(mainAdapter);
        
        // 更新RecyclerView数据
        updateRecyclerViewData();
    }
    
    /**
     * 更新RecyclerView数据
     */
    private void updateRecyclerViewData() {
        itemDataList.clear();
        
        // 添加手机号显示项
        MainAdapter.ItemData phoneItem = new MainAdapter.ItemData(MainAdapter.ItemType.PHONE_DISPLAY);
        String phone1 = PreferencesHelper.getPhoneNumber1(this);
        String phone2 = PreferencesHelper.getPhoneNumber2(this);
        String displayPhone1 = phone1.isEmpty() ? "卡1：未设置" : "卡1：" + phone1;
        String displayPhone2 = phone2.isEmpty() ? "卡2：未设置" : "卡2：" + phone2;
        phoneItem.phoneData = new PhoneNumberData(displayPhone1, displayPhone2);
        itemDataList.add(phoneItem);
        
        // 添加白名单设置项
        MainAdapter.ItemData whiteListItem = new MainAdapter.ItemData(MainAdapter.ItemType.SETTING);
        whiteListItem.title = "应用优化设置：";
        // 直接计算白名单状态
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            boolean isIgnoring = PermissionHelper.isIgnoringBatteryOptimizations(this);
            if (isIgnoring) {
                whiteListItem.status = "白名单状态：已加入";
            } else {
                whiteListItem.status = "白名单状态：未加入";
            }
        } else {
            whiteListItem.status = "白名单状态：Android 6.0以下无需设置";
        }
        whiteListItem.buttonText = "应用白名单";
        whiteListItem.buttonClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PermissionHelper.guideUserToWhiteList(MainActivity.this);
            }
        };
        itemDataList.add(whiteListItem);
        
        // 添加悬浮窗设置项
        MainAdapter.ItemData floatWindowItem = new MainAdapter.ItemData(MainAdapter.ItemType.SETTING);
        floatWindowItem.title = "悬浮弹窗设置：";
        // 直接计算悬浮窗状态
        if (FloatWindowPermissionHelper.hasFloatWindowPermission(this)) {
            floatWindowItem.status = "悬浮窗权限：已获得";
        } else {
            floatWindowItem.status = "悬浮窗权限：未获得";
        }
        floatWindowItem.buttonText = "悬浮窗设置";
        floatWindowItem.buttonClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkAndRequestFloatWindowPermission();
            }
        };
        itemDataList.add(floatWindowItem);
        
        // 添加API结果列表
        if (!apiResultList.isEmpty()) {
            for (Map<String, Object> apiResult : apiResultList) {
                MainAdapter.ItemData apiItem = new MainAdapter.ItemData(MainAdapter.ItemType.API_RESULT);
                apiItem.apiResult = apiResult;
                itemDataList.add(apiItem);
            }
        } else {
            MainAdapter.ItemData emptyItem = new MainAdapter.ItemData(MainAdapter.ItemType.EMPTY);
            emptyItem.title = "配置列表：";
            emptyItem.status = "暂无数据";
            itemDataList.add(emptyItem);
        }
        
        // 通知适配器数据已更改
        mainAdapter.notifyDataSetChanged();
        
        // 控制空数据提示的显示
        if (itemDataList.isEmpty()) {
            tvNoData.setVisibility(View.VISIBLE);
        } else {
            tvNoData.setVisibility(View.GONE);
        }
    }
    

    
    /**
     * 检查并请求悬浮窗权限
     */
    private void checkAndRequestFloatWindowPermission() {
        if (!FloatWindowPermissionHelper.checkAndRequestFloatWindowPermission(this, REQUEST_FLOAT_WINDOW_PERMISSION)) {
            Toast.makeText(this, "需要悬浮窗权限才能显示悬浮弹窗", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 初始化倒计时功能
     */
    // private void initCountdown() {
    //     countdownHelper = new CountdownHelper(tvCountdown, 300, new CountdownHelper.CountdownListener() {
    //         @Override
    //         public void onCountdownComplete() {
    //             // handleRefreshApi();
    //         }
    //     });
    // }

    /**
     * 处理刷新接口按钮点击事件
     */
    /**
     * 处理删除所有短信的逻辑
     */
    private void handleDeleteAllSms() {
        // 显示确认对话框
        new AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("请确保先删除了本机所有短信，然后再删除配置，且操作不可逆！！！")
            .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // 删除forwarded_sms_prefs配置
                    ForwardedSmsManager.getInstance(MainActivity.this).clearAllForwardedIds();
                    
                    // 删除sms_storage_prefs配置
                    SmsStorageHelper.getInstance(MainActivity.this).clearSmsList();
                    
                    Log.d("MainActivity", "已删除所有短信配置文件");
                    Toast.makeText(MainActivity.this, "短信配置已删除", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void handleRefreshApi() {
        // 显示加载提示
        // Toast.makeText(this, "正在请求接口数据...", Toast.LENGTH_SHORT).show();
        
        // 检查是否有网络权限
        if (PermissionHelper.hasPermission(this, android.Manifest.permission.INTERNET)) {
            // 获取电池电量
            int batteryPercentage = PermissionHelper.getBatteryLevel(this);
            
            // 使用ApiClient执行网络请求，传入电池电量参数
            ApiClient.fetchApiData(this, batteryPercentage, new ApiClient.ApiCallback() {
                @Override
                public void onSuccess(final String result) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Toast.makeText(MainActivity.this, "请求成功", Toast.LENGTH_SHORT).show();
                            Log.d("MainActivity", "接口返回数据: " + result);
                            
                            // 获取已保存的手机号
                            String savedPhone1 = PreferencesHelper.getPhoneNumber1(MainActivity.this);
                            String savedPhone2 = PreferencesHelper.getPhoneNumber2(MainActivity.this);
                            
                            // 解析接口数据
                            ApiClient.ParseResult parseResult = ApiClient.parseApiResult(result, savedPhone1, savedPhone2);
                            
                            // 更新数据并显示
                            updateAndShowApiResult(parseResult);
                            
                            // 确保请求完成后才开始倒计时
//                            countdownHelper.startCountdown();
                        }
                    });
                }

                @Override
                public void onFailure(final String error) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "请求失败: " + error + "，尝试加载本地缓存数据", Toast.LENGTH_SHORT).show();
                            
                            // 尝试从本地加载已保存的数据
                            List<Map<String, Object>> savedApiResultList = PreferencesHelper.getApiResultList(MainActivity.this);
                            List<Map<String, Object>> savedTargetList = PreferencesHelper.getTargetList(MainActivity.this);
                            
                            if (savedApiResultList != null && savedTargetList != null) {
                                // 清空现有数据
                                apiResultList.clear();
                                targetList.clear();
                                
                                // 添加本地缓存的数据
                                apiResultList.addAll(savedApiResultList);
                                targetList.addAll(savedTargetList);
                                
                                // 更新RecyclerView数据
                                updateRecyclerViewData();
                                
                                // 显示加载本地数据成功的提示
                                Toast.makeText(MainActivity.this, "已加载本地缓存数据", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(MainActivity.this, "暂无本地缓存数据", Toast.LENGTH_SHORT).show();
                            }
                            
                            // 即使请求失败，也要继续倒计时，以便下次尝试
//                            countdownHelper.startCountdown();
                        }
                    });
                }
            });
        } else {
            Toast.makeText(this, "需要网络权限", Toast.LENGTH_SHORT).show();
            PermissionHelper.requestPermission(this, android.Manifest.permission.INTERNET, PermissionHelper.PERMISSION_REQUEST_CODE);
        }
    }

    /**
     * 更新并显示API结果
     */
    private void updateAndShowApiResult(ApiClient.ParseResult parseResult) {
        // 清空现有数据
        apiResultList.clear();
        targetList.clear();
        
        // 添加新数据
        apiResultList.addAll(parseResult.getApiResultList());
        targetList.addAll(parseResult.getTargetList());
        
        // 更新RecyclerView数据
        updateRecyclerViewData();
    }

    /**
     * 显示设置手机号对话框
     */
    private void showSetPhoneNumberDialog() {
        // 创建对话框布局
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_set_phone_number, null);
        final EditText etPhone1 = dialogView.findViewById(R.id.et_phone1);
        final EditText etPhone2 = dialogView.findViewById(R.id.et_phone2);
        
        // 加载已保存的手机号
        String savedPhone1 = PreferencesHelper.getPhoneNumber1(this);
        String savedPhone2 = PreferencesHelper.getPhoneNumber2(this);
        etPhone1.setText(savedPhone1);
        etPhone2.setText(savedPhone2);
        
        // 创建对话框并应用自定义样式
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomDialog);
        AlertDialog dialog = builder
            .setTitle("设置手机号")
            .setView(dialogView)
            .setPositiveButton("确定", (d, which) -> {
                String phone1 = etPhone1.getText().toString().trim();
                String phone2 = etPhone2.getText().toString().trim();
                
                // 保存手机号
                PreferencesHelper.savePhoneNumbers(MainActivity.this, phone1, phone2);
                
                // 更新界面显示
                loadAndShowSavedPhoneNumbers();
                
                // 保存成功后主动刷新数据
                // handleRefreshApi();
                
                Toast.makeText(MainActivity.this, "手机号已保存并刷新数据", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", (d, which) -> d.dismiss())
            .create();
        
        // 自定义对话框按钮样式
        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            
            // 设置按钮文本颜色
            positiveButton.setTextColor(getResources().getColor(android.R.color.holo_blue_light));
            negativeButton.setTextColor(getResources().getColor(android.R.color.darker_gray));
            
            // 设置按钮字体大小
            positiveButton.setTextSize(16);
            negativeButton.setTextSize(16);
            
            // 为两个按钮设置相同的布局参数，确保平均分配空间
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            );
            positiveButton.setLayoutParams(params);
            negativeButton.setLayoutParams(params);
        });
        
        dialog.show();
    }

    // 从SharedPreferences加载并显示已保存的手机号
    private void loadAndShowSavedPhoneNumbers() {
        // 加载手机号数据
        String phoneNumber1 = PreferencesHelper.getPhoneNumber1(this);
        String phoneNumber2 = PreferencesHelper.getPhoneNumber2(this);
        
        // 更新RecyclerView数据
        updateRecyclerViewData();
    }

    /**
     * 初始化方法（已移除短信接收器，短信接收由SmsForwardService统一处理）
     */
    private void initSmsReceiver() {
        Log.d("MainActivity", "短信接收功能已由SmsForwardService统一处理");
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 停止倒计时
//        if (countdownHelper != null) {
//            countdownHelper.stopCountdown();
//        }
        
        // 停止保活机制
        KeepAliveManager.getInstance(this).stopAllKeepAliveMechanisms();
    }

    /**
     * 处理权限请求结果
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean allGranted = PermissionHelper.handlePermissionResult(requestCode, grantResults, this);
        
        // 如果所有权限都被授予，引导用户将应用加入白名单
        if (allGranted) {
            checkAndGuideWhiteList();
        }
        
        // 如果是通知权限被授予，重新启动前台服务
        if (android.os.Build.VERSION.SDK_INT >= 33) { // 33对应Android 13(TIRAMISU)
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(PermissionHelper.NOTIFICATION_PERMISSION) && 
                        grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    startForegroundService();
                    break;
                }
            }
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // 处理悬浮窗权限请求结果
        if (requestCode == REQUEST_FLOAT_WINDOW_PERMISSION) {
            // 如果获得了悬浮窗权限，重新启动前台服务以应用悬浮窗
            if (FloatWindowPermissionHelper.hasFloatWindowPermission(this)) {
                startForegroundService();
                Toast.makeText(this, "悬浮窗权限已获得，现在可以显示悬浮弹窗", Toast.LENGTH_SHORT).show();
            }
            
            // 更新RecyclerView数据
            updateRecyclerViewData();
        }
    }
    
    /**
     * 检查并引导用户将应用加入白名单
     */
    private void checkAndGuideWhiteList() {
        // 检查是否需要请求忽略电池优化
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && 
                !PermissionHelper.isIgnoringBatteryOptimizations(this)) {
            // 显示白名单引导对话框
            PermissionHelper.guideUserToWhiteList(this);
        }
    }
    
    @Override
    public void onBackPressed() {
        // 显示退出确认弹窗
        new AlertDialog.Builder(this)
                .setTitle("确认退出")
                .setMessage("确定要退出应用吗？退出后将无法自动转发短信。")
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 执行原有的返回键逻辑，退出应用
                        MainActivity.super.onBackPressed();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // 恢复倒计时功能
//        if (countdownHelper != null) {
//            countdownHelper.resumeCountdown();
//        }
        
        // 更新RecyclerView数据
        updateRecyclerViewData();
    }
}
