package kr.voiceparking.reservation.notification;

/** 동시 구독 수 상한을 넘었다. 웹 계층이 503 SUBSCRIPTION_LIMIT으로 바꾼다. */
public class SubscriptionLimitException extends RuntimeException {

    public SubscriptionLimitException() {
        super("subscription limit reached", null, false, false);
    }
}
