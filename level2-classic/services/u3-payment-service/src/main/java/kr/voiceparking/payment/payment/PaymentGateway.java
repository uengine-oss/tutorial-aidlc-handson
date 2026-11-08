package kr.voiceparking.payment.payment;

/**
 * 결제를 PG에 요청하는 유일한 접점 (BR2.2). 모의 PG 패키지가 구현하며, 실제 PG로 바꿀 때 구현만 바꾼다. 이 패키지는
 * 구현을 참조하지 않는다.
 */
public interface PaymentGateway {

    /** 결제 한 건을 PG에 등록한다. 응답을 막지 않아야 하며, 같은 결제의 두 번째 등록은 무시한다. */
    void register(PgPayment payment);
}
