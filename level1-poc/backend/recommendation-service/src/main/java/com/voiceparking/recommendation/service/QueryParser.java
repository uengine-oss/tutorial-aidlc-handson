package com.voiceparking.recommendation.service;

import com.voiceparking.recommendation.domain.ParsedQuery;
import com.voiceparking.recommendation.repository.TicketRepository;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 음성 텍스트에서 목적지와 이용 시간을 읽어 낸다 (FR1.1, FR1.2). */
public class QueryParser {

    private static final Pattern ARABIC_HOURS = Pattern.compile("(\\d{1,2})\\s*시간");
    private static final Pattern KOREAN_HOURS =
            Pattern.compile("(한|두|세|네|다섯|여섯|일곱|여덟|아홉|열)\\s*시간");
    private static final Pattern GENERIC_DESTINATION =
            Pattern.compile("([가-힣A-Za-z0-9]+?(?:역|터미널|공원))");
    private static final Map<String, Integer> KOREAN_NUMBERS = Map.of(
            "한", 1, "두", 2, "세", 3, "네", 4, "다섯", 5,
            "여섯", 6, "일곱", 7, "여덟", 8, "아홉", 9, "열", 10);
    private static final int MAX_HOURS = 24;

    private final TicketRepository repository;

    public QueryParser(TicketRepository repository) {
        this.repository = repository;
    }

    /** 목적지와 시간을 모두 찾으면 결과를, 하나라도 못 찾으면 빈 값을 돌려준다. */
    public Optional<ParsedQuery> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Optional<String> destination = findDestination(text);
        Optional<Integer> hours = findHours(text);
        if (destination.isEmpty() || hours.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ParsedQuery(destination.get(), hours.get()));
    }

    private Optional<String> findDestination(String text) {
        for (String known : repository.knownDestinations()) {
            if (text.contains(known)) {
                return Optional.of(known);
            }
        }
        Matcher m = GENERIC_DESTINATION.matcher(text);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    private Optional<Integer> findHours(String text) {
        Matcher arabic = ARABIC_HOURS.matcher(text);
        if (arabic.find()) {
            return validHours(Integer.parseInt(arabic.group(1)));
        }
        Matcher korean = KOREAN_HOURS.matcher(text);
        if (korean.find()) {
            return validHours(KOREAN_NUMBERS.get(korean.group(1)));
        }
        return Optional.empty();
    }

    private Optional<Integer> validHours(int hours) {
        return hours >= 1 && hours <= MAX_HOURS ? Optional.of(hours) : Optional.empty();
    }
}
