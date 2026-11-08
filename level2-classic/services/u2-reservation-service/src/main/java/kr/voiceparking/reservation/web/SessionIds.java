package kr.voiceparking.reservation.web;

import java.util.regex.Pattern;

import kr.voiceparking.reservation.reservation.ErrorCode;
import kr.voiceparking.reservation.reservation.ReservationException;

/** 세션 식별자(UUID) 읽기와 검증 (BR3.1). 형식이 틀리면 401 MISSING_SESSION이다. */
final class SessionIds {

    static final Pattern UUID_FORMAT =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private SessionIds() {
    }

    static boolean isValid(String value) {
        return value != null && UUID_FORMAT.matcher(value).matches();
    }

    /** 헤더의 세션을 검증해 돌려준다. */
    static String require(String header) {
        if (!isValid(header)) {
            throw new ReservationException(ErrorCode.MISSING_SESSION);
        }
        return header;
    }

    /** 구독 엔드포인트용: 헤더가 있으면 헤더가 우선이고, 없으면 쿼리 sessionId를 쓴다. */
    static String resolve(String header, String query) {
        return require(header != null ? header : query);
    }
}
