package com.funshion.funautosend.util;

import android.os.Handler;
import android.os.SystemClock;
import android.widget.TextView;

/**
 * 倒计时助手类，负责处理倒计时功能
 */
public class CountdownHelper {
    private Handler handler;
    private Runnable countdownRunnable;
    private TextView countdownTextView;
    private int countdownSeconds;
    private CountdownListener listener;
    private int remainingSeconds; // 用于跟踪剩余秒数
    private long lastUpdateTime; // 上次更新时间，用于在后台恢复时计算准确的剩余时间

    /**
     * 倒计时监听器接口
     */
    public interface CountdownListener {
        void onCountdownComplete();
    }

    /**
     * 构造函数
     * @param countdownTextView 显示倒计时的TextView
     * @param countdownSeconds 倒计时秒数
     * @param listener 倒计时监听器
     */
    public CountdownHelper(TextView countdownTextView, int countdownSeconds, CountdownListener listener) {
        this.handler = new Handler();
        this.countdownTextView = countdownTextView;
        this.countdownSeconds = countdownSeconds;
        this.remainingSeconds = countdownSeconds;
        this.listener = listener;
    }

    /**
     * 构造函数
     * @param countdownTextView 显示倒计时的TextView
     * @param countdownSeconds 倒计时秒数
     */
    public CountdownHelper(TextView countdownTextView, int countdownSeconds) {
        this(countdownTextView, countdownSeconds, null);
    }

    /**
     * 启动倒计时
     */
    public void startCountdown() {
        // 停止之前的倒计时
        stopCountdown();

        // 重置剩余秒数
        remainingSeconds = countdownSeconds;
        lastUpdateTime = SystemClock.elapsedRealtime();

        // 创建倒计时Runnable
        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (remainingSeconds > 0) {
                    // 计算实际经过的时间，确保倒计时准确
                    long currentTime = SystemClock.elapsedRealtime();
                    long elapsedTime = currentTime - lastUpdateTime;
                    int secondsPassed = (int) (elapsedTime / 1000);
                    
                    if (secondsPassed > 0) {
                        remainingSeconds = Math.max(0, remainingSeconds - secondsPassed);
                        lastUpdateTime = currentTime;
                    }
                    
                    int minutes = remainingSeconds / 60;
                    int seconds = remainingSeconds % 60;

                    // 格式化倒计时显示
                    String countdownText = String.format("%02d:%02d后自动刷新", minutes, seconds);
                    if (countdownTextView != null) {
                        countdownTextView.setText(countdownText);
                    }

                    // 继续倒计时
                    handler.postDelayed(this, 1000);
                } else {
                    // 倒计时结束，通知监听器
                    if (listener != null) {
                        listener.onCountdownComplete();
                    }
                }
            }
        };

        // 开始倒计时
        handler.post(countdownRunnable);
    }

    /**
     * 停止倒计时
     */
    public void stopCountdown() {
        if (countdownRunnable != null) {
            handler.removeCallbacks(countdownRunnable);
            countdownRunnable = null;
        }
    }

    /**
     * 恢复倒计时
     * 当应用从后台回到前台时调用此方法
     */
    public void resumeCountdown() {
        // 如果倒计时正在运行，重新计算剩余时间并继续
        if (countdownRunnable != null && remainingSeconds > 0) {
            // 停止当前的倒计时
            stopCountdown();
            
            // 计算实际已经过去的时间
            long currentTime = SystemClock.elapsedRealtime();
            long elapsedTime = currentTime - lastUpdateTime;
            int secondsPassed = (int) (elapsedTime / 1000);
            
            // 更新剩余秒数
            remainingSeconds = Math.max(0, remainingSeconds - secondsPassed);
            
            // 重新启动倒计时
            startCountdown();
        } else if (countdownRunnable == null && remainingSeconds <= 0) {
            // 如果倒计时已经结束，直接回调完成方法
            if (listener != null) {
                listener.onCountdownComplete();
            }
        } else if (countdownRunnable == null) {
            // 如果倒计时没有运行但还有剩余时间，启动倒计时
            startCountdown();
        }
    }

    /**
     * 设置倒计时时长（秒）
     */
    public void setCountdownSeconds(int seconds) {
        this.countdownSeconds = seconds;
        this.remainingSeconds = seconds;
    }

    /**
     * 设置倒计时监听器
     */
    public void setCountdownListener(CountdownListener listener) {
        this.listener = listener;
    }

    /**
     * 获取剩余秒数
     */
    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    /**
     * 检查倒计时是否正在运行
     */
    public boolean isRunning() {
        return countdownRunnable != null;
    }
}