package kr.voiceparking.search.service;

/** 이용 시간 허용 범위(시간). 음성 해석(BR1.2)과 주차권 조회(C4)가 같은 범위를 쓴다. */
final class DurationLimits {

    static final int MIN_HOURS = 1;
    static final int MAX_HOURS = 24;

    private DurationLimits() {
    }

    static boolean inRange(long hours) {
        return hours >= MIN_HOURS && hours <= MAX_HOURS;
    }
}
