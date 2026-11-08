package kr.voiceparking.search.data;

import java.util.List;

import kr.voiceparking.search.domain.ParkingLot;
import kr.voiceparking.search.domain.ParkingPass;

/** 검증을 통과한 목업 데이터 묶음. */
public record ParkingData(List<ParkingLot> lots, List<ParkingPass> passes) {
}
