package com.voiceparking.recommendation.repository;

import com.voiceparking.recommendation.domain.ParkingTicket;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** 샘플 주차권을 메모리에 들고 있는 저장소. */
public class TicketRepository {

    private final List<ParkingTicket> tickets;

    public TicketRepository(List<ParkingTicket> tickets) {
        this.tickets = List.copyOf(tickets);
    }

    /** 목적지가 같고 거리가 반경 이내(경계 포함)인 주차권. */
    public List<ParkingTicket> findNear(String destination, int radiusMeters) {
        return tickets.stream()
                .filter(t -> t.destination().equals(destination))
                .filter(t -> t.distanceMeters() <= radiusMeters)
                .sorted(Comparator.comparing(ParkingTicket::id))
                .toList();
    }

    /** 데이터에 있는 목적지 이름 목록(긴 이름 우선). */
    public Set<String> knownDestinations() {
        Set<String> names = new TreeSet<>(Comparator.comparingInt(String::length).reversed()
                .thenComparing(Comparator.naturalOrder()));
        tickets.forEach(t -> names.add(t.destination()));
        return names;
    }
}
