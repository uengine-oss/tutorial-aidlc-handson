package kr.voiceparking.search.domain;

/** 종합 점수를 이루는 세 항목. 선언 순서가 기여도 동점일 때의 우선순위다 (BR3.5). */
public enum Factor {
    PRICE("가격"),
    DISTANCE("거리"),
    TIME_FIT("시간 적합도");

    private final String label;

    Factor(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
