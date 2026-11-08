package kr.voiceparking.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;

/** NFR6.7: 기본 server.address가 루프백이고, 이 머신의 다른 주소로는 접속이 거부된다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoopbackBindingTest {

    @LocalServerPort
    private int port;
    @Autowired
    private Environment environment;

    @Test
    void defaultServerAddressIsLoopback() {
        assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
    }

    @Test
    void theDefaultPortSettingIs8083() throws IOException {
        String config = java.nio.file.Files.readString(java.nio.file.Path.of("src", "main", "resources",
                "application.properties"));

        assertThat(config).contains("server.port=${SERVER_PORT:8083}")
                .contains("server.address=${SERVER_ADDRESS:127.0.0.1}")
                .contains("reservation.service.url=${RESERVATION_SERVICE_URL:http://127.0.0.1:8082}")
                .contains("payment.webhook.url=${WEBHOOK_URL:http://127.0.0.1:8083/api/payments/webhook}");
    }

    @Test
    void theServiceAnswersOnTheLoopbackAddress() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void aNonLoopbackAddressOfThisMachineIsRefused() throws Exception {
        List<InetAddress> addresses = nonLoopbackIpv4Addresses();
        // 비루프백 주소가 없는 환경에서는 이 시험을 건너뛴다. 건너뛴 사실은 시험 결과에 남는다.
        assumeFalse(addresses.isEmpty(), "이 머신에 비루프백 IPv4 주소가 없어 접속 거부 시험을 건너뜀");

        for (InetAddress address : addresses) {
            assertThatThrownBy(() -> {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(address, port), 1000);
                }
            }).isInstanceOf(IOException.class);
        }
    }

    private static List<InetAddress> nonLoopbackIpv4Addresses() throws IOException {
        List<InetAddress> found = new ArrayList<>();
        for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!nic.isUp()) {
                continue;
            }
            for (InetAddress address : Collections.list(nic.getInetAddresses())) {
                if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                    found.add(address);
                }
            }
        }
        return found;
    }
}
