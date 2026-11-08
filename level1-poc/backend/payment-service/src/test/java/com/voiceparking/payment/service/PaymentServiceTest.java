package com.voiceparking.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.voiceparking.payment.client.ReservationClient;
import com.voiceparking.payment.client.ReservationClientException;
import com.voiceparking.payment.domain.PaymentStatus;
import com.voiceparking.payment.domain.PaymentView;
import com.voiceparking.payment.repository.PaymentRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {

    /** 서비스 간 호출을 대신하는 가짜. */
    static class FakeReservationClient implements ReservationClient {
        ConfirmResult next = ConfirmResult.CONFIRMED;
        boolean unreachable;
        final List<String> confirmed = new ArrayList<>();

        @Override
        public ConfirmResult confirm(String reservationId) {
            if (unreachable) {
                throw new ReservationClientException("연결 실패");
            }
            confirmed.add(reservationId);
            return next;
        }
    }

    private FakeReservationClient client;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        client = new FakeReservationClient();
        service = new PaymentService(new PaymentRepository(), client);
    }

    @Test
    void 승인_웹훅은_결제를_승인하고_예약_확정을_요청한다() {
        PaymentView p = service.create("r1", 6000);
        assertEquals(PaymentStatus.PENDING, p.status());

        PaymentView result = service.handleWebhook(p.id(), WebhookResult.APPROVED, null);

        assertEquals(PaymentStatus.APPROVED, result.status());
        assertEquals(List.of("r1"), client.confirmed);
    }

    @Test
    void 중복_웹훅은_한_번만_처리한다() {
        PaymentView p = service.create("r1", 6000);
        service.handleWebhook(p.id(), WebhookResult.APPROVED, null);
        PaymentView again = service.handleWebhook(p.id(), WebhookResult.APPROVED, null);

        assertEquals(PaymentStatus.APPROVED, again.status());
        assertEquals(1, client.confirmed.size());
    }

    @Test
    void 이미_실패한_결제에_승인_웹훅이_와도_상태가_바뀌지_않는다() {
        PaymentView p = service.create("r1", 6000);
        service.handleWebhook(p.id(), WebhookResult.FAILED, "한도 초과");
        PaymentView after = service.handleWebhook(p.id(), WebhookResult.APPROVED, null);
        assertEquals(PaymentStatus.FAILED, after.status());
        assertTrue(client.confirmed.isEmpty());
    }

    @Test
    void 모르는_결제_ID는_NotFound() {
        assertThrows(PaymentNotFoundException.class,
                () -> service.handleWebhook("nope", WebhookResult.APPROVED, null));
    }

    @Test
    void 취소된_예약에_승인이_오면_LATE_APPROVAL_IGNORED() {
        client.next = ReservationClient.ConfirmResult.CANCELLED;
        PaymentView p = service.create("r1", 6000);

        PaymentView result = service.handleWebhook(p.id(), WebhookResult.APPROVED, null);

        assertEquals(PaymentStatus.LATE_APPROVAL_IGNORED, result.status());
        assertFalse(result.retryable());
    }

    @Test
    void 실패_웹훅은_사유와_재결제_가능_상태를_남긴다() {
        PaymentView p = service.create("r1", 6000);
        PaymentView result = service.handleWebhook(p.id(), WebhookResult.FAILED, "카드 한도 초과");
        assertEquals(PaymentStatus.FAILED, result.status());
        assertEquals("카드 한도 초과", result.failureReason());
        assertTrue(result.retryable());
        assertTrue(client.confirmed.isEmpty());
    }

    @Test
    void 실패_뒤에는_같은_예약으로_다시_결제할_수_있다() {
        PaymentView first = service.create("r1", 6000);
        service.handleWebhook(first.id(), WebhookResult.FAILED, "실패");
        PaymentView second = service.create("r1", 6000);
        assertEquals(PaymentStatus.PENDING, second.status());
    }

    @Test
    void 이미_승인된_예약에는_다시_결제할_수_없다() {
        PaymentView p = service.create("r1", 6000);
        service.handleWebhook(p.id(), WebhookResult.APPROVED, null);
        assertThrows(PaymentConflictException.class, () -> service.create("r1", 6000));
    }

    @Test
    void 예약_서비스에_닿지_못하면_결제는_PENDING으로_남고_예외가_난다() {
        client.unreachable = true;
        PaymentView p = service.create("r1", 6000);
        assertThrows(ReservationClientException.class,
                () -> service.handleWebhook(p.id(), WebhookResult.APPROVED, null));
        assertEquals(PaymentStatus.PENDING, service.get(p.id()).status());
    }
}
