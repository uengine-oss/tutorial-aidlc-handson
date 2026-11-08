package kr.voiceparking.payment.signature;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 웹훅 본문 원문 바이트의 HMAC-SHA256 서명 계산과 검증 (BR3.1). 웹 계층의 검증기와 모의 PG가 함께 쓰므로 어느 쪽의
 * 패키지에도 속하지 않는다. 헤더 값은 정확히 {@code sha256=<64자 소문자 hex>}이고, 비교는 상수 시간으로 한다.
 */
public final class HmacSigner {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String PREFIX = "sha256=";
    private static final Pattern HEADER_FORMAT = Pattern.compile("sha256=[0-9a-f]{64}");

    private final byte[] key;

    /** 비밀 값이 없거나 비어 있으면 거부한다(빨리 실패, BR3.2). */
    public HmacSigner(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("webhook secret must not be blank");
        }
        this.key = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** 본문에 대한 서명 헤더 값 {@code sha256=<소문자 hex>}. */
    public String sign(byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return PREFIX + HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", e);
        }
    }

    /** 헤더가 형식에 맞고 본문의 서명과 같을 때만 true. 형식이 틀리면 비교하지 않는다. */
    public boolean matches(byte[] body, String header) {
        if (!isWellFormed(header)) {
            return false;
        }
        byte[] expected = sign(body).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, header.getBytes(StandardCharsets.UTF_8));
    }

    /** 정확히 {@code sha256=} 뒤에 소문자 hex 64자. */
    public static boolean isWellFormed(String header) {
        return header != null && HEADER_FORMAT.matcher(header).matches();
    }
}
