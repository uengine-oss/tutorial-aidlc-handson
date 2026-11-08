package kr.voiceparking.payment.web.dto;

/** 웹훅 처리 결과 (C6): APPLIED, DUPLICATE, IGNORED. 오류가 아니라 200이다. */
public record WebhookResponse(String handled) {
}
