package com.funshion.funautosend.receiver;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsManager;
import com.funshion.funautosend.util.LogUtil;
import java.util.HashMap;
import java.util.Map;

import com.funshion.funautosend.util.ForwardedSmsManager;

/**
 * 短信发送状态广播接收器
 * 用于监听短信发送结果，并在发送成功时记录短信ID
 */
public class SmsSendStatusReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsSendStatusReceiver";
    
    // 用于存储短信ID的extra键
    private static final String EXTRA_SMS_ID = "SMS_ID";
    
    // 用于跟踪多部分短信的发送状态
    private static final Map<String, Boolean> multipartSmsStatus = new HashMap<>();
    private static final Object lock = new Object();
    
    /**
     * 从Intent中获取短信ID
     */
    private static String getSmsIdFromIntent(Intent intent) {
        if (intent != null) {
            return intent.getStringExtra(EXTRA_SMS_ID);
        }
        return null;
    }
    
    /**
     * 向Intent添加短信ID
     */
    public static void addSmsIdToIntent(Intent intent, String smsId) {
        if (intent != null && smsId != null) {
            intent.putExtra(EXTRA_SMS_ID, smsId);
        }
    }
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            LogUtil.e(TAG, "接收到的Intent为null");
            return;
        }
        
        String action = intent.getAction();
        if (action == null || !action.equals("SMS_SENT")) {
            LogUtil.d(TAG, "收到非短信发送状态广播: " + action);
            return;
        }
        
        // 获取发送状态结果码
        int resultCode = getResultCode();
        // 获取短信部分索引
        int partIndex = intent.getIntExtra("PART_INDEX", -1);
        // 直接从Intent中获取短信ID
        String smsId = getSmsIdFromIntent(intent);
        
        LogUtil.d(TAG, "收到短信发送状态广播，结果码: " + resultCode + ", 部分索引: " + partIndex + ", 短信ID: " + smsId);
        
        // 检查是否有短信ID需要处理
        if (smsId == null || smsId.isEmpty()) {
            LogUtil.d(TAG, "没有待处理的短信ID");
            return;
        }
        
        boolean isTempId = smsId.startsWith("temp_");
        boolean isSuccessful = false;
        
        // 处理发送结果
        switch (resultCode) {
            case Activity.RESULT_OK:
                // 短信发送成功
                LogUtil.d(TAG, "短信发送成功，ID: " + smsId + (isTempId ? " (临时ID)" : ""));
                isSuccessful = true;
                break;
                
            case SmsManager.RESULT_NO_DEFAULT_SMS_APP:
                // 特殊处理RESULT_NO_DEFAULT_SMS_APP(32)，实际是发送成功
                LogUtil.d(TAG, "短信发送成功(RESULT_NO_DEFAULT_SMS_APP)，ID: " + smsId + (isTempId ? " (临时ID)" : ""));
                isSuccessful = true;
                break;
                
            case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                LogUtil.e(TAG, "短信发送失败: 一般错误，ID: " + smsId);
                break;
                
            case SmsManager.RESULT_ERROR_RADIO_OFF:
                LogUtil.e(TAG, "短信发送失败: 无线功能关闭，ID: " + smsId);
                break;
                
            case SmsManager.RESULT_ERROR_NULL_PDU:
                LogUtil.e(TAG, "短信发送失败: PDU为空，ID: " + smsId);
                break;
                
            case SmsManager.RESULT_ERROR_NO_SERVICE:
                LogUtil.e(TAG, "短信发送失败: 无服务，ID: " + smsId);
                break;
                
            default:
                LogUtil.e(TAG, "短信发送失败: 未知错误码 " + resultCode + ", ID: " + smsId);
                break;
        }
        
        // 处理多部分短信的情况
        if (partIndex >= 0) {
            synchronized (lock) {
                if (isSuccessful) {
                    // 对于多部分短信，只有当收到成功结果时才记录
                    multipartSmsStatus.put(smsId, true);
                    LogUtil.d(TAG, "多部分短信部分 " + partIndex + " 发送成功，ID: " + smsId);
                }
                
                // 注意：在简化实现中，我们只要有一个部分发送成功就认为整体成功
                // 实际应用中可能需要更复杂的逻辑来确认所有部分都发送成功
                if (multipartSmsStatus.containsKey(smsId)) {
                    LogUtil.d(TAG, "多部分短信至少有一部分发送成功，记录短信ID: " + smsId);
                    saveForwardedSmsId(context, smsId);
                    // 清理状态
                    multipartSmsStatus.remove(smsId);
                }
            }
        } else {
            // 单条短信的情况
            if (isSuccessful) {
                saveForwardedSmsId(context, smsId);
            }
        }
    }
    
    /**
     * 保存已转发短信ID
     */
    private void saveForwardedSmsId(Context context, String smsId) {
        try {
            ForwardedSmsManager.getInstance(context).addForwardedSmsId(smsId);
            LogUtil.d(TAG, "已成功保存短信ID: " + smsId);
        } catch (Exception e) {
            LogUtil.e(TAG, "保存短信ID失败: " + e.getMessage(), e);
        }
    }
    

}