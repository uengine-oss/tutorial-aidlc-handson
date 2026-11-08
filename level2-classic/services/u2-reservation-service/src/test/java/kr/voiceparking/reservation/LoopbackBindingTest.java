package kr.voiceparking.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;

import kr.voiceparking.reservation.support.TestHttp;

/** NFR6.5: 내부 경로를 막는 방어선은 루프백 바인딩이다. 기본 주소가 루프백이고 다른 주소로는 접속이 거부된다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoopbackBindingTest {

    @LocalServerPort
    private int port;
    @Autowired
    private Environment environment;

    @Test
    void defaultServerAddressIsLoopback() {
        String address = environment.getProperty("server.address");

        assertThat(address).isNotNull();
        assertThat(address).isEqualTo("127.0.0.1");
    }

    @Test
    void theServiceAnswersOnTheLoopbackAddress() throws Exception {
        assertThat(new TestHttp(port).get("/actuator/health", null).statusCode()).isEqualTo(200);
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
