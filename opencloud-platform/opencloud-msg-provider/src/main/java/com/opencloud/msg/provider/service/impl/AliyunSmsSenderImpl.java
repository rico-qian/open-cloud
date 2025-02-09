package com.opencloud.msg.provider.service.impl;


import com.alibaba.fastjson.JSONObject;
import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.opencloud.msg.client.model.SmsNotify;
import com.opencloud.msg.provider.service.SmsSender;
import lombok.extern.slf4j.Slf4j;


/**
 * @author woodev
 */
@Slf4j
public class AliyunSmsSenderImpl implements SmsSender {

    private String accessKeyId;

    private String accessKeySecret;

    private final static String OK = "OK";

    private final static String CODE = "Code";

    public AliyunSmsSenderImpl() {
        log.info("init aliyunSMS sender:" + this);
    }

    @Override
    public Boolean send(SmsNotify parameter) {
        boolean result = false;
        try {
            // 地域ID
            DefaultProfile profile = DefaultProfile.getProfile(
                    "cn-hangzhou",
                    accessKeyId,
                    accessKeySecret);
            IAcsClient client = new DefaultAcsClient(profile);
            CommonRequest request = new CommonRequest();
            request.setMethod(MethodType.POST);
            request.setDomain("dysmsapi.aliyuncs.com");
            request.setVersion("2017-05-25");
            request.setAction("SendSms");
            request.putQueryParameter("RegionId", "cn-hangzhou");
            request.putQueryParameter("PhoneNumbers", parameter.getPhoneNumber());
            request.putQueryParameter("SignName", parameter.getSignName());
            request.putQueryParameter("TemplateCode", parameter.getTemplateCode());
            request.putQueryParameter("TemplateParam", parameter.getParams());
            CommonResponse response = client.getCommonResponse(request);
            System.out.println(response.toString());
            JSONObject json = JSONObject.parseObject(response.getData());
            result = OK.equalsIgnoreCase(json.getString(CODE));
            log.info("result:{}", response.getData());
        } catch (Exception e) {
            log.error("发送短信失败：{}", e.getMessage(), e);
        }
        return result;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
    }

}
