package kr.voiceparking.payment.payment;

/** u2 결제 결과 반영의 판정. APPLIED는 상태를 바꿨다는 뜻, REJECTED는 바꾸지 않았다는 뜻이다. */
public enum ApplyResult {
    APPLIED, REJECTED
}
