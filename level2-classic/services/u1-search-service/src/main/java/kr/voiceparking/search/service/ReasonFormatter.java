package kr.voiceparking.search.service;

import kr.voiceparking.search.domain.ScoredPass;

/** 추천 이유 문장을 만든다. 가장 크게 기여한 항목의 이름을 반드시 넣는다 (BR3.5). */
public class ReasonFormatter {

    public String format(ScoredPass scored) {
        return "%s: %s이(가) 가장 큰 강점입니다 (가격 %d점, 거리 %d점, 시간 적합도 %d점)".formatted(
                scored.lot().name(), scored.topFactor().label(),
                scored.priceScore(), scored.distanceScore(), scored.timeFitScore());
    }
}
