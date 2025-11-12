package com.funshion.funautosend.model;

/**
 * 手机号数据模型类
 */
public class PhoneNumberData {
    private String phoneNumber1;
    private String phoneNumber2;

    public PhoneNumberData(String phoneNumber1, String phoneNumber2) {
        this.phoneNumber1 = phoneNumber1;
        this.phoneNumber2 = phoneNumber2;
    }

    public String getPhoneNumber1() {
        return phoneNumber1;
    }

    public void setPhoneNumber1(String phoneNumber1) {
        this.phoneNumber1 = phoneNumber1;
    }

    public String getPhoneNumber2() {
        return phoneNumber2;
    }

    public void setPhoneNumber2(String phoneNumber2) {
        this.phoneNumber2 = phoneNumber2;
    }
}