package com.voiceparking.reservation.event;

import com.voiceparking.reservation.domain.ReservationView;

/** 예약 상태가 바뀌었을 때 구독자에게 알린다. */
public interface ReservationEventPublisher {
    void publish(ReservationView view);
}
