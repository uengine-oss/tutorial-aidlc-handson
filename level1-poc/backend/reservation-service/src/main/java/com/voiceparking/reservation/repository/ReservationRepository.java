package com.voiceparking.reservation.repository;

import com.voiceparking.reservation.domain.Reservation;
import com.voiceparking.reservation.domain.ReservationStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** 메모리 예약 저장소. */
@Repository
public class ReservationRepository {

    private final Map<String, Reservation> store = new ConcurrentHashMap<>();

    public Reservation save(Reservation reservation) {
        store.put(reservation.getId(), reservation);
        return reservation;
    }

    public Optional<Reservation> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    public List<Reservation> findByStatus(ReservationStatus status) {
        return store.values().stream().filter(r -> r.getStatus() == status).toList();
    }
}
