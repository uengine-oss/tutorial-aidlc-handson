package com.voiceparking.recommendation.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voiceparking.recommendation.domain.ParkingTicket;
import com.voiceparking.recommendation.repository.TicketRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/** classpath의 sample-tickets.json을 읽어 저장소를 만든다. 읽지 못하면 기동을 중단한다(fail fast). */
@Configuration
public class TicketDataConfig {

    @Bean
    public TicketRepository ticketRepository(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource("sample-tickets.json").getInputStream()) {
            List<ParkingTicket> tickets = objectMapper.readValue(in, new TypeReference<>() { });
            return new TicketRepository(tickets);
        } catch (IOException e) {
            throw new IllegalStateException("sample-tickets.json을 읽을 수 없습니다", e);
        }
    }
}
