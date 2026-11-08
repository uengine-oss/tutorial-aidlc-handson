package kr.voiceparking.payment.web;

import kr.voiceparking.payment.payment.PaymentCommand;

/** 요청 로그가 세션 식별자(UUID) 형식을 확인하는 도우미. 형식이 틀린 값은 로그 위조를 막으려고 버린다. */
final class SessionIds {

    private SessionIds() {
    }

    static boolean isValid(String value) {
        return value != null && PaymentCommand.UUID_FORMAT.matcher(value).matches();
    }
}
