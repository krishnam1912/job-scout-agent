package dev.phoenix.channel;

import dev.phoenix.domain.Ask;

/**
 * Pull-style delivery: Claude fetches open asks with {@code list_asks} and relays them to the user (chat, Remote
 * Control or push notification). Nothing is sent from here, so delivery is simply "available to be pulled".
 */
public final class ClaudeSessionChannel implements NotificationChannel {

    public static final String ID = "claude-session";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean deliver(Ask ask) {
        return true;
    }
}
