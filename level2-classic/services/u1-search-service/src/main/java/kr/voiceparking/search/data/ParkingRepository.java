package kr.voiceparking.search.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import kr.voiceparking.search.domain.ParkingLot;
import kr.voiceparking.search.domain.ParkingPass;

/** 메모리 안의 읽기 전용 저장소. 장소별 색인과 passId 해시맵으로 조회한다 (NFR3.3). */
public class ParkingRepository {

    private final Map<String, ParkingLot> lotsById = new HashMap<>();
    private final Map<String, ParkingPass> passesById = new HashMap<>();
    private final Map<String, List<ParkingPass>> passesByPlace = new LinkedHashMap<>();

    public ParkingRepository(ParkingData data) {
        data.lots().forEach(lot -> lotsById.put(lot.lotId(), lot));
        for (ParkingPass pass : data.passes()) {
            passesById.put(pass.passId(), pass);
            String place = lotsById.get(pass.lotId()).placeName();
            passesByPlace.computeIfAbsent(place, k -> new ArrayList<>()).add(pass);
        }
    }

    /** 등록된 장소 이름 전체. */
    public List<String> placeNames() {
        return List.copyOf(passesByPlace.keySet());
    }

    /** 장소 이름이 같은 주차장의 모든 주차권 (BR2.1). 없는 장소면 빈 목록. */
    public List<ParkingPass> findPassesByPlace(String placeName) {
        return List.copyOf(passesByPlace.getOrDefault(placeName, List.of()));
    }

    public Optional<ParkingPass> findPass(String passId) {
        return Optional.ofNullable(passesById.get(passId));
    }

    public Optional<ParkingLot> findLot(String lotId) {
        return Optional.ofNullable(lotsById.get(lotId));
    }
}
