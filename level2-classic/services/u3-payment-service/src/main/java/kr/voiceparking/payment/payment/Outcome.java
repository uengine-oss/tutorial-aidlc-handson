package kr.voiceparking.payment.payment;

/** PG(모의 PG)가 웹훅으로 알리는 결제 결과. u2의 결과 반영(C5)에도 같은 이름으로 전한다. */
public enum Outcome {
    APPROVED, FAILED
}
