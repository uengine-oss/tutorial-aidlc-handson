package com.voiceparking.recommendation.domain;

/**
 * 주차권 한 건. price는 원 단위 총액, distanceMeters는 목적지까지 거리, durationHours는 이용 가능 시간.
 */
public record ParkingTicket(
        String id,
        String lotName,
        String destination,
        int price,
        int distanceMeters,
        int durationHours) {
}
