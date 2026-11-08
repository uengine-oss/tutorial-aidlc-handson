package com.voiceparking.reservation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 주기적으로 승인 마감이 지난 예약을 취소한다. */
@Component
public class ExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpiryScheduler.class);

    private final ReservationService service;

    public ExpiryScheduler(ReservationService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${reservation.expiry-check-ms:1000}")
    public void run() {
        try {
            int cancelled = service.expireOverdue();
            if (cancelled > 0) {
                log.info("승인 마감 초과로 {}건 자동 취소", cancelled);
            }
        } catch (RuntimeException e) {
            log.error("예약 만료 처리 중 오류", e);
        }
    }
}
