package com.voiceparking.payment.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** RestClient로 예약 서비스의 내부 확정 API를 호출한다. 타임아웃을 두고 오류는 예외로 올린다. */
@Component
public class HttpReservationClient implements ReservationClient {

    private final RestClient client;

    public HttpReservationClient(
            @Value("${reservation.base-url}") String baseUrl,
            @Value("${reservation.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${reservation.read-timeout-ms:3000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public ConfirmResult confirm(String reservationId) {
        try {
            return client.post()
                    .uri("/internal/reservations/{id}/confirm", reservationId)
                    .exchange((request, response) -> {
                        HttpStatusCode code = response.getStatusCode();
                        if (code.is2xxSuccessful()) {
                            return ConfirmResult.CONFIRMED;
                        }
                        if (code.value() == 409) {
                            return ConfirmResult.CANCELLED;
                        }
                        if (code.value() == 404) {
                            return ConfirmResult.NOT_FOUND;
                        }
                        throw new ReservationClientException("예약 서비스가 예상 밖 응답을 보냈습니다: " + code.value());
                    });
        } catch (RestClientException e) {
            throw new ReservationClientException("예약 서비스에 연결하지 못했습니다", e);
        }
    }
}
