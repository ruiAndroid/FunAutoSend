package com.funshion.funautosend.model;

/**
 * 短信实体类，用于存储短信相关信息
 */
public class SmsMessage {
    private String id;            // 短信ID
    private String address;       // 原始地址字段
    private String sender;        // 发件人
    private String recipient;     // 收件人
    private String body;          // 短信内容
    private String date;          // 格式化的时间
    private String type;          // 短信类型
    private String simId;         // SIM卡ID
    
    // 构造函数
    public SmsMessage(String id, String address, String sender, String recipient, String body, String date, String type) {
        this.id = id;
        this.address = address;
        this.sender = sender;
        this.recipient = recipient;
        this.body = body;
        this.date = date;
        this.type = type;
        this.simId = null;
    }
    
    // 带simId的构造函数
    public SmsMessage(String id, String address, String sender, String recipient, String body, String date, String type, String simId) {
        this.id = id;
        this.address = address;
        this.sender = sender;
        this.recipient = recipient;
        this.body = body;
        this.date = date;
        this.type = type;
        this.simId = simId;
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getAddress() {
        return address;
    }
    
    public void setAddress(String address) {
        this.address = address;
    }
    
    public String getSender() {
        return sender;
    }
    
    public void setSender(String sender) {
        this.sender = sender;
    }
    
    public String getRecipient() {
        return recipient;
    }
    
    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }
    
    public String getBody() {
        return body;
    }
    
    public void setBody(String body) {
        this.body = body;
    }
    
    public String getDate() {
        return date;
    }
    
    public void setDate(String date) {
        this.date = date;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    // Getter and Setter for simId
    public String getSimId() {
        return simId;
    }
    
    public void setSimId(String simId) {
        this.simId = simId;
    }
    
    @Override
    public String toString() {
        return "SmsMessage{" +
                "id='" + id + '\'' +
                ", address='" + address + '\'' +
                ", sender='" + sender + '\'' +
                ", recipient='" + recipient + '\'' +
                ", date='" + date + '\'' +
                ", type='" + type + '\'' +
                ", simId='" + simId + '\'' +
                '}';
    }
}