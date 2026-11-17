package dev.phoenix.channel;

import dev.phoenix.domain.Ask;

/**
 * A way to put an {@link Ask} in front of the human (the Claude session today, a chat bot later). Implementations
 * only deliver; storing the ask, its status and the answer stay in {@link AskService}, so every channel behaves
 * the same and an answer can arrive through any of them.
 */
public interface NotificationChannel {

    /** Stable name stored in {@code asks.channel}. */
    String id();

    /** @return true when the channel took responsibility for showing the ask */
    boolean deliver(Ask ask);
}
