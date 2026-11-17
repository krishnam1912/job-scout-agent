package dev.phoenix.channel;

import dev.phoenix.domain.Ask;
import dev.phoenix.domain.AskStatus;
import dev.phoenix.error.NotFoundException;
import dev.phoenix.error.UserErrorException;
import dev.phoenix.store.AskRepository;
import dev.phoenix.store.TxRunner;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** The asks inbox: stores questions for the human, offers them to every enabled channel and records the answer. */
public class AskService {

    private final AskRepository asks;
    private final List<NotificationChannel> channels;
    private final TxRunner tx;
    private final Clock clock;

    public AskService(AskRepository asks, List<NotificationChannel> channels, TxRunner tx, Clock clock) {
        this.asks = asks;
        this.channels = List.copyOf(channels);
        this.tx = tx;
        this.clock = clock;
    }

    /** Stores a new ask and offers it to the channels; it is OFFERED when at least one accepted it, else OPEN. */
    public Ask create(Long jobId, String kind, String prompt, String options, int priority) {
        return tx.inTx(() -> {
            Ask draft = new Ask(0, jobId, kind, prompt, options, priority, AskStatus.OPEN, null, null,
                    clock.instant(), null, null);
            long id = asks.insert(draft);
            Ask stored = asks.findById(id).orElseThrow();
            List<String> delivered = new ArrayList<>();
            for (NotificationChannel channel : channels) {
                if (channel.deliver(stored)) {
                    delivered.add(channel.id());
                }
            }
            if (!delivered.isEmpty()) {
                asks.markOffered(id, String.join(",", delivered));
            }
            return asks.findById(id).orElseThrow();
        });
    }

    /** @param statuses which asks to list; empty means everything still waiting (OPEN and OFFERED) */
    public List<Ask> list(Collection<AskStatus> statuses) {
        Collection<AskStatus> wanted = statuses.isEmpty() ? List.of(AskStatus.OPEN, AskStatus.OFFERED) : statuses;
        return asks.findByStatuses(wanted);
    }

    public Ask answer(long askId, String answer) {
        if (answer == null || answer.isBlank()) {
            throw new UserErrorException("An answer must not be empty");
        }
        return tx.inTx(() -> {
            Ask ask = asks.findById(askId).orElseThrow(() -> new NotFoundException("Ask", askId));
            if (ask.status() == AskStatus.ANSWERED) {
                throw new UserErrorException("Ask " + askId + " is already answered");
            }
            asks.markAnswered(askId, answer, clock.instant());
            return asks.findById(askId).orElseThrow();
        });
    }
}
