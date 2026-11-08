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

/** NFR6.2: 운영 소스와 설정(src/main)에 흔한 비밀 값 패턴이 없다. 시험 소스는 서명용 시험 값이 필요하므로 검색하지 않는다. */
class NoSecretsTest {

    private static final List<Pattern> SECRET_PATTERNS = List.of(
            Pattern.compile("AKIA[0-9A-Z]{16}"),
            Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----"),
            Pattern.compile("(?i)(password|passwd|secret|api[-_]?key|access[-_]?token)\\s*[=:]\\s*[\"']?[^\\s\"'$]{4,}"),
            Pattern.compile("(?i)bearer\\s+[a-z0-9._-]{20,}"));

    @Test
    void mainSourcesAndConfigContainNoSecretLikeValues() throws IOException {
        Path root = Path.of("src", "main");
        assertThat(root).isDirectory();

        List<String> findings = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                for (Pattern pattern : SECRET_PATTERNS) {
                    if (pattern.matcher(content).find()) {
                        findings.add(file + " matches " + pattern.pattern());
                    }
                }
            }
        }
        assertThat(findings).isEmpty();
    }

    @Test
    void theSecretSettingHasNoDefaultValueInTheMainConfig() throws IOException {
        String config = Files.readString(Path.of("src", "main", "resources", "application.properties"),
                StandardCharsets.UTF_8);

        assertThat(config).contains("payment.webhook.secret=${PAYMENT_WEBHOOK_SECRET}")
                .doesNotContain("PAYMENT_WEBHOOK_SECRET:");
    }

    @Test
    void detectorFlagsAnObviousSecret() {
        assertThat(SECRET_PATTERNS.stream().anyMatch(p -> p.matcher("db.password=hunter2x").find())).isTrue();
        assertThat(SECRET_PATTERNS.stream().anyMatch(p -> p.matcher("webhook.secret=abcd1234").find())).isTrue();
        assertThat(SECRET_PATTERNS.stream().anyMatch(p -> p.matcher("key AKIAABCDEFGHIJKLMNOP").find())).isTrue();
        assertThat(SECRET_PATTERNS.stream().anyMatch(p -> p.matcher("payment.webhook.secret=${PAYMENT_WEBHOOK_SECRET}")
                .find())).isFalse();
    }
}
