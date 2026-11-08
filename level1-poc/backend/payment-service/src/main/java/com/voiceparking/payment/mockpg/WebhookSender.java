package com.voiceparking.payment.mockpg;

import com.voiceparking.payment.service.WebhookResult;

/** 모의 PG가 웹훅을 보내는 수단. 테스트에서는 가짜로 바꾼다. */
public interface WebhookSender {
    void send(String paymentId, WebhookResult result, String reason);
}
