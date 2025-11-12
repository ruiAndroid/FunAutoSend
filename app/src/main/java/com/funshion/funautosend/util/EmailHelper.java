package com.funshion.funautosend.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 * 邮件发送助手类，负责处理邮件发送逻辑
 */
public class EmailHelper {
    private static final String TAG = "EmailHelper";

    /**
     * 邮件发送回调接口
     */
    public interface EmailSendCallback {
        void onSuccess();
        void onFailure(String error);
    }

    // 使用自定义线程池替代AsyncTask
    private static final ExecutorService EMAIL_EXECUTOR = Executors.newFixedThreadPool(
            Math.max(3, Runtime.getRuntime().availableProcessors() * 2)); // 最小3个线程，最大CPU核心数*2
    private static final Handler UI_HANDLER = new Handler(Looper.getMainLooper());

    /**
     * 发送邮件
     * @param context 上下文
     * @param toEmail 收件人邮箱
     * @param subject 邮件主题
     * @param content 邮件内容
     */
    public static void sendEmail(final Context context, final String toEmail, final String subject, final String content) {
        sendEmailInternal(context, toEmail, subject, content, null);
    }

    /**
     * 发送邮件
     * @param context 上下文
     * @param toEmail 收件人邮箱
     * @param subject 邮件主题
     * @param content 邮件内容
     * @param callback 发送回调
     */
    public static void sendEmail(final Context context, final String toEmail, final String subject, final String content, final EmailSendCallback callback) {
        sendEmailInternal(context, toEmail, subject, content, callback);
    }

    /**
     * 内部邮件发送实现
     */
    private static void sendEmailInternal(final Context context, final String toEmail, final String subject, final String content, final EmailSendCallback callback) {
        final Context appContext = context != null ? context.getApplicationContext() : null;
        
        EMAIL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                final boolean result = sendEmailInBackground(toEmail, subject, content);
                
                // 在UI线程执行回调
                UI_HANDLER.post(new Runnable() {
                    @Override
                    public void run() {
                        handleSendResult(appContext, result, callback);
                    }
                });
            }
        });
    }

    /**
     * 在后台线程执行邮件发送
     */
    private static boolean sendEmailInBackground(String toEmail, String subject, String content) {
        try {
            // 配置邮件服务器信息
            Properties props = new Properties();
            props.put("mail.transport.protocol", "SMTP");
            props.put("mail.smtp.host", "smtp.exmail.qq.com"); // foxmail邮箱SMTP服务器
            props.put("mail.smtp.port", "465"); // foxmail邮箱SMTP SSL端口(正确端口)
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.socketFactory.port", "465"); // SSL端口
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory"); // SSL工厂类
            props.put("mail.smtp.ssl.enable", "true"); // 启用SSL

            // 创建邮件会话
            Session session = Session.getInstance(props, new javax.mail.Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    // 发件人邮箱地址以及授权码
                    return new PasswordAuthentication("java_team@fun.tv", "Nihao123)");
                }
            });

            // 创建邮件消息
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress("java_team@fun.tv")); // 发件人邮箱必须与认证邮箱一致
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail)); // 收件人邮箱
            message.setSubject(subject); // 邮件主题
            message.setText(content); // 邮件内容

            // 发送邮件
            Transport.send(message);
            return true;
        } catch (MessagingException e) {
            Log.e("EmailTask", "邮件发送异常: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 获取当前日期的格式化字符串，格式：xxxx年xxxx月xxx日
     */
    public static String getCurrentFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault());
        return sdf.format(new Date());
    }

    /**
     * 邮件发送任务类，在后台线程执行邮件发送操作
     */
    // 已替换为自定义线程池实现，不再使用AsyncTask

    /**
     * 处理发送结果并在UI线程执行回调
     */
    private static void handleSendResult(Context context, boolean result, EmailSendCallback callback) {
        Log.d("EmailTask", "发送结果: " + result);
        if (result) {
            if (callback != null) {
                callback.onSuccess();
            } else if (context != null) {
                Log.d("EmailTask", "邮件发送成功");
                // Toast.makeText(context, "邮件发送成功", Toast.LENGTH_LONG).show();
            }
        } else {
            String errorMsg = "邮件发送失败，请检查：1)Foxmail邮箱已开启SMTP服务；2)授权码正确；3)网络连接正常";
            if (callback != null) {
                callback.onFailure(errorMsg);
            } else if (context != null) {
                Log.e("EmailTask", errorMsg);
                // Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show();
            }
        }
    }
}