package kr.voiceparking.reservation;

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

/** NFR6.1: 소스와 설정에 흔한 비밀 값 패턴이 없다. */
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
    void detectorFlagsAnObviousSecret() {
        assertThat(SECRET_PATTERNS.stream().anyMatch(p -> p.matcher("db.password=hunter2x").find())).isTrue();
        assertThat(SECRET_PATTERNS.stream().anyMatch(p -> p.matcher("key AKIAABCDEFGHIJKLMNOP").find())).isTrue();
    }
}
