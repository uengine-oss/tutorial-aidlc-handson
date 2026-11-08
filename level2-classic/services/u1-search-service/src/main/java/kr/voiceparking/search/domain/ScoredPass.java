package kr.voiceparking.search.domain;

/** 후보 한 건과 그 점수. 점수는 모두 반올림한 정수로 보관한다. 저장하지 않는다. */
public record ScoredPass(
        ParkingLot lot,
        ParkingPass pass,
        int totalPrice,
        int priceScore,
        int distanceScore,
        int timeFitScore,
        int totalScore,
        Factor topFactor) {
}
