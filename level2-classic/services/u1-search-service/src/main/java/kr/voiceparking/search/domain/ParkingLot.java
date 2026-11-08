package kr.voiceparking.search.domain;

/** 요청 장소 주변에 있는 목업 주차장. */
public record ParkingLot(String lotId, String name, String placeName, int distanceMeters) {
}
