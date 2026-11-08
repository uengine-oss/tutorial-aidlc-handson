package kr.voiceparking.search.service;

import kr.voiceparking.search.data.ParkingRepository;
import kr.voiceparking.search.domain.ParkingLot;
import kr.voiceparking.search.domain.ParkingPass;

/** 예약 생성용 주차권 조회(C4). 읽기 전용이라 같은 요청은 항상 같은 응답이다 (BR4.1, BR4.2). */
public class PassLookupService {

    private final ParkingRepository repository;

    public PassLookupService(ParkingRepository repository) {
        this.repository = repository;
    }

    public PassInfo lookup(String passId, int durationHours) {
        if (!DurationLimits.inRange(durationHours)) {
            throw new SearchException(ErrorCode.INVALID_DURATION);
        }
        ParkingPass pass = repository.findPass(passId)
                .orElseThrow(() -> new SearchException(ErrorCode.PASS_NOT_FOUND));
        if (pass.availableHours() < durationHours) {
            throw new SearchException(ErrorCode.DURATION_NOT_AVAILABLE);
        }
        ParkingLot lot = repository.findLot(pass.lotId()).orElseThrow();
        return new PassInfo(pass.passId(), lot.name(), durationHours, pass.totalPrice(durationHours));
    }
}
