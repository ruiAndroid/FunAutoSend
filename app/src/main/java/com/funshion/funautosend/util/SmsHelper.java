package com.funshion.funautosend.util;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import com.funshion.funautosend.util.LogUtil;
import android.widget.Toast;
import java.lang.reflect.Method;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.funshion.funautosend.model.SmsMessage;
import com.funshion.funautosend.receiver.SmsSendStatusReceiver;
import com.funshion.funautosend.util.ApiClient;
import com.funshion.funautosend.util.ApiClient.SmsReportRequest;
import com.funshion.funautosend.util.ApiClient.EmailReportRequest;
import com.funshion.funautosend.util.ApiClient.ApiCallback;
import com.funshion.funautosend.util.EmailHelper.EmailSendCallback;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 短信助手类，负责处理短信发送逻辑
 */
public class SmsHelper {
    private static final String TAG = "SmsHelper";
    private static final int SMS_PERMISSION_REQUEST_CODE = 101;
    public static final int READ_SMS_PERMISSION_REQUEST_CODE = 102;
    
    // 静态成员变量，用于存储最近一次扫描的短信列表
    private static List<SmsMessage> lastScannedSmsList = new ArrayList<>();

    /**
     * 检查是否有发送短信权限
     */
    public static boolean hasSmsPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 请求发送短信权限
     */
    public static void requestSmsPermission(Activity activity) {
        ActivityCompat.requestPermissions(activity,
                new String[]{android.Manifest.permission.SEND_SMS}, SMS_PERMISSION_REQUEST_CODE);
    }
    
    /**
     * 检查是否有读取短信权限
     */
    public static boolean hasReadSmsPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * 请求读取短信权限
     */
    public static void requestReadSmsPermission(Activity activity) {
        ActivityCompat.requestPermissions(activity,
                new String[]{android.Manifest.permission.READ_SMS}, READ_SMS_PERMISSION_REQUEST_CODE);
    }
    
    /**
     * 生成短信唯一ID
     * @param sender 发件人
     * @param content 短信内容
     * @param timestamp 时间戳
     * @return 基于sender、content和timestamp生成的唯一ID
     */
    public static String generateSmsId(String sender, String content, long timestamp) {
        // 确保参数不为null
        sender = (sender == null) ? "" : sender;
        content = (content == null) ? "" : content;
        
        // 考虑到短信广播时间戳和数据库时间戳在秒级别就存在差异
        // 根据需求，我们将时间戳调整到3秒级别，这样可以平衡匹配概率和唯一性
        // long normalizedTimestamp = (timestamp / 3000) * 3000;
        
        // 组合sender、content和标准化的时间戳生成唯一字符串
        String uniqueString = sender + "|" + content + "|" + timestamp;
        
        // // 使用哈希算法生成ID，确保唯一性的同时保持ID长度合理
        // // 添加前缀"sms_"以便识别自定义生成的ID
        // String hash = String.valueOf(Math.abs(uniqueString.hashCode()));
        
        // // 为了进一步确保唯一性，添加时间戳的后6位
        // String timestampSuffix = String.valueOf(normalizedTimestamp % 1000000);
        
        // return "sms_" + hash + "_" + timestampSuffix;
        return uniqueString;
    }
    
