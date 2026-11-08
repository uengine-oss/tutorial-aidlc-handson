package kr.voiceparking.payment.payment;

/** 웹훅 처리 결과(C6 응답의 handled). WebhookEvent에는 APPLIED와 IGNORED만 기록한다. */
public enum Handled {
    APPLIED, DUPLICATE, IGNORED
}
