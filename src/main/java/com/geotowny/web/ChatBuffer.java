package com.geotowny.web;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class ChatBuffer {

    private static final int MAX_MESSAGES = 200;
    private static final ChatBuffer INSTANCE = new ChatBuffer();

    private final ConcurrentLinkedDeque<ChatMessage> messages = new ConcurrentLinkedDeque<>();

    private ChatBuffer() {
    }

    public static ChatBuffer getInstance() {
        return INSTANCE;
    }

    public void add(String playerName, String message, boolean fromWeb) {
        messages.addLast(new ChatMessage(System.currentTimeMillis(), playerName, message, fromWeb));
        while (messages.size() > MAX_MESSAGES) {
            messages.pollFirst();
        }
    }

    public List<ChatMessage> since(long timestamp) {
        List<ChatMessage> result = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (message.timestamp() > timestamp) {
                result.add(message);
            }
        }
        return result;
    }

    public record ChatMessage(long timestamp, String player, String message, boolean fromWeb) {
    }
}