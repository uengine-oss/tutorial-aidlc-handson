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

/** NFR4.5: notification 패키지는 reservation과 domain 패키지를 참조하지 않는다. 의존은 reservation에서 notification으로만 간다. */
class PackageBoundaryTest {

    private static final Pattern FORBIDDEN_REFERENCE =
            Pattern.compile("kr\\.voiceparking\\.reservation\\.(reservation|domain)\\b");

    private static List<String> referencesIn(Path packageDir, Pattern pattern) throws IOException {
        List<String> findings = new ArrayList<>();
        try (Stream<Path> files = Files.list(packageDir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                if (pattern.matcher(content).find()) {
                    findings.add(file.getFileName().toString());
                }
            }
        }
        return findings;
    }

    @Test
    void notificationDoesNotReferenceReservationOrDomain() throws IOException {
        Path notification = Path.of("src", "main", "java", "kr", "voiceparking", "reservation", "notification");
        assertThat(notification).isDirectory();

        assertThat(referencesIn(notification, FORBIDDEN_REFERENCE)).isEmpty();
    }

    @Test
    void theDetectorWouldCatchAForbiddenImport() {
        assertThat(FORBIDDEN_REFERENCE.matcher("import kr.voiceparking.reservation.reservation.ReservationService;")
                .find()).isTrue();
        assertThat(FORBIDDEN_REFERENCE.matcher("import kr.voiceparking.reservation.domain.Reservation;").find())
                .isTrue();
        assertThat(FORBIDDEN_REFERENCE.matcher("import kr.voiceparking.reservation.notification.Snapshot;").find())
                .isFalse();
    }

    @Test
    void reservationPackageIsAllowedToUseNotification() throws IOException {
        Path reservation = Path.of("src", "main", "java", "kr", "voiceparking", "reservation", "reservation");
        assertThat(referencesIn(reservation, Pattern.compile("kr\\.voiceparking\\.reservation\\.notification")))
                .contains("ReservationService.java");
    }
}
