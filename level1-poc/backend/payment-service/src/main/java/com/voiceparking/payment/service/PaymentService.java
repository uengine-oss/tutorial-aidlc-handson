package com.voiceparking.payment.service;

import com.voiceparking.payment.client.ReservationClient;
import com.voiceparking.payment.client.ReservationClient.ConfirmResult;
import com.voiceparking.payment.domain.Payment;
import com.voiceparking.payment.domain.PaymentStatus;
import com.voiceparking.payment.domain.PaymentView;
import com.voiceparking.payment.repository.PaymentRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 결제 생성과 PG 웹훅 처리 (FR3.2, FR3.3, FR4.1). */
@Service
public class PaymentService {

    private final PaymentRepository repository;
    private final ReservationClient reservationClient;

    public PaymentService(PaymentRepository repository, ReservationClient reservationClient) {
        this.repository = repository;
        this.reservationClient = reservationClient;
    }

    /** 결제를 PENDING으로 만든다. 이미 승인된 결제가 있는 예약이면 거절한다. 실패 후 재결제는 허용. */
    public PaymentView create(String reservationId, int amount) {
        boolean alreadyPaid = repository.findByReservationId(reservationId).stream()
                .anyMatch(p -> p.getStatus() == PaymentStatus.APPROVED);
        if (alreadyPaid) {
            throw new PaymentConflictException("이미 결제가 승인된 예약입니다: " + reservationId);
        }
        Payment payment = new Payment(UUID.randomUUID().toString(), reservationId, amount);
        return repository.save(payment).toView();
    }

    public PaymentView get(String paymentId) {
        return find(paymentId).toView();
    }

    /**
     * 웹훅을 처리한다. 같은 결제의 중복 웹훅은 처음 결과를 그대로 돌려준다(멱등).
     * 승인이면 예약 확정을 요청하고, 예약이 이미 취소되었으면 LATE_APPROVAL_IGNORED로 기록한다.
     * 예약 서비스에 닿지 못하면 결제는 PENDING으로 두고 예외를 올려 PG가 재시도하게 한다.
     */
    public PaymentView handleWebhook(String paymentId, WebhookResult result, String reason) {
        Payment payment = find(paymentId);
        synchronized (payment) {
            if (!payment.isPending()) {
                return payment.toView();
            }
            if (result == WebhookResult.FAILED) {
                payment.markFailed(reason == null || reason.isBlank() ? "결제가 실패했습니다" : reason, true);
                return payment.toView();
            }
            ConfirmResult confirm = reservationClient.confirm(payment.getReservationId());
            switch (confirm) {
                case CONFIRMED -> payment.markApproved();
                case CANCELLED -> payment.markLateApprovalIgnored();
                case NOT_FOUND -> payment.markFailed("예약을 찾을 수 없습니다", false);
            }
            return payment.toView();
        }
    }

    private Payment find(String paymentId) {
        return repository.findById(paymentId).orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }
}
