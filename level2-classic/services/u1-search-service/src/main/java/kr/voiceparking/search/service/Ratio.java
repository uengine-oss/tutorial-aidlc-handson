package kr.voiceparking.search.service;

/**
 * 정수 분수. 점수를 부동소수점 없이 정확히 계산하고 0.5에서 올림하려고 쓴다 (BR3.2).
 * 분모는 항상 양수다.
 */
record Ratio(long num, long den) {

    static final Ratio ZERO = new Ratio(0, 1);

    Ratio {
        if (den <= 0) {
            throw new IllegalArgumentException("denominator must be positive");
        }
    }

    Ratio plus(Ratio other) {
        return new Ratio(num * other.den + other.num * den, den * other.den);
    }

    /** 정수 비율(percent/100)을 곱한다. */
    Ratio timesPercent(int percent) {
        return new Ratio(num * percent, den * 100);
    }

    int compareTo(Ratio other) {
        return Long.compare(num * other.den, other.num * den);
    }

    /** 0.5에서 올림하는 반올림 (값은 0 이상). */
    int roundHalfUp() {
        return (int) ((2 * num + den) / (2 * den));
    }
}
