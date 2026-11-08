package com.voiceparking.payment.repository;

import com.voiceparking.payment.domain.Payment;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** 메모리 결제 저장소. */
@Repository
public class PaymentRepository {

    private final Map<String, Payment> store = new ConcurrentHashMap<>();

    public Payment save(Payment payment) {
        store.put(payment.getId(), payment);
        return payment;
    }

    public Optional<Payment> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    public List<Payment> findByReservationId(String reservationId) {
        return store.values().stream().filter(p -> p.getReservationId().equals(reservationId)).toList();
    }
}
