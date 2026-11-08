package kr.voiceparking.search.service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kr.voiceparking.search.data.ParkingRepository;
import kr.voiceparking.search.domain.ParsedQuery;

/**
 * 음성 텍스트에서 장소와 이용 시간을 뽑는다 (BR1.1~BR1.4). 입력 문장은 문자열 일치와 숫자 패턴
 * 추출에만 쓰고 해석하거나 실행하지 않는다.
 */
public class QueryParser {

    private static final Pattern DURATION = Pattern.compile("(\\d+|한|두|세|네|다섯)\\s*시간");
    private static final Map<String, Integer> WORDS = Map.of("한", 1, "두", 2, "세", 3, "네", 4, "다섯", 5);

    private final ParkingRepository repository;

    public QueryParser(ParkingRepository repository) {
        this.repository = repository;
    }

    public ParsedQuery parse(String text) {
        String place = findPlace(text);
        if (place == null) {
            throw new SearchException(ErrorCode.MISSING_PLACE);
        }
        return new ParsedQuery(place, findDuration(text));
    }

    /** 문장에서 가장 먼저 나오는 등록 장소. 같은 위치면 더 긴 이름. */
    private String findPlace(String text) {
        String found = null;
        int foundAt = Integer.MAX_VALUE;
        for (String name : repository.placeNames()) {
            int at = text.indexOf(name);
            if (at < 0) {
                continue;
            }
            if (at < foundAt || (at == foundAt && name.length() > found.length())) {
                found = name;
                foundAt = at;
            }
        }
        return found;
    }

    /** 가장 먼저 나오는 이용 시간 표현. 없거나 1~24 밖이면 MISSING_DURATION. */
    private int findDuration(String text) {
        Matcher matcher = DURATION.matcher(text);
        if (!matcher.find()) {
            throw new SearchException(ErrorCode.MISSING_DURATION);
        }
        String token = matcher.group(1);
        long hours = WORDS.containsKey(token) ? WORDS.get(token) : parseDigits(token);
        if (!DurationLimits.inRange(hours)) {
            throw new SearchException(ErrorCode.MISSING_DURATION);
        }
        return (int) hours;
    }

    private static long parseDigits(String digits) {
        // 3자리를 넘는 숫자는 어차피 범위 밖이므로 파싱하지 않는다.
        return digits.length() > 3 ? Long.MAX_VALUE : Long.parseLong(digits);
    }
}
