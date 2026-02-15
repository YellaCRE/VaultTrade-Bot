package com.vaulttradebot.application.usecase;

import com.vaulttradebot.application.outbox.OutboxMessage;
import com.vaulttradebot.application.port.out.ClockPort;
import com.vaulttradebot.application.port.out.OutboxRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OutboxRedriveService {
    private final OutboxRepository outboxRepository;
    private final ClockPort clockPort;
    private final int batchSize;

    public OutboxRedriveService(
            OutboxRepository outboxRepository,
            ClockPort clockPort,
            @Value("${vault.outbox.redrive-batch-size:100}") int batchSize
    ) {
        this.outboxRepository = outboxRepository;
        this.clockPort = clockPort;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${vault.outbox.redrive-delay-ms:60000}",
            initialDelayString = "${vault.outbox.redrive-initial-delay-ms:10000}"
    )
    public void scheduledRedrive() {
        redriveBatch(batchSize);
    }

    public int redriveBatch(int limit) {
        List<OutboxMessage> deadLetters = outboxRepository.findDeadLettered(limit);
        Instant now = clockPort.now();
        for (OutboxMessage deadLetter : deadLetters) {
            outboxRepository.redriveDeadLetter(deadLetter.id(), now);
        }
        return deadLetters.size();
    }
}
