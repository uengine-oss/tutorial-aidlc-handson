package kr.voiceparking.payment.payment;

import java.util.regex.Pattern;

/**
 * 결제 요청(C2)의 검증 전 입력. 값의 검증은 서비스가 한다 (BR1.1). 금액 필드는 일부러 없다: 클라이언트가 금액을
 * 보내도 쓰지 않는다 (BR4.1).
 */
public record PaymentCommand(String sessionId, String passId, Integer durationHours, String idempotencyKey,
        String mockScenario, Integer mockDelaySeconds) {

    /** 세션 식별자(UUID) 형식. */
    public static final Pattern UUID_FORMAT =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
}
