package com.funshion.funautosend.util;

import android.content.Context;
import android.content.SharedPreferences;
import com.funshion.funautosend.util.LogUtil;

import com.funshion.funautosend.model.SmsMessage;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 短信存储助手类，负责将短信数据保存到本地并读取
 */
public class SmsStorageHelper {
    private static final String TAG = "SmsStorageHelper";
    private static final String PREFS_NAME = "sms_storage_prefs";
    private static final String SMS_LIST_KEY = "sms_message_list";
    private static final String LAST_SCAN_TIME_KEY = "last_scan_timestamp";
    
    private final SharedPreferences prefs;
    private final Gson gson;
    
    private static volatile SmsStorageHelper instance;
    
    private SmsStorageHelper(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }
    
    /**
     * 获取单例实例
     */
    public static SmsStorageHelper getInstance(Context context) {
        if (instance == null) {
            synchronized (SmsStorageHelper.class) {
                if (instance == null) {
                    instance = new SmsStorageHelper(context.getApplicationContext());
                }
            }
        }
        return instance;
    }
    
    /**
     * 保存短信列表到本地存储
     * @param smsList 短信列表
     */
    public void saveSmsList(List<SmsMessage> smsList) {
        try {
            String json = gson.toJson(smsList);
            prefs.edit()
                .putString(SMS_LIST_KEY, json)
                .putLong(LAST_SCAN_TIME_KEY, System.currentTimeMillis())
                .apply();
            LogUtil.d(TAG, "成功保存 " + smsList.size() + " 条短信到本地存储");
        } catch (Exception e) {
            LogUtil.e(TAG, "保存短信列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 从本地存储读取短信列表
     * @return 短信列表，如果没有存储则返回空列表
     */
    public List<SmsMessage> getSmsList() {
        try {
            String json = prefs.getString(SMS_LIST_KEY, null);
            if (json != null) {
                Type type = new TypeToken<List<SmsMessage>>(){}.getType();
                List<SmsMessage> smsList = gson.fromJson(json, type);
                LogUtil.d(TAG, "从本地存储读取到 " + (smsList != null ? smsList.size() : 0) + " 条短信");
                return smsList != null ? smsList : new ArrayList<>();
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "读取短信列表失败: " + e.getMessage());
        }
        return new ArrayList<>();
    }
    
    /**
     * 获取上次扫描的时间戳
     * @return 时间戳，单位毫秒
     */
    public long getLastScanTimestamp() {
        return prefs.getLong(LAST_SCAN_TIME_KEY, 0);
    }
    
    /**
     * 清空本地存储的短信列表
     */
    public void clearSmsList() {
        prefs.edit()
            .remove(SMS_LIST_KEY)
            .remove(LAST_SCAN_TIME_KEY)
            .apply();
        LogUtil.d(TAG, "已清空本地存储的短信列表");
    }
    
    /**
     * 检查是否有存储的短信数据
     * @return true表示有存储数据，false表示没有
     */
    public boolean hasStoredSmsData() {
        return prefs.contains(SMS_LIST_KEY);
    }
}