package com.voiceparking.recommendation.domain;

/** 음성 텍스트에서 읽어 낸 목적지와 이용 시간(시간 단위). */
public record ParsedQuery(String destination, int hours) {
}
