package com.voiceparking.reservation.service;

import com.voiceparking.reservation.domain.InvalidTransitionException;
import com.voiceparking.reservation.domain.Reservation;
import com.voiceparking.reservation.domain.ReservationStatus;
import com.voiceparking.reservation.domain.ReservationView;
import com.voiceparking.reservation.event.ReservationEventPublisher;
import com.voiceparking.reservation.repository.ReservationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 예약 생성, 확정, 승인 마감 만료 처리 (FR3.4, FR4.1, NFR2). */
@Service
public class ReservationService {

    private final ReservationRepository repository;
    private final Clock clock;
    private final ReservationEventPublisher publisher;
    private final Duration approvalTimeout;

    public ReservationService(ReservationRepository repository, Clock clock,
                              ReservationEventPublisher publisher,
                              @Value("${reservation.approval-timeout-seconds:180}") long approvalTimeoutSeconds) {
        this.repository = repository;
        this.clock = clock;
        this.publisher = publisher;
        this.approvalTimeout = Duration.ofSeconds(approvalTimeoutSeconds);
    }

    public ReservationView create(String ticketId, String ticketName, int price) {
        Instant now = clock.instant();
        Reservation r = new Reservation(UUID.randomUUID().toString(), ticketId, ticketName, price,
                now, now.plus(approvalTimeout));
        repository.save(r);
        return r.toView();
    }

    public ReservationView get(String id) {
        Reservation r = find(id);
        expireIfDue(r);
        return r.toView();
    }

    /**
     * 결제 승인 후 예약을 확정하고 구독자에게 알린다.
     *
     * @throws InvalidTransitionException 이미 취소되었거나 승인 마감을 넘긴 경우(늦은 승인)
     */
    public ReservationView confirm(String id) {
        Reservation r = find(id);
        synchronized (r) {
            expireIfDue(r);
            boolean changed = r.confirm(clock.instant());
            ReservationView view = r.toView();
            if (changed) {
                publisher.publish(view);
            }
            return view;
        }
    }

    /** 마감이 지난 대기 예약을 모두 취소한다. 취소한 건수를 돌려준다. */
    public int expireOverdue() {
        int count = 0;
        for (Reservation r : repository.findByStatus(ReservationStatus.PENDING)) {
            if (expireIfDue(r)) {
                count++;
            }
        }
        return count;
    }

    private boolean expireIfDue(Reservation r) {
        synchronized (r) {
            if (r.getStatus() == ReservationStatus.PENDING && r.isExpired(clock.instant()) && r.cancel()) {
                publisher.publish(r.toView());
                return true;
            }
            return false;
        }
    }

    private Reservation find(String id) {
        return repository.findById(id).orElseThrow(() -> new ReservationNotFoundException(id));
    }
}
