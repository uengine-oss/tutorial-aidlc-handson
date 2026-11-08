package kr.voiceparking.payment.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** 서명 계산과 검증 (BR3.1, BR3.2, NFR6.1). */
class HmacSignerTest {

    private static final byte[] FOX = "The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8);
    /** RFC 4231 계열의 널리 알려진 벡터: HMAC_SHA256("key", FOX). */
    private static final String FOX_HEX = "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8";

    private final HmacSigner signer = new HmacSigner("key");

    @Test
    void signingMatchesTheKnownVector() {
        assertThat(signer.sign(FOX)).isEqualTo("sha256=" + FOX_HEX);
    }

    @Test
    void aCorrectSignatureMatches() {
        assertThat(signer.matches(FOX, "sha256=" + FOX_HEX)).isTrue();
    }

    @Test
    void aDifferentBodyOrKeyDoesNotMatch() {
        byte[] tampered = "The quick brown fox jumps over the lazy cat".getBytes(StandardCharsets.UTF_8);

        assertThat(signer.matches(tampered, "sha256=" + FOX_HEX)).isFalse();
        assertThat(new HmacSigner("other").matches(FOX, "sha256=" + FOX_HEX)).isFalse();
    }

    @Test
    void aSignatureOffByOneCharacterDoesNotMatch() {
        String wrong = "sha256=" + FOX_HEX.substring(0, 63) + (FOX_HEX.endsWith("8") ? "9" : "8");

        assertThat(signer.matches(FOX, wrong)).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "sha256=", "sha256=abc", "SHA256=f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
            "sha1=f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
            "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
            "sha256=F7BC83F430538424B13298E6AA6FB143EF4D59A14946175997479DBC2D1A3CD8",
            "sha256=f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8 ",
            "sha256=f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8f" })
    void aMalformedHeaderIsNeverAccepted(String header) {
        assertThat(HmacSigner.isWellFormed(header)).isFalse();
        assertThat(signer.matches(FOX, header)).isFalse();
    }

    @Test
    void aWellFormedHeaderIsExactlySha256AndSixtyFourLowercaseHex() {
        assertThat(HmacSigner.isWellFormed("sha256=" + FOX_HEX)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "   " })
    void aMissingOrBlankKeyIsRejectedAtConstruction(String key) {
        assertThatThrownBy(() -> new HmacSigner(key)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theComparisonUsesTheConstantTimeJdkHelper() throws IOException {
        String source = Files.readString(Path.of("src", "main", "java", "kr", "voiceparking", "payment", "signature",
                "HmacSigner.java"), StandardCharsets.UTF_8);

        assertThat(source).contains("MessageDigest.isEqual").doesNotContain(".equals(header)");
    }
}
