package kr.voiceparking.payment.payment;

/** Payment의 상태. REQUESTED에서 시작해 종료 상태 하나로 한 번만 바뀐다 (BR3.5). */
public enum PaymentStatus {
    REQUESTED, APPROVED, FAILED, IGNORED;

    public boolean isTerminal() {
        return this != REQUESTED;
    }

    /** PG가 알려 준 결과에 대응하는 종료 상태. */
    public static PaymentStatus of(Outcome outcome) {
        return outcome == Outcome.APPROVED ? APPROVED : FAILED;
    }
}