    /**
     * 获取所有短信内容
     * @param context 上下文
     * @return 短信列表，每个元素是SmsMessage实体对象
     */
    public static List<SmsMessage> getAllSms(Context context) {
        List<SmsMessage> smsList = new ArrayList<>();
        
        // 检查权限
        if (!hasReadSmsPermission(context)) {
            LogUtil.e(TAG, "没有读取短信权限");
            return smsList;
        }
        
        try {
            // 短信内容提供者的URI
            Uri uri = Uri.parse("content://sms/");
            ContentResolver contentResolver = context.getContentResolver();
            
            // 查询短信内容 - 只获取收到的短信（type=1），增加_id字段用于获取短信ID，增加sim_id和sub_id字段以获取SIM卡信息
            String[] projection = new String[]{"_id", "address", "body", "date", "type", "sim_id", "sub_id"};
            String selection = "type = ?";
            String[] selectionArgs = new String[]{"1"}; // 1表示收件箱（收到的短信）
            Cursor cursor = contentResolver.query(uri, projection, selection, selectionArgs, "date DESC");
            
            if (cursor != null) {
                int totalCount = cursor.getCount();
                LogUtil.d(TAG, "总共找到 " + totalCount + " 条短信");
                
                while (cursor.moveToNext()) {
                    String senderNumber = cursor.getString(cursor.getColumnIndex("address")); // 由于只查询收到的短信，address就是发件人
                    String body = cursor.getString(cursor.getColumnIndex("body")); // 短信内容
                    long date = cursor.getLong(cursor.getColumnIndex("date")); // 时间戳
                    
                    // 从数据库获取短信ID
                    String id = String.valueOf(cursor.getLong(cursor.getColumnIndex("_id")));
                    
                    // 暂时注释掉使用自定义方法生成短信ID的逻辑，后面再用
                    // String id = generateSmsId(senderNumber, body, date);
                    
                    // 格式化时间
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                    String formattedDate = sdf.format(new Date(date));
                    
                    // 尝试获取SIM卡信息，不同设备可能使用不同的字段名
                    String simId = null;
                    try {
                        // 尝试获取sim_id字段
                        int simIdIndex = cursor.getColumnIndex("sim_id");
                        if (simIdIndex != -1 && !cursor.isNull(simIdIndex)) {
                            simId = cursor.getString(simIdIndex);
                        } else {
                            // 尝试获取sub_id字段
                            int subIdIndex = cursor.getColumnIndex("sub_id");
                            if (subIdIndex != -1 && !cursor.isNull(subIdIndex)) {
                                simId = cursor.getString(subIdIndex);
                            }
                        }
                    } catch (Exception e) {
                        LogUtil.e(TAG, "获取短信SIM卡信息时出错", e);
                    }
                    
                    // 创建短信实体对象，包含SIM卡ID信息
                    SmsMessage smsItem = new SmsMessage(
                            id,
                            senderNumber,
                            senderNumber,
                            "本机号码",
                            body,
                            formattedDate,
                            "收到的短信",
                            simId
                    );
                    
                    smsList.add(smsItem);
                    
                    // 打印短信信息到日志
                    LogUtil.d(TAG, "短信ID: " + smsItem.getId() + ", 类型: " + smsItem.getType() + ", 发件人: " + smsItem.getSender() + ", 收件人: " + smsItem.getRecipient() + ", 时间: " + smsItem.getDate() + ", SIM卡ID: " + (smsItem.getSimId() != null ? smsItem.getSimId() : "未找到"));
                    LogUtil.d(TAG, "内容: " + smsItem.getBody());
                }
                cursor.close();
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "获取短信内容失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        // 将扫描结果存储到静态成员变量中
        lastScannedSmsList = new ArrayList<>(smsList);
        
        return smsList;
    }
    
    /**
     * 扫描短信并保存到本地存储
     * 无论是否扫描到短信，都会更新本地存储
     * 同时检查是否有漏发的短信，如果有则补发短信和邮件
     * @param context 上下文
     */
    public static void scanAndSaveSmsToStorage(Context context) {
        List<SmsMessage> smsList = getAllSms(context);
        // 无论是否扫描到短信，都更新本地存储
        SmsStorageHelper.getInstance(context).saveSmsList(smsList);
        
        // 检查并补发白转发的短信
        checkAndResendMissedSms(context, smsList);
    }
    
    /**
     * 检查并补发白转发的短信
     * @param context 上下文
     * @param smsList 扫描到的短信列表
     */
    private static void checkAndResendMissedSms(Context context, List<SmsMessage> smsList) {
        if (context == null || smsList == null || smsList.isEmpty()) {
            LogUtil.d(TAG, "没有可检查的短信列表或上下文为空");
            return;
        }
        
        try {
            // 获取已转发短信ID列表和管理器
            ForwardedSmsManager forwardedManager = ForwardedSmsManager.getInstance(context);
            Set<String> forwardedSmsIds = forwardedManager.getForwardedSmsIds();
            LogUtil.d(TAG, "当前已转发短信ID数量: " + forwardedSmsIds.size());
            
            // 获取转发规则列表
            List<Map<String, Object>> targetList = PreferencesHelper.getTargetList(context);
            if (targetList == null || targetList.isEmpty()) {
                LogUtil.d(TAG, "没有可用的转发规则，跳过补发白短信");
                return;
            }
            
            LogUtil.d(TAG, "开始检查漏发的短信，总短信数: " + smsList.size());
            int resendCount = 0;
            
            // 遍历扫描到的短信，找出未转发的短信
            for (SmsMessage smsMessage : smsList) {
                String smsId = smsMessage.getId();
                // 检查短信是否已转发
                    if (!forwardedSmsIds.contains(smsId)) {
                        LogUtil.d(TAG, "发现未转发的短信，ID: " + smsId + "，发件人: " + smsMessage.getSender());
                    
                    // 尝试转发前，先将短信ID标记为已转发，确保每个短信只补发一次
                    forwardedManager.addForwardedSmsId(smsId);
                    LogUtil.d(TAG, "短信ID: " + smsId + " 已标记为已转发，确保只补发一次");
                    
                    // 对每条未转发的短信，只应用第一个匹配的转发规则
                    // 避免因多个规则导致同一短信被多次转发
                    boolean smsHandled = false;
                    for (Map<String, Object> item : targetList) {
                        try {
                            // 获取规则中的workPhone字段用于匹配
                            Map<String, String> fields = (Map<String, String>) item.get("fields");
                            if (fields != null && fields.containsKey("workPhone")) {
                                String workPhone = fields.get("workPhone");
                                // 获取SIM卡号码
                                String simPhoneNumber = getSimPhoneNumber(context, smsMessage.getSimId());
                                LogUtil.d(TAG, "检查规则匹配: workPhone=" + workPhone + ", SIM卡ID=" + smsMessage.getSimId() + ", SIM卡号码=" + (simPhoneNumber != null ? simPhoneNumber : "未找到"));
                                
                                // 只有当workPhone与SIM卡号码匹配时，才进行短信转发
                                if (simPhoneNumber != null && workPhone.equals(simPhoneNumber)) {
                                    // 调用handleAutoSendSms方法进行短信转发，确保参数顺序正确
                                    // 参数顺序: 规则项, 发送者手机号, 短信内容, 上下文, 短信ID, SIM卡ID
                                    boolean resendResult = handleAutoSendSms(item, smsMessage.getSender(), smsMessage.getBody(), context, smsId, smsMessage.getSimId(), smsMessage.getDate());
                                    LogUtil.d(TAG, "补发短信ID: " + smsId + " 使用SIM卡ID: " + (smsMessage.getSimId() != null ? smsMessage.getSimId() : "未设置"));
                                    if (resendResult) {
                                        LogUtil.d(TAG, "成功提交短信ID: " + smsId + " 的补发请求");
                                        resendCount++;
                                        smsHandled = true;
                                        // 成功处理后跳出循环，避免应用多个规则
                                        break;
                                    } else {
                                        LogUtil.e(TAG, "提交短信ID: " + smsId + " 的补发请求失败，但已标记为已转发，不再重复补发");
                                    }
                                } else {
                                    LogUtil.d(TAG, "SIM卡号码与workPhone不匹配，跳过转发: workPhone=" + workPhone + ", SIM卡号码=" + (simPhoneNumber != null ? simPhoneNumber : "未找到"));
                                }
                            }
                        } catch (Exception e) {
                                LogUtil.e(TAG, "处理转发规则时出错: " + e.getMessage());
                            }
                        
                        // 如果已经处理成功，跳出循环
                        if (smsHandled) {
                            break;
                        }
                    }
                }
            }
            
            if (resendCount > 0) {
                LogUtil.d(TAG, "补发白短信任务完成，成功提交 " + resendCount + " 条补发请求，所有尝试补发的短信已标记为已转发");
            } else {
                LogUtil.d(TAG, "没有发现需要补发的短信");
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "检查和补发白短信时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 从本地存储获取短信列表
     * @param context 上下文
     * @return 短信列表
     */
    public static List<SmsMessage> getSmsListFromStorage(Context context) {
        return SmsStorageHelper.getInstance(context).getSmsList();
    }
    
    /**
     * 获取最近一次扫描的短信列表
     * @return 短信列表，如果从未扫描过则返回空列表
     */
    public static List<SmsMessage> getLastScannedSmsList() {
        return new ArrayList<>(lastScannedSmsList); // 返回副本以避免外部直接修改
    }
    
    /**
     * 清空内存中存储的短信列表
     */
    public static void clearLastScannedSmsList() {
        lastScannedSmsList.clear();
    }
    
    /**
     * 清空本地存储的短信列表
     */
    public static void clearStoredSmsList(Context context) {
        SmsStorageHelper.getInstance(context).clearSmsList();
    }
    
    /**
     * 根据SIM卡ID获取对应的SmsManager实例
     * @param context 上下文
     * @param simId SIM卡ID
     * @return 对应的SmsManager实例
     */
    private static SmsManager getSmsManagerForSim(Context context, String simId) {
        try {
            if (simId == null || simId.isEmpty()) {
                LogUtil.d(TAG, "未指定SIM卡ID，使用默认SmsManager");
                return SmsManager.getDefault();
            }
            
            LogUtil.d(TAG, "获取SmsManager，SIM卡ID: " + simId);
            
            // 尝试根据SIM卡ID获取对应的SmsManager
            // 不同Android版本和厂商可能有不同的实现方式
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                // Android 5.1及以上版本
                try {
                    // 尝试使用SubscriptionManager获取订阅ID
                    SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                    if (subscriptionManager != null) {
                        List<SubscriptionInfo> subscriptionInfoList = subscriptionManager.getActiveSubscriptionInfoList();
                        if (subscriptionInfoList != null && !subscriptionInfoList.isEmpty()) {
                            // 针对当前设备的特殊映射处理
                                if (simId.equals("1")) {
                                    // 对于simId为"1"的情况，我们需要找到实际对应SIM卡2的订阅ID
                                    // 通常这是第二个可用的SIM卡
                                    // if (subscriptionInfoList.size() >= 2) {
                                    //     SubscriptionInfo secondInfo = subscriptionInfoList.get(1);
                                    //     LogUtil.d(TAG, "特殊映射：simId为1，使用第二个SIM卡，订阅ID: " + secondInfo.getSubscriptionId() + "，SIM卡槽: " + secondInfo.getSimSlotIndex());
                                    //     return SmsManager.getSmsManagerForSubscriptionId(secondInfo.getSubscriptionId());
                                    // }
                                    SubscriptionInfo firstInfo = subscriptionInfoList.get(0);
                                    LogUtil.d(TAG, "特殊映射：simId为1，使用第一个SIM卡，订阅ID: " + firstInfo.getSubscriptionId() + "，SIM卡槽: " + firstInfo.getSimSlotIndex());
                                    return SmsManager.getSmsManagerForSubscriptionId(firstInfo.getSubscriptionId());
                                } else if (simId.equals("2")) {
                                    // 对于simId为"2"的情况，我们需要找到实际对应SIM卡1的订阅ID
                                    // 通常这是第一个可用的SIM卡
                                    // SubscriptionInfo firstInfo = subscriptionInfoList.get(0);
                                    // LogUtil.d(TAG, "特殊映射：simId为2，使用第一个SIM卡，订阅ID: " + firstInfo.getSubscriptionId() + "，SIM卡槽: " + firstInfo.getSimSlotIndex());
                                    // return SmsManager.getSmsManagerForSubscriptionId(firstInfo.getSubscriptionId());

                                    SubscriptionInfo secondInfo = subscriptionInfoList.get(1);
                                    LogUtil.d(TAG, "特殊映射：simId为2，使用第二个SIM卡，订阅ID: " + secondInfo.getSubscriptionId() + "，SIM卡槽: " + secondInfo.getSimSlotIndex());
                                    return SmsManager.getSmsManagerForSubscriptionId(secondInfo.getSubscriptionId());
                            }
                            
                            // 常规匹配逻辑
                            // for (SubscriptionInfo info : subscriptionInfoList) {
                            //     // 尝试匹配SIM卡ID或订阅ID
                            //     if (String.valueOf(info.getSubscriptionId()).equals(simId) || 
                            //         (info.getSimSlotIndex() >= 0 && String.valueOf(info.getSimSlotIndex() + 1).equals(simId))) {
                            //         LogUtil.d(TAG, "找到匹配的SIM卡，使用订阅ID: " + info.getSubscriptionId() + "，SIM卡槽: " + info.getSimSlotIndex());
                            //         return SmsManager.getSmsManagerForSubscriptionId(info.getSubscriptionId());
                            //     }
                            // }
                            
                            // // 对于包含数字的SIM卡ID，使用更精确的判断逻辑
                            // if (simId.contains("1") && !simId.contains("2")) {
                            //     // 只包含1不包含2，判断为SIM卡2，使用第二个可用的SIM卡
                            //     if (subscriptionInfoList.size() >= 2) {
                            //         SubscriptionInfo secondInfo = subscriptionInfoList.get(1);
                            //         LogUtil.d(TAG, "特殊映射：simId包含1不包含2，使用第二个SIM卡，订阅ID: " + secondInfo.getSubscriptionId());
                            //         return SmsManager.getSmsManagerForSubscriptionId(secondInfo.getSubscriptionId());
                            //     }
                            // } else if (simId.contains("2")) {
                            //     // 包含2，判断为SIM卡1，使用第一个可用的SIM卡
                            //     SubscriptionInfo firstInfo = subscriptionInfoList.get(0);
                            //     LogUtil.d(TAG, "特殊映射：simId包含2，使用第一个SIM卡，订阅ID: " + firstInfo.getSubscriptionId());
                            //     return SmsManager.getSmsManagerForSubscriptionId(firstInfo.getSubscriptionId());
                            // }
                        }
                    }
                } catch (Exception e) {
                    LogUtil.e(TAG, "使用SubscriptionManager获取SmsManager失败: " + e.getMessage());
                }
            }
            
            // 所有尝试都失败时，回退到默认SmsManager
            LogUtil.d(TAG, "无法根据simId获取特定的SmsManager，使用默认SmsManager");
            return SmsManager.getDefault();
        } catch (Exception e) {
            LogUtil.e(TAG, "获取SmsManager时出错: " + e.getMessage());
            return SmsManager.getDefault();
        }
    }

    /**
     * 发送短信到指定手机号码
     * @param phoneNumber 目标手机号
     * @param message 短信内容
     * @param context 上下文
     * @param simId 发送短信的SIM卡ID（可选）
     * @return 是否成功提交发送请求
     */
    public static boolean sendSmsToTarget(String phoneNumber, String message, Context context, String simId, String smsId) {
        try {
            if (phoneNumber == null || phoneNumber.isEmpty()) {
                if (context != null) {
                    showToastInUiThread(context, "目标手机号不能为空", Toast.LENGTH_SHORT);
                }
                LogUtil.e(TAG, "目标手机号为空，无法发送短信");
                return false;
            }

            // 获取当前SIM卡对应的手机号码
            String simPhoneNumber = getSimPhoneNumber(context, simId);
            
            // 详细日志记录，包括短信内容长度和特殊字符情况
            LogUtil.d(TAG, "准备发送短信到: " + phoneNumber);
            LogUtil.d(TAG, "短信内容长度: " + message.length() + " 字符");
            LogUtil.d(TAG, "短信内容包含特殊字符检查: " + containsSpecialCharacters(message));
            LogUtil.d(TAG, "短信内容前50字符: " + (message.length() > 50 ? message.substring(0, 50) : message));
            LogUtil.d(TAG, "使用SIM卡ID: " + (simId != null ? simId : "默认"));
            LogUtil.d(TAG, "使用SIM卡号码: " + (simPhoneNumber != null && !simPhoneNumber.isEmpty() ? simPhoneNumber : "未找到"));

            // 根据simId选择合适的SIM卡发送短信
            SmsManager smsManager = getSmsManagerForSim(context, simId);
            
            // 处理特殊字符，将可能导致问题的特殊字符进行转义或替换
            String processedMessage = processSpecialCharacters(message);
            if (!processedMessage.equals(message)) {
                LogUtil.d(TAG, "特殊字符已处理，处理后内容前50字符: " + (processedMessage.length() > 50 ? processedMessage.substring(0, 50) : processedMessage));
            }

            // 创建发送状态监听器，使用ComponentName明确指定目标广播接收器
            Intent sentIntent = new Intent("SMS_SENT");
            // 使用ComponentName明确指定广播接收器，确保在Android高版本中能正确接收
            sentIntent.setComponent(new ComponentName(context.getPackageName(), "com.funshion.funautosend.receiver.SmsSendStatusReceiver"));
            
            // 添加短信ID到Intent中（如果有）
            if (smsId != null && !smsId.isEmpty()) {
                try {
                    // 使用反射调用方法，避免直接依赖可能导致的编译错误
                    Class<?> receiverClass = Class.forName("com.funshion.funautosend.receiver.SmsSendStatusReceiver");
                    Method method = receiverClass.getMethod("addSmsIdToIntent", Intent.class, String.class);
                    method.invoke(null, sentIntent, smsId);
                    LogUtil.d(TAG, "已将短信ID添加到Intent: " + smsId);
                } catch (Exception e) {
                    // 备用方案：直接设置Extra
                    sentIntent.putExtra("SMS_ID", smsId);
                    LogUtil.d(TAG, "备用方案：已将短信ID添加到Intent: " + smsId);
                }
            }
            
            // 生成唯一的requestCode，使用smsId的哈希值或时间戳
            int requestCode = 0;
            if (smsId != null && !smsId.isEmpty()) {
                requestCode = smsId.hashCode();
            } else {
                requestCode = (int)(System.currentTimeMillis() % Integer.MAX_VALUE);
            }
            
            PendingIntent pendingSentIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                sentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            // 对于长短信进行拆分发送
            if (processedMessage.length() > 70) {
                ArrayList<String> parts = smsManager.divideMessage(processedMessage);
                LogUtil.d(TAG, "短信过长，已拆分为: " + parts.size() + " 部分");
                
                // 为每个短信部分创建发送状态监听器，同样使用ComponentName
                ArrayList<PendingIntent> sentIntents = new ArrayList<>();
                
                for (int i = 0; i < parts.size(); i++) {
                    Intent partIntent = new Intent("SMS_SENT");
                    partIntent.putExtra("PART_INDEX", i);
                    partIntent.setComponent(new ComponentName(context.getPackageName(), "com.funshion.funautosend.receiver.SmsSendStatusReceiver"));
                    
                    // 添加短信ID到Intent中（如果有）
                    if (smsId != null && !smsId.isEmpty()) {
                        try {
                            // 使用反射调用方法
                            Class<?> receiverClass = Class.forName("com.funshion.funautosend.receiver.SmsSendStatusReceiver");
                            Method method = receiverClass.getMethod("addSmsIdToIntent", Intent.class, String.class);
                            method.invoke(null, partIntent, smsId);
                            LogUtil.d(TAG, "已将短信ID添加到部分短信Intent: " + smsId + ", 部分索引: " + i);
                        } catch (Exception e) {
                            // 备用方案：直接设置Extra
                            partIntent.putExtra("SMS_ID", smsId);
                            LogUtil.d(TAG, "备用方案：已将短信ID添加到部分短信Intent: " + smsId + ", 部分索引: " + i);
                        }
                    }
                    
                    // 为每个短信部分生成唯一的requestCode，结合smsId和部分索引
                    int partRequestCode = i;
                    if (smsId != null && !smsId.isEmpty()) {
                        partRequestCode = smsId.hashCode() + i; // 避免相同smsId不同部分的requestCode冲突
                    }
                    
                    sentIntents.add(PendingIntent.getBroadcast(
                        context,
                        partRequestCode,
                        partIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    ));
                }
                
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, null);
            } else {
                smsManager.sendTextMessage(phoneNumber, null, processedMessage, pendingSentIntent, null);
            }
            
            LogUtil.d(TAG, "短信发送命令已提交到系统");
            
            // 注意：这里只是表示发送命令已提交，实际发送结果需要通过广播监听器获取
            // 在当前实现中，我们暂时保持原有逻辑，将命令提交视为发送成功
            // 完整实现应该是在广播接收器中处理发送结果并通知相应组件
            
            if (context != null) {
                showToastInUiThread(context, "短信发送命令已提交", Toast.LENGTH_SHORT);
            }
            return true;
        } catch (Exception e) {
            LogUtil.e(TAG, "短信发送失败: " + e.getMessage());
            e.printStackTrace();
            if (context != null) {
                LogUtil.e(TAG, "短信发送失败，请检查网络或稍后重试");
                // 错误提示不使用Toast，避免在后台线程中出现问题
            }
            return false;
        }
    }
    
    /**
     * 根据SIM卡ID获取对应的手机号码
     * @param context 上下文
     * @param simId SIM卡ID
     * @return 对应的手机号码，如果无法获取则返回空字符串
     */
    private static String getSimPhoneNumber(Context context, String simId) {
        if (context == null || simId == null) {
            LogUtil.w(TAG, "上下文或SIM卡ID为空，无法获取SIM卡号码");
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

//            // 针对当前设备的特殊映射：subscription_id为1实际对应SIM卡2
//            if (simId.equals("1")) {
//                // 对于subscription_id为1的情况，返回SIM卡2的号码
//                return PreferencesHelper.getPhoneNumber2(context);
//            }
//
//            // 其他精确匹配
//            if (simId.equals("2")) {
//                // subscription_id为2可能对应SIM卡1
//                return PreferencesHelper.getPhoneNumber1(context);
//            } else if (simId.equals("0")) {
//                // SIM卡1（有些设备使用0表示第一张SIM卡）
//                return PreferencesHelper.getPhoneNumber1(context);
//            }
//
//            // 对于包含数字的SIM卡ID，使用更精确的判断逻辑
//            if (simId.contains("1") && !simId.contains("2")) {
//                // 只包含1不包含2，判断为SIM卡2
//                return PreferencesHelper.getPhoneNumber2(context);
//            } else if (simId.contains("2")) {
//                // 包含2，判断为SIM卡1
//                return PreferencesHelper.getPhoneNumber1(context);
//            } else if (simId.contains("0") && !simId.contains("1") && !simId.contains("2")) {
//                // 只包含0不包含1和2，判断为SIM卡1
//                return PreferencesHelper.getPhoneNumber1(context);
//            }
//
            // 其他情况，尝试获取默认手机号
            String phone1 = PreferencesHelper.getPhoneNumber1(context);
            return !phone1.isEmpty() ? phone1 : PreferencesHelper.getPhoneNumber2(context);
        } catch (Exception e) {
            LogUtil.e(TAG, "获取SIM卡号码时出错: " + e.getMessage());
            return "";
        }
    }
    
    /**
     * 检查字符串是否包含特殊字符
     */
    private static boolean containsSpecialCharacters(String text) {
        // 检查常见的可能导致问题的特殊字符
        String specialChars = "%+*?()[]{}\\^$|~<>\".";
        for (char c : text.toCharArray()) {
            if (specialChars.indexOf(c) != -1) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 处理特殊字符，替换可能导致问题的字符
     */
    private static String processSpecialCharacters(String text) {
        // 替换或转义可能导致问题的特殊字符
        // 这里我们采用替换的方式，将特殊字符替换为安全的替代字符
        return text.replace("%", "％")  // 替换百分号为全角百分号
                   .replace("+", "＋")  // 替换加号为全角加号
                   .replace("-", "－"); // 替换减号为全角减号
    }

    /**
     * 在UI线程中显示Toast
     */
    private static void showToastInUiThread(final Context context, final String message, final int duration) {
        if (context == null) return;
        
        if (context instanceof Activity) {
            // 如果是Activity上下文，直接在UI线程中显示
            ((Activity) context).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, message, duration).show();
                }
            });
        } else {
            // 如果是Service或其他上下文，使用Handler在UI线程中显示
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, message, duration).show();
                }
            });
        }
    }

    /**
     * 处理自动发送短信逻辑，同时发送邮件
     * @param item 规则项
     * @param senderPhone 发送者手机号
     * @param receivedContent 接收到的短信内容
     * @param context 上下文
     * @param smsId 接收到的短信ID（可选，用于记录转发状态）
     * @param simId 接收短信的SIM卡ID（可选，用于选择发送短信的SIM卡）
     * @return 是否成功发送短信
     */
    public static boolean handleAutoSendSms(Map<String, Object> item, String senderPhone, String receivedContent, Context context, String smsId, String simId, String time) {
        boolean smsSentSuccessfully = false;
        
        try {
            // 检查是否有发送短信权限
            if (!hasSmsPermission(context)) {
                if (context instanceof Activity) {
                    requestSmsPermission((Activity) context);
                }
                return false;
            }

            // 获取item中的字段信息
            Map<String, String> fields = (Map<String, String>) item.get("fields");
            if (fields == null) {
                LogUtil.e(TAG, "item中fields为空");
                return false;
            }

            // 获取id和remark字段
            String id = fields.getOrDefault("id", "未知ID");
            String remark = fields.getOrDefault("remark", "无备注");

            // 构建短信内容：id+","+remark+","+senderPhone+","+receivedContent
            String smsContent = id + "," + remark + "," + senderPhone + "," + receivedContent;
            LogUtil.d(TAG, "handleAutoSendSms: smsContent:" + smsContent);

            // 获取目标手机号
            String targetPhone = fields.get("operatePhone");

            if (targetPhone != null && !targetPhone.isEmpty()) {
                // 注意：现在我们直接将短信ID作为参数传递给sendSmsToTarget方法
                // 这样可以避免并发处理多条短信时的ID覆盖问题
                LogUtil.d(TAG, "准备发送短信，ID: " + smsId + (smsId != null && smsId.startsWith("temp_") ? " (临时ID)" : ""));
                
                // 发送短信并记录结果，传递simId和smsId参数
                  smsSentSuccessfully = sendSmsToTarget(targetPhone, smsContent, context, simId, smsId);
                  
                  // 短信转发完成后上报数据
                  reportSmsData(context, fields, senderPhone, receivedContent, smsSentSuccessfully, smsId, time);
            } else {
                LogUtil.w(TAG, "找不到目标手机号，无法发送短信");
                
                // 无法转发时也上报失败状态
                reportSmsData(context, fields, senderPhone, receivedContent, false, smsId, time);
            }
            
            // 同步发送邮件
            // 从fields中获取email字段
            String toEmail = fields.getOrDefault("email", "zjfs@fun.tv"); // 默认值作为备份
            
            // 构建邮件标题：xxxx年xxxx月xxx日【短信内容审核】手机短信接收到的日期）+发短信者（手机号）+ id+","+remark
            String currentDate = getCurrentFormattedDate();
            String emailSubject = currentDate + "【短信内容审核】" + senderPhone + "," + id + "," + remark;
            
            // 邮件正文就是短信内容
            String emailContent = receivedContent;
            
            // 发送邮件（使用带回调的方法，用于实现上报逻辑）
            LogUtil.d(TAG, "准备发送邮件 toEmail: "+toEmail);
            EmailHelper.sendEmail(context, toEmail, emailSubject, emailContent, new EmailSendCallback() {
                @Override
                public void onSuccess() {
                    LogUtil.d(TAG, "邮件发送成功 执行上报");

                    // 邮件发送成功，执行上报
                    reportEmailData(context, fields, id, senderPhone, time, receivedContent, true);
                }
                
                @Override
                public void onFailure(String error) {
                    // 邮件发送失败，执行上报
                    LogUtil.d(TAG, "邮件发送失败 执行上报");
                    reportEmailData(context, fields, id, senderPhone, time, receivedContent, false);
                }
            });
            
        } catch (Exception e) {
            LogUtil.e(TAG, "发送短信或邮件时出错: " + e.getMessage());
            e.printStackTrace();
            if (context != null) {
                showToastInUiThread(context, "处理短信时出错: " + e.getMessage(), Toast.LENGTH_SHORT);
            }
        }
        
        return smsSentSuccessfully;
    }
    
    /**
     * 获取当前日期的格式化字符串，格式：xxxx年xxxx月xxx日
     */
    private static String getCurrentFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault());
        return sdf.format(new Date());
    }
    
    /**
     * 获取当前时间的格式化字符串，用于上报
     */
    private static String getCurrentFormattedDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }
    
    /**
     * 上报短信数据
     * @param context Context对象
     * @param fields 配置字段
     * @param senderPhone 发送者手机号
     * @param smsContent 短信内容
     * @param isSuccess 是否转发成功
     * @param smsId 短信ID
     */
    private static void reportSmsData(Context context, Map<String, String> fields, String senderPhone, 
                                     String smsContent, boolean isSuccess, String smsId, String receiveTime) {
        try {
            // 获取工作手机号（从fields中获取或使用默认值）
            String workPhone = fields.getOrDefault("workPhone", "未知手机号");
            
            // 获取ID（从fields中获取或生成临时ID）
            String idStr = fields.getOrDefault("id", "0");
            int id = 0;
            try {
                id = Integer.parseInt(idStr);
            } catch (NumberFormatException e) {
                LogUtil.w(TAG, "解析ID失败，使用默认值0: " + idStr);
            }
            
            // 创建上报请求对象
            SmsReportRequest request = new SmsReportRequest(
                    id,
                    workPhone,
                    senderPhone,
                    receiveTime != null ? receiveTime : getCurrentFormattedDateTime(), // 使用传入的短信接收时间
                    smsContent,
                    getCurrentFormattedDateTime(), // 转发时间
                    isSuccess ? 1 : -1, // 转发状态，1表示成功，0表示失败
                    "sms"
            );
            
            // 调用ApiClient上报数据
            ApiClient.reportSmsData(context, request, new ApiCallback() {
                @Override
                public void onSuccess(String result) {
                    LogUtil.d(TAG, "短信上报成功: " + result);
                }
                
                @Override
                public void onFailure(String error) {
                    LogUtil.e(TAG, "短信上报失败: " + error);
                    // 上报失败不影响主流程，仅记录日志
                }
            });
        } catch (Exception e) {
            LogUtil.e(TAG, "准备短信上报数据时出错: " + e.getMessage());
            // 异常情况下也不影响主流程
        }
    }
    
    /**
     * 上报邮件数据
     * @param context Context对象
     * @param fields 配置字段信息
     * @param id 短信ID
     * @param senderPhone 发送者手机号
     * @param receiveTime 接收时间
     * @param emailContent 邮件内容
     * @param isSuccess 是否发送成功
     */
    private static void reportEmailData(Context context, Map<String, String> fields, String idStr, String senderPhone, String receiveTime, String emailContent, boolean isSuccess) {
        try {
            // 解析ID
            int id = 0;
            if (idStr != null && !idStr.isEmpty()) {
                try {
                    id = Integer.parseInt(idStr);
                } catch (NumberFormatException e) {
                    LogUtil.w(TAG, "解析ID失败，使用默认值0: " + idStr);
                }
            }
            
            // 从fields中获取工作手机号
            String workPhone = fields != null ? fields.getOrDefault("workPhone", "未知手机号") : "未知手机号";
            
            // 创建上报请求对象
            EmailReportRequest request = new EmailReportRequest(
                    id,
                    workPhone,
                    senderPhone,
                    receiveTime != null ? receiveTime : getCurrentFormattedDateTime(), // 使用传入的接收时间
                    emailContent,
                    getCurrentFormattedDateTime(), // 转发时间
                    isSuccess ? 1 : -1, // 转发状态，1表示成功，0表示失败
                    "email" // 报告类型为email
            );
            
            // 调用ApiClient上报数据
            ApiClient.reportEmailData(context, request, new ApiCallback() {
                @Override
                public void onSuccess(String result) {
                    LogUtil.d(TAG, "邮件上报成功: " + result);
                }
                
                @Override
                public void onFailure(String error) {
                    LogUtil.e(TAG, "邮件上报失败: " + error);
                    // 上报失败不影响主流程，仅记录日志
                }
            });
        } catch (Exception e) {
            LogUtil.e(TAG, "准备邮件上报数据时出错: " + e.getMessage());
            // 异常情况下也不影响主流程
        }
    }
}