package kr.voiceparking.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * BR2.2, E5: 모의 PG는 걷어낼 수 있어야 한다. {@code payment}·{@code web}·{@code signature} 소스는 {@code pg}를
 * import하지 않고, {@code pg}를 아는 곳은 {@code config}뿐이며, {@code pg}는 {@code web}을 참조하지 않는다.
 */
class PackageBoundaryTest {

    private static final Path MAIN = Path.of("src", "main", "java", "kr", "voiceparking", "payment");
    private static final Pattern PG_REFERENCE = Pattern.compile("kr\\.voiceparking\\.payment\\.pg\\b");
    private static final Pattern WEB_REFERENCE = Pattern.compile("kr\\.voiceparking\\.payment\\.web\\b");

    /** 하위 폴더까지 포함해, 패턴을 참조하는 소스 파일을 모은다. 패키지 폴더가 없으면 시험이 실패한다. */
    private static List<String> referencesIn(Path packageDir, Pattern pattern) throws IOException {
        assertThat(packageDir).isDirectory();
        List<String> findings = new ArrayList<>();
        try (Stream<Path> files = Files.walk(packageDir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                if (pattern.matcher(Files.readString(file, StandardCharsets.UTF_8)).find()) {
                    findings.add(MAIN.relativize(file).toString());
                }
            }
        }
        return findings;
    }

    @Test
    void paymentWebAndSignatureDoNotImportPg() throws IOException {
        assertThat(referencesIn(MAIN.resolve("payment"), PG_REFERENCE)).isEmpty();
        assertThat(referencesIn(MAIN.resolve("web"), PG_REFERENCE)).isEmpty();
        assertThat(referencesIn(MAIN.resolve("signature"), PG_REFERENCE)).isEmpty();
    }

    @Test
    void theApplicationClassDoesNotImportPgEither() throws IOException {
        String source = Files.readString(MAIN.resolve("PaymentApplication.java"), StandardCharsets.UTF_8);

        assertThat(PG_REFERENCE.matcher(source).find()).isFalse();
    }

    @Test
    void onlyConfigIsAllowedToImportPg() throws IOException {
        assertThat(referencesIn(MAIN.resolve("config"), PG_REFERENCE)).contains("config/PgConfig.java");
        assertThat(referencesIn(MAIN.resolve("config"), PG_REFERENCE)).containsOnly("config/PgConfig.java");
    }

    @Test
    void pgDoesNotImportWeb() throws IOException {
        assertThat(referencesIn(MAIN.resolve("pg"), WEB_REFERENCE)).isEmpty();
    }

    @Test
    void paymentDoesNotImportWebEither() throws IOException {
        assertThat(referencesIn(MAIN.resolve("payment"), WEB_REFERENCE)).isEmpty();
    }

    @Test
    void pgDependsOnPaymentAndSignatureAsDesigned() throws IOException {
        assertThat(referencesIn(MAIN.resolve("pg"), Pattern.compile("kr\\.voiceparking\\.payment\\.signature")))
                .contains("pg/MockPaymentGateway.java");
        assertThat(referencesIn(MAIN.resolve("pg"), Pattern.compile("kr\\.voiceparking\\.payment\\.payment")))
                .contains("pg/MockPaymentGateway.java");
    }

    @Test
    void theDetectorWouldCatchAForbiddenImport() {
        assertThat(PG_REFERENCE.matcher("import kr.voiceparking.payment.pg.MockPaymentGateway;").find()).isTrue();
        assertThat(WEB_REFERENCE.matcher("import kr.voiceparking.payment.web.ApiExceptionHandler;").find()).isTrue();
        assertThat(PG_REFERENCE.matcher("import kr.voiceparking.payment.payment.PaymentGateway;").find()).isFalse();
    }
}
