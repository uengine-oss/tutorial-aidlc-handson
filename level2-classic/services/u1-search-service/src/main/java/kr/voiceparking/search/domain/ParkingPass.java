package kr.voiceparking.search.domain;

/** 주차장이 파는 주차권 한 종류. 시간당 요금과 한 번에 쓸 수 있는 최대 시간을 가진다. */
public record ParkingPass(String passId, String lotId, int hourlyPrice, int availableHours) {

    /** 요청 이용 시간 전체에 대한 합계 금액(원). 추천과 주차권 조회가 같은 계산을 쓴다 (BR4.1). */
    public int totalPrice(int durationHours) {
        return hourlyPrice * durationHours;
    }
}
