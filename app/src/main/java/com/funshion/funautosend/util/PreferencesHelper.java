package com.funshion.funautosend.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * SharedPreferences助手类，负责处理SharedPreferences的读写操作
 */
public class PreferencesHelper {
    // SharedPreferences键名
    public static final String PREFS_NAME = "PhoneNumberPrefs";
    public static final String KEY_PHONE_NUMBER_1 = "phoneNumber1";
    public static final String KEY_PHONE_NUMBER_2 = "phoneNumber2";
    public static final String KEY_API_RESULT_LIST = "apiResultList";
    public static final String KEY_TARGET_LIST = "targetList";
    public static final String KEY_LAST_UPDATE_TIME = "lastUpdateTime";

    /**
     * 获取SharedPreferences实例
     */
    private static SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 获取SharedPreferences编辑器实例
     */
    private static SharedPreferences.Editor getEditor(Context context) {
        return getPreferences(context).edit();
    }

    /**
     * 保存手机号到SharedPreferences
     */
    public static void savePhoneNumbers(Context context, String phoneNumber1, String phoneNumber2) {
        SharedPreferences.Editor editor = getEditor(context);
        editor.putString(KEY_PHONE_NUMBER_1, phoneNumber1);
        editor.putString(KEY_PHONE_NUMBER_2, phoneNumber2);
        editor.apply();
    }

    /**
     * 获取保存的第一个手机号
     */
    public static String getPhoneNumber1(Context context) {
        return getPreferences(context).getString(KEY_PHONE_NUMBER_1, "");
    }

    /**
     * 获取保存的第二个手机号
     */
    public static String getPhoneNumber2(Context context) {
        return getPreferences(context).getString(KEY_PHONE_NUMBER_2, "");
    }

    /**
     * 清除所有保存的数据
     */
    public static void clearAllData(Context context) {
        SharedPreferences.Editor editor = getEditor(context);
        editor.clear();
        editor.apply();
    }

    /**
     * 保存String类型的值
     */
    public static void putString(Context context, String key, String value) {
        SharedPreferences.Editor editor = getEditor(context);
        editor.putString(key, value);
        editor.apply();
    }

    /**
     * 获取String类型的值
     */
    public static String getString(Context context, String key, String defaultValue) {
        return getPreferences(context).getString(key, defaultValue);
    }

    /**
     * 保存Int类型的值
     */
    public static void putInt(Context context, String key, int value) {
        SharedPreferences.Editor editor = getEditor(context);
        editor.putInt(key, value);
        editor.apply();
    }

    /**
     * 获取Int类型的值
     */
    public static int getInt(Context context, String key, int defaultValue) {
        return getPreferences(context).getInt(key, defaultValue);
    }

    /**
     * 保存Boolean类型的值
     */
    public static void putBoolean(Context context, String key, boolean value) {
        SharedPreferences.Editor editor = getEditor(context);
        editor.putBoolean(key, value);
        editor.apply();
    }

    /**
     * 获取Boolean类型的值
     */
    public static boolean getBoolean(Context context, String key, boolean defaultValue) {
        return getPreferences(context).getBoolean(key, defaultValue);
    }

    /**
     * 保存API结果列表到本地
     */
    public static void saveApiResultList(Context context, List<Map<String, Object>> resultList) {
        Gson gson = new Gson();
        String json = gson.toJson(resultList);
        putString(context, KEY_API_RESULT_LIST, json);
        // 保存更新时间
        putLong(context, KEY_LAST_UPDATE_TIME, System.currentTimeMillis());
    }

    /**
     * 从本地获取API结果列表
     */
    public static List<Map<String, Object>> getApiResultList(Context context) {
        String json = getString(context, KEY_API_RESULT_LIST, "");
        if (json.isEmpty()) {
            return null;
        }
        Gson gson = new Gson();
        Type type = new TypeToken<List<Map<String, Object>>>(){}.getType();
        return gson.fromJson(json, type);
    }

    /**
     * 保存目标列表到本地
     */
    public static void saveTargetList(Context context, List<Map<String, Object>> targetList) {
        Gson gson = new Gson();
        String json = gson.toJson(targetList);
        putString(context, KEY_TARGET_LIST, json);
    }

    /**
     * 从本地获取目标列表
     */
    public static List<Map<String, Object>> getTargetList(Context context) {
        String json = getString(context, KEY_TARGET_LIST, "");
        if (json.isEmpty()) {
            return null;
        }
        Gson gson = new Gson();
        Type type = new TypeToken<List<Map<String, Object>>>(){}.getType();
        return gson.fromJson(json, type);
    }

    /**
     * 保存Long类型的值
     */
    public static void putLong(Context context, String key, long value) {
        SharedPreferences.Editor editor = getEditor(context);
        editor.putLong(key, value);
        editor.apply();
    }

    /**
     * 获取Long类型的值
     */
    public static long getLong(Context context, String key, long defaultValue) {
        return getPreferences(context).getLong(key, defaultValue);
    }

    /**
     * 保存最后更新时间（当前时间戳）
     */
    public static void saveLastUpdateTime(Context context) {
        putLong(context, KEY_LAST_UPDATE_TIME, System.currentTimeMillis());
    }
    
    /**
     * 获取最后更新时间
     */
    public static long getLastUpdateTime(Context context) {
        return getLong(context, KEY_LAST_UPDATE_TIME, 0);
    }
}