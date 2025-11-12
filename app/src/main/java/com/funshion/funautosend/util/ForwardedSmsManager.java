package com.funshion.funautosend.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.funshion.funautosend.model.SmsMessage;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 管理已转发的短信ID列表
 * 用于保存和跟踪哪些短信已经被成功转发
 */
public class ForwardedSmsManager {
    private static final String TAG = "ForwardedSmsManager";
    private static final String PREF_NAME = "forwarded_sms_prefs";
    private static final String KEY_FORWARDED_IDS = "forwarded_sms_ids";
    
    private static ForwardedSmsManager instance;
    private final Context context;
    
    private ForwardedSmsManager(Context context) {
        this.context = context;
    }
    
    /**
     * 获取单例实例
     * @param context 上下文
     * @return ForwardedSmsManager实例
     */
    public static synchronized ForwardedSmsManager getInstance(Context context) {
        if (instance == null) {
            instance = new ForwardedSmsManager(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * 获取SharedPreferences实例
     */
    private SharedPreferences getPreferences() {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * 获取SharedPreferences编辑器实例
     */
    private SharedPreferences.Editor getEditor() {
        return getPreferences().edit();
    }
    
    /**
     * 添加一条已转发的短信ID
     * @param smsId 短信ID
     */
    public void addForwardedSmsId(String smsId) {
        if (smsId == null || smsId.isEmpty()) {
            Log.w(TAG, "无效的短信ID，跳过添加");
            return;
        }
        
        try {
            // 获取现有ID集合
            Set<String> forwardedIds = getForwardedSmsIds();
            
            // 添加新ID
            forwardedIds.add(smsId);
            
            // 保存更新后的集合
            getEditor().putStringSet(KEY_FORWARDED_IDS, forwardedIds).apply();
            
            Log.d(TAG, "成功添加已转发短信ID: " + smsId + "，当前已转发总数: " + forwardedIds.size());
        } catch (Exception e) {
            Log.e(TAG, "添加已转发短信ID时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 获取所有已转发的短信ID集合
     * @return 已转发短信ID的集合
     */
    public Set<String> getForwardedSmsIds() {
        try {
            Set<String> ids = getPreferences().getStringSet(KEY_FORWARDED_IDS, new HashSet<>());
            // 创建新的HashSet以确保可修改性
            return new HashSet<>(ids);
        } catch (Exception e) {
            Log.e(TAG, "获取已转发短信ID列表时出错: " + e.getMessage());
            e.printStackTrace();
            return new HashSet<>();
        }
    }
    
    /**
     * 检查短信ID是否已转发
     * @param smsId 短信ID
     * @return 如果已转发返回true，否则返回false
     */
    public boolean isSmsForwarded(String smsId) {
        if (smsId == null || smsId.isEmpty()) {
            return false;
        }
        return getForwardedSmsIds().contains(smsId);
    }
    
    /**
     * 清空所有已转发的短信ID记录
     */
    public void clearAllForwardedIds() {
        try {
            getEditor().putStringSet(KEY_FORWARDED_IDS, new HashSet<>()).apply();
            Log.d(TAG, "已清空所有已转发短信ID记录");
        } catch (Exception e) {
            Log.e(TAG, "清空已转发短信ID记录时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 静态方法：保存接收到的短信信息到本地存储
     * @param context 上下文
     * @param smsId 短信ID
     * @param sender 发送者手机号
     * @param content 短信内容
     * @param time 接收时间
     * @param simId SIM卡ID
     */
    public static void saveReceivedSms(Context context, String smsId, String sender, String content, String time, String simId) {
        try {
            // 创建短信对象，包含SIM卡ID信息
            SmsMessage smsMessage = new SmsMessage(
                    smsId,
                    sender,
                    sender,
                    "本机号码",
                    content,
                    time,
                    "收到的短信",
                    simId
            );
            
            // 获取现有短信列表
            List<SmsMessage> smsList = SmsStorageHelper.getInstance(context).getSmsList();
            
            // 添加新短信到列表开头
            smsList.add(0, smsMessage);
            
            // 保存更新后的列表
            SmsStorageHelper.getInstance(context).saveSmsList(smsList);
            
            Log.d(TAG, "已保存短信到本地存储，短信ID: " + smsId + ", SIM卡ID: " + (simId != null ? simId : "未知"));
            
            // 打印所有已保存的短信ID
            List<SmsMessage> allSmsList = SmsStorageHelper.getInstance(context).getSmsList();
            StringBuilder idsString = new StringBuilder("所有已保存的短信ID: ");
            for (SmsMessage message : allSmsList) {
                idsString.append(message.getId()).append(" ");
            }
            Log.d(TAG, idsString.toString());
        } catch (Exception e) {
            Log.e(TAG, "保存收到的短信时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
}