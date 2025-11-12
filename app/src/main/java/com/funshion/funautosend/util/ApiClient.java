package com.funshion.funautosend.util;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import com.funshion.funautosend.util.PreferencesHelper;

/**
 * API客户端类，负责处理API请求和数据解析
 */
public class ApiClient {
    private static final String TAG = "ApiClient";
    //测试环境API地址
    private static final String ALPHA_API_URL = "http://172.17.5.156:8089/service/openapi/query/smsForwardConfig";
    // 正式环境API地址
    private static final String API_URL = "https://mgc.funshion.com/service/openapi/query/smsForwardConfig";

    private static final String ALPHA_REPORT_URL = "http://172.17.5.156:8089/service/openapi/reportSmsForward";
    private static final String REPORT_URL = "https://mgc.funshion.com/service/openapi/reportSmsForward";
    // 授权令牌
    private static final String AUTH_TOKEN = "Bearer sk-716430cd7c376bc82ca6f2e014bbb3bf1748505057779RLUCKDCJg12S";
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS) // 连接超时设置为30秒
            .readTimeout(30, TimeUnit.SECONDS)    // 读取超时设置为30秒
            .writeTimeout(30, TimeUnit.SECONDS)   // 写入超时设置为30秒
            .retryOnConnectionFailure(true)       // 连接失败时自动重试
            .build();
    private static final Gson gson = new Gson();

    /**
     * 接口回调接口
     */
    public interface ApiCallback {
        void onSuccess(String result);
        void onFailure(String error);
    }

    /**
     * 发送API请求（带电量参数）
     * @param context Context对象，用于访问偏好设置
     * @param batteryPercentage 电池电量百分比
     * @param callback 回调接口，用于处理请求结果
     */
    public static void fetchApiData(final Context context, final int batteryPercentage, final ApiCallback callback) {
        // 获取保存的手机号
        String savedPhone1 = PreferencesHelper.getPhoneNumber1(context);
        String savedPhone2 = PreferencesHelper.getPhoneNumber2(context);
        
        // 构建phones参数，格式为"121,121"
        StringBuilder phonesBuilder = new StringBuilder();
        if (savedPhone1 != null && !savedPhone1.isEmpty()) {
            phonesBuilder.append(savedPhone1);
        }
        if (savedPhone2 != null && !savedPhone2.isEmpty()) {
            if (phonesBuilder.length() > 0) {
                phonesBuilder.append(",");
            }
            phonesBuilder.append(savedPhone2);
        }
        String phonesParam = phonesBuilder.toString();
        
        // 构建请求URL，添加phones参数
        StringBuilder urlBuilder = new StringBuilder(API_URL);
        boolean hasParams = false;
        
        if (phonesParam.length() > 0) {
            urlBuilder.append(API_URL.contains("?") ? "&" : "?")
                     .append("phones=").append(phonesParam);
            hasParams = true;
        }
        
        // 直接添加battery参数
        urlBuilder.append(hasParams ? "&" : "?")
                 .append("battery=").append(batteryPercentage);
        
        String requestUrl = urlBuilder.toString();
        
        // 打印完整的请求参数
        Log.d(TAG, "发送API请求: URL=" + requestUrl + ", Authorization=" + AUTH_TOKEN + ", battery=" + batteryPercentage);
        
        Request request = new Request.Builder()
                .url(requestUrl)
                .get()
                // 添加Authorization请求头
                .header("Authorization", AUTH_TOKEN)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "请求失败: " + e.getMessage());
                if (callback != null) {
                    callback.onFailure("请求失败: " + e.getMessage());
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        final String result = response.body().string();
                        
                        // 保存数据到本地存储
                        if (context != null) {
                            // 获取已保存的手机号
                            String savedPhone1 = PreferencesHelper.getPhoneNumber1(context);
                            String savedPhone2 = PreferencesHelper.getPhoneNumber2(context);
                            
                            // 解析API结果
                            ParseResult parseResult = parseApiResult(result, savedPhone1, savedPhone2);
                            
                            // 保存解析结果到本地存储
                            PreferencesHelper.saveApiResultList(context, parseResult.getApiResultList());
                            PreferencesHelper.saveTargetList(context, parseResult.getTargetList());
                            
                            // 保存最后更新时间
                            PreferencesHelper.saveLastUpdateTime(context);
                            
                            Log.d(TAG, "API数据已保存到本地存储");
                        }
                        
                        if (callback != null) {
                            callback.onSuccess(result);
                        }
                    } else {
                        if (callback != null) {
                            callback.onFailure("请求失败，响应码: " + response.code());
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "处理响应失败: " + e.getMessage());
                    if (callback != null) {
                        callback.onFailure("处理响应失败: " + e.getMessage());
                    }
                } finally {
                    if (response.body() != null) {
                        response.body().close();
                    }
                }
            }
        });
    }

    /**
     * 解析API返回结果
     * @param result API返回的JSON字符串
     * @param savedPhone1 保存的第一个手机号
     * @param savedPhone2 保存的第二个手机号
     * @return 包含解析后数据的结果对象
     */
    public static ParseResult parseApiResult(String result, String savedPhone1, String savedPhone2) {
        List<Map<String, Object>> apiResultList = new ArrayList<>();
        List<Map<String, Object>> targetList = new ArrayList<>();

        try {
            JsonElement jsonElement = JsonParser.parseString(result);
            if (jsonElement.isJsonObject()) {
                JsonObject jsonObject = jsonElement.getAsJsonObject();

                if (jsonObject.has("data")) {
                    JsonElement dataElement = jsonObject.get("data");
                    if (dataElement.isJsonArray()) {
                        JsonArray dataArray = dataElement.getAsJsonArray();
                        for (int i = 0; i < dataArray.size(); i++) {
                            if (dataArray.get(i).isJsonObject()) {
                                processDataItem(dataArray.get(i).getAsJsonObject(), i + 1, savedPhone1, savedPhone2, apiResultList, targetList);
                            }
                        }
                    } else if (dataElement.isJsonObject()) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("title", "Data Object");
                        Map<String, String> fields = new HashMap<>();
                        JsonObject dataObject = dataElement.getAsJsonObject();
                        for (String key : dataObject.keySet()) {
                            fields.put(key, dataObject.get(key).getAsString());
                        }
                        item.put("fields", fields);
                        apiResultList.add(item);
                    } else {
                        Map<String, Object> item = new HashMap<>();
                        item.put("title", "Data");
                        Map<String, String> fields = new HashMap<>();
                        fields.put("data", dataElement.getAsString());
                        item.put("fields", fields);
                        apiResultList.add(item);
                    }
                } else {
                    Map<String, Object> item = new HashMap<>();
                    item.put("title", "配置列表");
                    Map<String, String> fields = new HashMap<>();
                    fields.put("原始数据", result);
                    item.put("fields", fields);
                    apiResultList.add(item);
                }
            } else {
                Map<String, Object> item = new HashMap<>();
                item.put("title", "配置列表");
                Map<String, String> fields = new HashMap<>();
                fields.put("原始数据", result);
                item.put("fields", fields);
                apiResultList.add(item);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "JSON解析错误: " + e.getMessage());
            Map<String, Object> item = new HashMap<>();
            item.put("title", "配置列表(解析失败)");
            Map<String, String> fields = new HashMap<>();
            fields.put("错误信息", e.getMessage());
            fields.put("原始数据", result);
            item.put("fields", fields);
            apiResultList.add(item);
        }

        return new ParseResult(apiResultList, targetList);
    }

    /**
     * 处理单个数据项
     */
    private static void processDataItem(JsonObject itemObject, int index, String savedPhone1, String savedPhone2,
                                       List<Map<String, Object>> apiResultList, List<Map<String, Object>> targetList) {
        try {
            Map<String, Object> item = new HashMap<>();
            item.put("title", "Item " + index);

            Map<String, String> fields = new HashMap<>();
            for (String key : itemObject.keySet()) {
                JsonElement element = itemObject.get(key);
                fields.put(key, element.isJsonPrimitive() ? element.getAsString() : element.toString());
            }

            boolean isMatch = false;
            String matchedWorkPhone = null;
            if (fields.containsKey("workPhone")) {
                String workPhone = fields.get("workPhone");
                if (workPhone.equals(savedPhone1) || workPhone.equals(savedPhone2)) {
                    isMatch = true;
                    matchedWorkPhone = workPhone;
                }
            }

            item.put("fields", fields);

            if (isMatch) {
                item.put("isMatch", true);
                item.put("matchedWorkPhone", matchedWorkPhone);
                targetList.add(new HashMap<>(item));
            }

            apiResultList.add(item);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 解析结果类，包含API结果列表和匹配到的目标列表
     */
    public static class ParseResult {
        private List<Map<String, Object>> apiResultList;
        private List<Map<String, Object>> targetList;

        public ParseResult(List<Map<String, Object>> apiResultList, List<Map<String, Object>> targetList) {
            this.apiResultList = apiResultList;
            this.targetList = targetList;
        }

        public List<Map<String, Object>> getApiResultList() {
            return apiResultList;
        }

        public List<Map<String, Object>> getTargetList() {
            return targetList;
        }
    }
    
    /**
     * 短信上报请求参数类
     */
    public static class SmsReportRequest {
        private int id;
        private String workPhone;
        private String smsSender;
        private String smsReceiveTime;
        private String smsContent;
        private String forwardTime;
        private int forwardStatus;
        private String reportType;
        
        public SmsReportRequest(int id, String workPhone, String smsSender, String smsReceiveTime, 
                               String smsContent, String forwardTime, int forwardStatus, String reportType) {
            this.id = id;
            this.workPhone = workPhone;
            this.smsSender = smsSender;
            this.smsReceiveTime = smsReceiveTime;
            this.smsContent = smsContent;
            this.forwardTime = forwardTime;
            this.forwardStatus = forwardStatus;
            this.reportType = reportType;
        }
        
        // Getters and setters can be added if needed
    }
    
    /**
     * 邮件上报请求参数类
     */
    public static class EmailReportRequest {
        private int id;
        private String workPhone;
        private String smsSender;
        private String smsReceiveTime;
        private String smsContent;
        private String forwardTime;
        private int forwardStatus;
        private String reportType;
        
        public EmailReportRequest(int id, String workPhone, String smsSender, String smsReceiveTime,
                               String smsContent, String forwardTime, int forwardStatus, String reportType) {
            this.id = id;
            this.workPhone = workPhone;
            this.smsSender = smsSender;
            this.smsReceiveTime = smsReceiveTime;
            this.smsContent = smsContent;
            this.forwardTime = forwardTime;
            this.forwardStatus = forwardStatus;
            this.reportType = reportType;
        }
        
        // Getters and setters can be added if needed
    }
    
    /**
     * 上报短信数据
     * @param context Context对象
     * @param smsReportRequest 短信上报请求参数
     * @param callback 回调接口，用于处理请求结果
     */
    public static void reportSmsData(final Context context, final SmsReportRequest smsReportRequest, final ApiCallback callback) {
        try {
            // 转换请求参数为JSON字符串
            String jsonBody = gson.toJson(smsReportRequest);
            
            // 打印完整的请求参数
            Log.d(TAG, "上报短信数据: URL=" + REPORT_URL + ", RequestBody=" + jsonBody);
            
            // 创建请求体
            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(jsonBody, JSON);
            
            // 构建请求
            Request request = new Request.Builder()
                    .url(REPORT_URL)
                    .post(body)
                    // 添加Authorization请求头
                    .header("Authorization", AUTH_TOKEN)
                    .build();
            
            // 发送请求
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "短信上报失败: " + e.getMessage());
                    if (callback != null) {
                        callback.onFailure("短信上报失败: " + e.getMessage());
                    }
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (response.isSuccessful() && response.body() != null) {
                            final String result = response.body().string();
                            // 移除重复的成功日志打印，让调用者在回调中处理
                            if (callback != null) {
                                callback.onSuccess(result);
                            }
                        } else {
                            String errorMsg = "短信上报失败，响应码: " + response.code();
                            Log.e(TAG, errorMsg);
                            if (callback != null) {
                                callback.onFailure(errorMsg);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "处理短信上报响应失败: " + e.getMessage());
                        if (callback != null) {
                            callback.onFailure("处理短信上报响应失败: " + e.getMessage());
                        }
                    } finally {
                        if (response.body() != null) {
                            response.body().close();
                        }
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "准备短信上报请求失败: " + e.getMessage());
            if (callback != null) {
                callback.onFailure("准备短信上报请求失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 上报邮件数据
     * @param context Context对象
     * @param emailReportRequest 邮件上报请求参数
     * @param callback 回调接口，用于处理请求结果
     */
    public static void reportEmailData(final Context context, final EmailReportRequest emailReportRequest, final ApiCallback callback) {
        try {
            // 转换请求参数为JSON字符串
            String jsonBody = gson.toJson(emailReportRequest);
            
            // 打印完整的请求参数
            Log.d(TAG, "上报邮件数据: URL=" + REPORT_URL + ", RequestBody=" + jsonBody);
            
            // 创建请求体
            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(jsonBody, JSON);
            
            // 构建请求
            Request request = new Request.Builder()
                    .url(REPORT_URL)
                    .post(body)
                    // 添加Authorization请求头
                    .header("Authorization", AUTH_TOKEN)
                    .build();
            
            // 发送请求
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "邮件上报失败: " + e.getMessage());
                    if (callback != null) {
                        callback.onFailure("邮件上报失败: " + e.getMessage());
                    }
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (response.isSuccessful() && response.body() != null) {
                            final String result = response.body().string();
                            // 移除重复的成功日志打印，让调用者在回调中处理
                            if (callback != null) {
                                callback.onSuccess(result);
                            }
                        } else {
                            String errorMsg = "邮件上报失败，响应码: " + response.code();
                            Log.e(TAG, errorMsg);
                            if (callback != null) {
                                callback.onFailure(errorMsg);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "处理邮件上报响应失败: " + e.getMessage());
                        if (callback != null) {
                            callback.onFailure("处理邮件上报响应失败: " + e.getMessage());
                        }
                    } finally {
                        if (response.body() != null) {
                            response.body().close();
                        }
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "准备邮件上报请求失败: " + e.getMessage());
            if (callback != null) {
                callback.onFailure("准备邮件上报请求失败: " + e.getMessage());
            }
        }
    }
}