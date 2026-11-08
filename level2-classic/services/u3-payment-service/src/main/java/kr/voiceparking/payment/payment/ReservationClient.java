package kr.voiceparking.payment.payment;

import java.time.Duration;

/**
 * u2 예약 서비스 호출(C5). 두 메서드 모두 실패는 {@link PaymentException}으로만 알린다. create는 404·409(+reservationId)·
 * 422를 같은 코드로 전하고 그 밖의 모든 실패를 UPSTREAM_FAILED로 낸다. applyPaymentResult의 모든 실패는
 * UPSTREAM_FAILED다.
 */
public interface ReservationClient {

    /** 예약 생성. 재시도 없이 한 번 호출하고, 호출 전체가 timeout을 넘기지 않는다 (E2). */
    ReservationReply create(CreateCall call, Duration timeout);

    /** 결제 결과 반영. 같은 eventId라 재시도해도 안전하다. */
    ApplyReply applyPaymentResult(String reservationId, String eventId, Outcome outcome);
}
