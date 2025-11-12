package com.funshion.funautosend.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import com.funshion.funautosend.util.KeepAliveManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 无声音乐播放服务，用于增强应用在后台的保活能力
 * 通过播放一段无声音乐（静音音频），让系统认为应用正在播放音乐
 * 从而降低被系统回收的概率
 */
public class AudioPlayerService extends Service {

    private static final String TAG = "AudioPlayerService";
    private static final String CHANNEL_ID = "audio_keep_alive_channel";
    private static final int NOTIFICATION_ID = 1002;
    private AudioTrack audioTrack; // 音频轨道，用于播放静音音频流
    private boolean isPlaying = false; // 当前是否正在播放
    private ExecutorService executorService; // 用于异步播放的线程池
    private Thread playbackThread; // 播放线程

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: AudioPlayerService 已创建");
        
        // 创建通知渠道（Android 8.0及以上）
        createNotificationChannel();
        
        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification());
        
        executorService = Executors.newSingleThreadExecutor();
        
        // 初始化AudioTrack
        initAudioTrack();
        
        // 移除不必要的保活检查，避免循环触发
    }
    
    /**
     * 创建通知渠道
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "保持应用运行",
                    NotificationManager.IMPORTANCE_LOW); // 使用低优先级，减少用户干扰
            channel.setDescription("用于保持应用在后台稳定运行");
            channel.setSound(null, null); // 无声音
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
    
    /**
     * 创建前台服务通知
     */
    private Notification createNotification() {
        NotificationCompat.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new NotificationCompat.Builder(this, CHANNEL_ID);
        } else {
            builder = new NotificationCompat.Builder(this);
        }
        
        return builder
                .setContentTitle("自动发送服务运行中")
                .setContentText("保持应用在后台稳定运行")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSound(null) // 无声音
                .setVibrate(null) // 无震动
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: 启动AudioPlayerService");
        startSilentPlayback();
        
        // 移除不必要的保活检查，避免循环触发
        
        return START_STICKY; // 确保服务被杀死后能够自动重启
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: AudioPlayerService 已销毁");
        
        // 停止前台服务
        stopForeground(true);
        
        stopSilentPlayback();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        // 尝试重启服务
        restartServiceWithDelay();
        
        // 移除不必要的保活检查，避免循环触发
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d(TAG, "onTaskRemoved: 任务被移除，尝试重启服务");
        restartServiceWithDelay();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 初始化AudioTrack用于播放静音音频流
     */
    private void initAudioTrack() {
        try {
            // 配置音频参数
            int sampleRate = 44100; // 标准采样率
            int channelConfig = AudioFormat.CHANNEL_OUT_MONO; // 单声道
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT; // 16位PCM编码
            int bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2; // 缓冲区大小
            
            // 创建AudioTrack实例
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build();
                
                AudioFormat format = new AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(audioFormat)
                        .setChannelMask(channelConfig)
                        .build();
                
                audioTrack = new AudioTrack.Builder()
                        .setAudioAttributes(audioAttributes)
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(bufferSize)
                        .build();
            } else {
                // 兼容旧版本API
                audioTrack = new AudioTrack(
                        AudioManager.STREAM_MUSIC,  // 使用媒体流类型
                        sampleRate,               // 采样率
                        channelConfig,            // 声道配置
                        audioFormat,              // 音频格式
                        bufferSize,               // 缓冲区大小
                        AudioTrack.MODE_STREAM);  // 流模式
            }
            
            // 设置音量为0（静音）
            audioTrack.setVolume(0.0f);
            
            Log.d(TAG, "AudioTrack初始化成功");
        } catch (Exception e) {
            Log.e(TAG, "AudioTrack初始化失败", e);
            audioTrack = null;
        }
    }

    /**
     * 开始静音音频流播放
     */
    private void startSilentPlayback() {
        try {
            if (isPlaying || audioTrack == null) {
                return;
            }

            // 启动播放线程
            playbackThread = new Thread(() -> {
                try {
                    // 准备播放
                    audioTrack.play();
                    isPlaying = true;
                    Log.d(TAG, "静音音频流播放已开始");
                    
                    // 生成并写入静音数据（16位PCM数据，值为0表示静音）
                    short[] silentBuffer = new short[1024]; // 静音缓冲区，所有值默认为0
                    
                    // 持续播放静音数据
                    while (isPlaying && !Thread.interrupted()) {
                        // 检查AudioTrack状态
                        if (audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                            Log.w(TAG, "AudioTrack状态异常: " + audioTrack.getPlayState());
                            audioTrack.play(); // 尝试恢复播放
                        }
                        
                        // 写入静音数据
                        audioTrack.write(silentBuffer, 0, silentBuffer.length);
                        
                        // 短暂休眠，避免CPU过度占用
                        Thread.sleep(10);
                    }
                } catch (InterruptedException e) {
                    Log.d(TAG, "播放线程被中断");
                } catch (Exception e) {
                    Log.e(TAG, "播放过程中发生错误", e);
                    // 发生错误时重置播放状态
                    isPlaying = false;
                    // 尝试重新初始化和播放
                    initAudioTrack();
                    startSilentPlayback();
                }
            });
            
            playbackThread.start();
        } catch (Exception e) {
            Log.e(TAG, "启动静音播放失败", e);
        }
    }

    /**
     * 停止静音音频流播放
     */
    private void stopSilentPlayback() {
        try {
            isPlaying = false;
            
            // 中断播放线程
            if (playbackThread != null && playbackThread.isAlive()) {
                playbackThread.interrupt();
                try {
                    playbackThread.join(1000); // 等待线程结束，最多1秒
                } catch (InterruptedException e) {
                    Log.e(TAG, "等待播放线程结束时被中断", e);
                }
                playbackThread = null;
            }
            
            // 释放AudioTrack资源
            if (audioTrack != null) {
                try {
                    audioTrack.stop();
                    audioTrack.release();
                } catch (Exception e) {
                    Log.e(TAG, "释放AudioTrack异常", e);
                }
                audioTrack = null;
            }
            
            Log.d(TAG, "静音音频流播放已停止");
        } catch (Exception e) {
            Log.e(TAG, "停止静音播放失败", e);
        }
    }

    /**
     * 延迟重启服务
     */
    private void restartServiceWithDelay() {
        Handler handler = new Handler();
        handler.postDelayed(() -> {
            try {
                Intent restartIntent = new Intent(AudioPlayerService.this, AudioPlayerService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(restartIntent);
                } else {
                    startService(restartIntent);
                }
                Log.d(TAG, "尝试重启AudioPlayerService");
            } catch (Exception e) {
                Log.e(TAG, "重启服务失败", e);
            }
        }, 1000); // 1秒后重启
    }
}