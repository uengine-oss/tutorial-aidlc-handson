package kr.voiceparking.search.data;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.voiceparking.search.domain.ParkingLot;
import kr.voiceparking.search.domain.ParkingPass;

/**
 * 목업 JSON을 읽고 검증한다. 필수 필드, 값 범위, 유일성, 참조 무결성 중 하나라도 어긋나면
 * {@link DataLoadException}으로 빨리 실패한다.
 */
public class MockDataLoader {

    private final ObjectMapper mapper = new ObjectMapper();

    public ParkingData load(InputStream in) {
        if (in == null) {
            throw new DataLoadException("목업 데이터 파일을 찾을 수 없습니다");
        }
        JsonNode root;
        try {
            root = mapper.readTree(in);
        } catch (IOException e) {
            throw new DataLoadException("목업 데이터 JSON을 읽을 수 없습니다", e);
        }
        if (root == null || !root.isObject()) {
            throw new DataLoadException("목업 데이터의 최상위는 객체여야 합니다");
        }

        List<ParkingLot> lots = new ArrayList<>();
        Set<String> lotIds = new HashSet<>();
        for (JsonNode node : array(root, "lots")) {
            ParkingLot lot = new ParkingLot(
                    text(node, "lotId", "lot"),
                    text(node, "name", "lot"),
                    text(node, "placeName", "lot"),
                    number(node, "distanceMeters", 0, "lot"));
            if (!lotIds.add(lot.lotId())) {
                throw new DataLoadException("lotId가 중복되었습니다: " + lot.lotId());
            }
            lots.add(lot);
        }

        List<ParkingPass> passes = new ArrayList<>();
        Set<String> passIds = new HashSet<>();
        for (JsonNode node : array(root, "passes")) {
            ParkingPass pass = new ParkingPass(
                    text(node, "passId", "pass"),
                    text(node, "lotId", "pass"),
                    number(node, "hourlyPrice", 1, "pass"),
                    number(node, "availableHours", 1, "pass"));
            if (!passIds.add(pass.passId())) {
                throw new DataLoadException("passId가 중복되었습니다: " + pass.passId());
            }
            if (!lotIds.contains(pass.lotId())) {
                throw new DataLoadException(
                        "주차권 " + pass.passId() + "이(가) 없는 주차장을 가리킵니다: " + pass.lotId());
            }
            passes.add(pass);
        }
        return new ParkingData(List.copyOf(lots), List.copyOf(passes));
    }

    private static JsonNode array(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isArray() || node.isEmpty()) {
            throw new DataLoadException("필수 배열이 없거나 비어 있습니다: " + field);
        }
        return node;
    }

    private static String text(JsonNode node, String field, String kind) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new DataLoadException(kind + "의 필수 필드가 없거나 비어 있습니다: " + field);
        }
        return value.asText();
    }

    private static int number(JsonNode node, String field, int min, String kind) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new DataLoadException(kind + "의 필수 정수 필드가 없거나 형식이 틀립니다: " + field);
        }
        int n = value.asInt();
        if (n < min) {
            throw new DataLoadException(kind + "의 " + field + " 값이 " + min + " 미만입니다: " + n);
        }
        return n;
    }
}
