package com.funshion.funautosend;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import com.funshion.funautosend.util.LogUtil;
import android.widget.Toast;

import com.funshion.funautosend.service.SmsForwardService;
import com.funshion.funautosend.util.ForwardedSmsManager;
import com.funshion.funautosend.util.PreferencesHelper;
import com.funshion.funautosend.util.SmsHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 短信接收广播接收器，用于拦截手机收到的短信并获取相关信息
 */
public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";
    private static final String SMS_RECEIVED_ACTION = "android.provider.Telephony.SMS_RECEIVED";
    
    private SmsReceivedListener mListener;
    private Intent mLastReceivedIntent; // 保存最后接收到的短信广播意图，用于获取SIM卡信息

    public interface SmsReceivedListener {
        void onSmsReceived(String sender, String content, String time);
    }
    
    /**
     * 根据SIM卡ID获取对应的本机号码
     * @param context 上下文
     * @param simId SIM卡ID
     * @return 对应的本机号码，如果无法获取则返回空字符串
     */
    private String getPhoneNumberBySimId(Context context, String simId) {
        if (context == null || simId == null) {
            LogUtil.w(TAG, "上下文或SIM卡ID为空，无法获取本机号码");
            return "";
        }
        
        LogUtil.d(TAG, "正在处理SIM卡ID: " + simId);
        
        try {


            // 针对当前设备的特殊映射：subscription_id为1实际对应SIM卡2
            if (simId.equals("1")) {
                // 对于subscription_id为1的情况，返回SIM卡2的号码
                return PreferencesHelper.getPhoneNumber1(context);
            }else if(simId.equals("2")){
                return PreferencesHelper.getPhoneNumber2(context);
            }
            
            // // 其他精确匹配
            // if (simId.equals("2")) {
            //     // subscription_id为2可能对应SIM卡1
            //     return PreferencesHelper.getPhoneNumber1(context);
            // } else if (simId.equals("0")) {
            //     // SIM卡1（有些设备使用0表示第一张SIM卡）
            //     return PreferencesHelper.getPhoneNumber1(context);
            // }
            
            // // 对于包含数字的SIM卡ID，使用更精确的判断逻辑
            // try {
            //     // 提取SIM卡ID中的主要数字标识
            //     if (simId.contains("1") && !simId.contains("2")) {
            //         // 只包含1不包含2，判断为SIM卡2
            //         return PreferencesHelper.getPhoneNumber2(context);
            //     } else if (simId.contains("2")) {
            //         // 包含2，判断为SIM卡1
            //         return PreferencesHelper.getPhoneNumber1(context);
            //     } else if (simId.contains("0") && !simId.contains("1") && !simId.contains("2")) {
            //         // 只包含0不包含1和2，判断为SIM卡1
            //         return PreferencesHelper.getPhoneNumber1(context);
            //     }
            // } catch (Exception e) {
            //     Log.w(TAG, "解析SIM卡ID时出错: " + e.getMessage());
            // }
            
            // 其他情况，尝试获取默认手机号
            String phone1 = PreferencesHelper.getPhoneNumber1(context);
            return !phone1.isEmpty() ? phone1 : PreferencesHelper.getPhoneNumber2(context);
        } catch (Exception e) {
            LogUtil.e(TAG, "获取本机号码时出错: " + e.getMessage());
            return "";
        }
    }
    
    public void setOnSmsReceivedListener(SmsReceivedListener listener) {
        this.mListener = listener;
    }
    
    @Override
    public void onReceive(Context context, Intent intent) {
        LogUtil.d(TAG, "接收到短信广播");
        
        // 保存最后接收到的意图，用于后续获取SIM卡信息
        this.mLastReceivedIntent = intent;
        
        // 确保前台服务正在运行
        startSmsForwardService(context);
        
        // 触发短信延迟扫描
        triggerSmsScan(context);
    }
    
    /**
     * 触发短信扫描服务
     * @param context 上下文
     */
    private void triggerSmsScan(Context context) {
        try {
            // 创建广播意图，告知SmsForwardService执行短信扫描
            Intent scanIntent = new Intent(context, SmsForwardService.class);
            scanIntent.putExtra("ACTION_SCAN_SMS", true);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(scanIntent);
            } else {
                context.startService(scanIntent);
            }
            
            LogUtil.d(TAG, "已发送触发短信扫描的广播");
        } catch (Exception e) {
            LogUtil.e(TAG, "触发短信扫描失败: " + e.getMessage());
        }
    }
    
    /**
     * 在后台处理短信转发逻辑
     */
    private void processSmsInBackground(final Context context, final String sender, final String content, final String time, final String smsId, final String simId) {
        // 使用线程池在后台线程中处理短信转发，避免在主线程阻塞
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, // 核心线程数
                2, // 最大线程数
                30, TimeUnit.SECONDS, // 空闲线程存活时间
                new LinkedBlockingQueue<Runnable>(),
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r, "SmsProcessThread");
                        thread.setPriority(Thread.MAX_PRIORITY); // 设置高优先级
                        return thread;
                    }
                }
        );
        
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // 优先使用本地存储的目标列表
                    List<Map<String, Object>> localTargetList = PreferencesHelper.getTargetList(context);
                    
                    if (localTargetList != null && !localTargetList.isEmpty()) {
                        LogUtil.d(TAG, "使用本地存储的目标列表，数量: " + localTargetList.size());
                        
                        // 根据SIM卡ID过滤目标列表，只保留匹配当前SIM卡的规则
                        List<Map<String, Object>> filteredTargetList = new ArrayList<>();
                        
                        // 获取当前SIM卡对应的本机号码
                        String currentPhoneNumber = getPhoneNumberBySimId(context, simId);
                        LogUtil.d(TAG, "当前SIM卡ID: " + simId + ", 对应的本机号码: " + currentPhoneNumber);
                        
                        // 过滤目标列表，只保留workPhone与当前SIM卡本机号码匹配的规则
                        for (Map<String, Object> item : localTargetList) {
                            Map<String, String> fields = (Map<String, String>) item.get("fields");
                            if (fields != null) {
                                String workPhone = fields.getOrDefault("workPhone", "");
                                // 只有当workPhone与当前SIM卡的本机号码匹配时，才添加到过滤后的列表中
                                if (!workPhone.isEmpty() && workPhone.equals(currentPhoneNumber)) {
                                    filteredTargetList.add(item);
                                    LogUtil.d(TAG, "找到匹配的转发规则，workPhone: " + workPhone);
                                }
                            }
                        }
                        
                        LogUtil.d(TAG, "过滤后的目标列表数量: " + filteredTargetList.size());
                        
                        // 遍历过滤后的目标列表，执行短信转发和邮件发送
                        for (Map<String, Object> item : filteredTargetList) {
                            // 处理短信转发，传递smsId、simId和时间参数
                            // 确保即使使用临时ID也能正确处理
                            boolean isForwarded = SmsHelper.handleAutoSendSms(item, sender, content, context, smsId, simId, time);
                        
                            // 保存短信到本地存储，包含SIM卡ID信息
                            // 对于临时ID也进行保存，确保数据完整性
                            if (smsId != null) {
                                ForwardedSmsManager.saveReceivedSms(context, smsId, sender, content, time, simId);
                                LogUtil.d(TAG, "短信已保存到本地存储，短信ID: " + smsId + ", SIM卡ID: " + (simId != null ? simId : "未知"));
                                
                                // 对于长短信，预先设置一个特殊标记，表示正在处理中
                                if (content.length() > 70 && smsId.startsWith("temp_")) {
                                    LogUtil.d(TAG, "长短信使用临时ID处理，确保能够正确跟踪发送状态");
                                    // 直接将临时ID添加到已转发列表中，避免重复处理
                                    // 注意：这是一个临时解决方案，实际状态仍由SmsSendStatusReceiver处理
                                    ForwardedSmsManager.getInstance(context).addForwardedSmsId(smsId);
                                }
                            }
                        
                            // 短信转发命令已提交，立即将短信ID添加到已转发列表中
                            // 这是为了防止扫描机制触发时重复转发同一条短信
                            if (isForwarded && smsId != null) {
                                LogUtil.d(TAG, "短信转发命令已提交，短信ID: " + smsId);
                                ForwardedSmsManager.getInstance(context).addForwardedSmsId(smsId);
                                LogUtil.d(TAG, "已将短信ID添加到已转发列表，避免重复转发，短信ID: " + smsId);
                            }
                        }
                    } else {
                        LogUtil.d(TAG, "本地没有存储目标列表，无法处理短信转发");
                    }
                } catch (Exception e) {
                    LogUtil.e(TAG, "处理短信时出错: " + e.getMessage());
                    e.printStackTrace();
                    
                    // 异常情况下也尝试保存短信信息，确保数据不丢失
                    if (smsId != null) {
                        try {
                            ForwardedSmsManager.saveReceivedSms(context, smsId, sender, content, time, simId);
                            LogUtil.d(TAG, "异常情况下已保存短信信息，短信ID: " + smsId);
                        } catch (Exception ex) {
                            LogUtil.e(TAG, "异常情况下保存短信信息失败: " + ex.getMessage());
                        }
                    }
                } finally {
                    // 执行完任务后关闭线程池
                    executor.shutdown();
                }
            }
        });
    }
    
    /**
     * 通过内容提供者查询短信ID和SIM卡信息
     * @param context 上下文
     * @param sender 发件人
     * @param content 短信内容
     * @param timestamp 时间戳
     * @return 包含短信ID和SIM卡ID的数组 [smsId, simId]，如果未找到则返回包含临时ID的数组
     */
    /**
     * 从短信广播意图中获取SIM卡信息
     * @param intent 短信广播意图
     * @return SIM卡ID，如果无法获取则返回null
     */
    private String getSimIdFromIntent(Intent intent) {
        try {
            // 不同设备和Android版本可能使用不同的key来存储SIM卡信息
            String simId = null;
            
            // 尝试从bundle中获取sub_id（Android较新版本常用）
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                // 尝试获取sub_id
                Object subIdObj = bundle.get("subscription_id");
                if (subIdObj != null) {
                    simId = subIdObj.toString();
                            LogUtil.d(TAG, "从intent获取到subscription_id: " + simId);
                }
                
                // 尝试获取slot_id
                if (simId == null) {
                    Object slotIdObj = bundle.get("slot_id");
                    if (slotIdObj != null) {
                        simId = slotIdObj.toString();
                            // 有些设备slot_id从0开始，需要调整为从1开始
                            try {
                                int slotId = Integer.parseInt(simId);
                                simId = String.valueOf(slotId + 1);
                            } catch (Exception e) {
                                // 忽略转换错误
                            }
                            LogUtil.d(TAG, "从intent获取到slot_id并调整: " + simId);
                    }
                }
                
                // 尝试获取sim_id
                if (simId == null) {
                    Object simIdObj = bundle.get("sim_id");
                    if (simIdObj != null) {
                        simId = simIdObj.toString();
                            LogUtil.d(TAG, "从intent获取到sim_id: " + simId);
                    }
                }
            }
            
            return simId;
        } catch (Exception e) {
            LogUtil.e(TAG, "从intent获取SIM卡信息时出错: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 生成短信唯一ID
     * @param sender 发件人
     * @param content 短信内容
     * @param timestamp 时间戳
     * @return 基于sender、content和timestamp生成的唯一ID
     */
    private String generateSmsId(String sender, String content, long timestamp) {
        // 确保参数不为null
        sender = (sender == null) ? "" : sender;
        content = (content == null) ? "" : content;
        
        // 考虑到短信广播时间戳和数据库时间戳在秒级别就存在差异
        // 根据需求，我们将时间戳调整到3秒级别，这样可以平衡匹配概率和唯一性
        long normalizedTimestamp = (timestamp / 3000) * 3000;
        
        // 组合sender、content和标准化的时间戳生成唯一字符串
        String uniqueString = sender + "|" + content + "|" + normalizedTimestamp;
        
        // // 使用哈希算法生成ID，确保唯一性的同时保持ID长度合理
        // // 添加前缀"sms_"以便识别自定义生成的ID
        // String hash = String.valueOf(Math.abs(uniqueString.hashCode()));
        
        // // 为了进一步确保唯一性，添加时间戳的后6位
        // String timestampSuffix = String.valueOf(normalizedTimestamp % 1000000);
        
        // return "sms_" + hash + "_" + timestampSuffix;
        return uniqueString;
    }
    
    /**
     * 获取短信信息，使用自定义生成的ID而不是从系统数据库获取
     * @param context 上下文
     * @param sender 发件人
     * @param content 短信内容
     * @param timestamp 时间戳
     * @return 包含短信ID和SIM卡ID的数组 [smsId, simId]
     */
    private String[] getSmsInfo(Context context, String sender, String content, long timestamp) {
        // 先尝试从全局变量保存的intent中获取SIM卡信息
        String simIdFromIntent = getSimIdFromIntent(mLastReceivedIntent);
        LogUtil.d(TAG, "从intent获取的SIM卡ID: " + (simIdFromIntent != null ? simIdFromIntent : "未找到"));
        
        // 使用自定义方法生成短信ID
        String smsId = generateSmsId(sender, content, timestamp);
        LogUtil.d(TAG, "生成的短信ID: " + smsId);
        
        // 仍然可以进行数据库查询来获取SIM卡信息，但不再依赖系统ID
        String simIdFromDb = null;
        
        // 为了获取SIM卡信息，可以进行一次简单的查询
        try {
            Uri uri = Uri.parse("content://sms/inbox");
            String[] projection = new String[]{"sim_id", "sub_id"};
            String selection = "address = ? AND date > ? AND date < ?";
            String[] selectionArgs = new String[]{
                sender,
                String.valueOf(timestamp - 2000),
                String.valueOf(timestamp + 2000)
            };
            
            Cursor cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, "date DESC");
            if (cursor != null && cursor.moveToFirst()) {
                // 尝试获取SIM卡信息，只有当intent中没有获取到时才从数据库获取
                if (simIdFromIntent == null) {
                    try {
                        String[] possibleSimFields = {"sim_id", "sub_id"};
                        for (String field : possibleSimFields) {
                            int fieldIndex = cursor.getColumnIndex(field);
                            if (fieldIndex != -1 && !cursor.isNull(fieldIndex)) {
                                simIdFromDb = cursor.getString(fieldIndex);
                                LogUtil.d(TAG, "从数据库" + field + "字段获取到SIM卡ID: " + simIdFromDb);
                                break;
                            }
                        }
                    } catch (Exception e) {
                        LogUtil.e(TAG, "获取SIM卡信息时出错", e);
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "查询SIM卡信息时出错: " + e.getMessage(), e);
        }
        
        // 优先使用intent中的SIM卡信息，如果没有则使用数据库中的
        String simId = simIdFromIntent != null ? simIdFromIntent : simIdFromDb;
        
        LogUtil.d(TAG, "返回的短信信息 - ID: " + smsId + ", SIM卡ID: " + (simId != null ? simId : "未找到"));
        return new String[]{smsId, simId};
    }
    
    /**
     * 计算两个短信内容的匹配分数
     * @param originalContent 原始短信内容
     * @param dbContent 数据库中的短信内容
     * @return 匹配分数，越高表示匹配度越好
     */
    private int calculateContentMatchScore(String originalContent, String dbContent) {
        if (originalContent == null || dbContent == null) {
            return 0;
        }
        
        // 改进的匹配算法，更准确地区分内容相似的短信
        int minLength = Math.min(originalContent.length(), dbContent.length());
        int matchCount = 0;
        int mismatchCount = 0;
        
        // 比较整个内容的开头部分
        int compareLength = Math.min(minLength, 100); // 增加比较长度到100个字符
        
        for (int i = 0; i < compareLength; i++) {
            if (originalContent.charAt(i) == dbContent.charAt(i)) {
                matchCount++;
            } else {
                mismatchCount++;
                // 对于完全相同位置的不匹配，给予更高的惩罚
                if (i < 20) { // 前20个字符更重要
                    mismatchCount++;
                }
            }
        }
        
        // 计算相似度百分比
        double similarityPercentage = (double)matchCount / compareLength * 100;
        
        // 对于非常短的短信（如验证码），需要特殊处理
        if (originalContent.length() <= 10 && dbContent.length() <= 10) {
            // 短短信要求更精确的匹配
            if (originalContent.equals(dbContent)) {
                return 100; // 完全匹配给满分
            } else {
                return 0; // 不完全匹配给低分
            }
        }
        
        // 根据相似度百分比计算最终分数
        int finalScore = (int)similarityPercentage;
        
        // 对于相似度高但不是完全相同的情况，降低一点分数
        if (finalScore > 80 && !originalContent.equals(dbContent)) {
            finalScore -= 10;
        }
        
        LogUtil.d(TAG, "内容匹配分析 - 原始内容长度: " + originalContent.length() + ", 数据库内容长度: " + dbContent.length() + 
              ", 匹配字符: " + matchCount + ", 不匹配字符: " + mismatchCount + ", 相似度: " + similarityPercentage + "%, 最终分数: " + finalScore);
        
        return finalScore;
    }
    
    /**
     * 兼容旧方法，仅返回短信ID
     */
    private String getSmsId(Context context, String sender, String content, long timestamp) {
        String[] smsInfo = getSmsInfo(context, sender, content, timestamp);
        return smsInfo != null ? smsInfo[0] : null;
    }
    
    /**
     * 启动短信转发前台服务
     */
    private void startSmsForwardService(Context context) {
        try {
            Intent serviceIntent = new Intent(context, SmsForwardService.class);
            
            // 检查Android版本，使用不同的启动方式
            if (Build.VERSION.SDK_INT >= 26) { // 26对应Android 8.0(O)
                // Android 8.0及以上使用startForegroundService
                context.startForegroundService(serviceIntent);
            } else {
                // Android 8.0以下使用普通的startService
                context.startService(serviceIntent);
            }
            
            LogUtil.d(TAG, "SmsForwardService启动成功");
        } catch (Exception e) {
            LogUtil.e(TAG, "SmsForwardService启动失败: " + e.getMessage());
        }
    }
}