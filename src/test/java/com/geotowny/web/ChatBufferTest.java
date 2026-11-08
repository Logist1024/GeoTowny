package com.geotowny.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatBufferTest {

    @Test
    void returnsMessagesAfterTimestamp() {
        ChatBuffer buffer = ChatBuffer.getInstance();
        long before = System.currentTimeMillis();
        buffer.add("Alice", "hello", false);
        buffer.add("Bob", "world", true);

        List<ChatBuffer.ChatMessage> messages = buffer.since(before - 1);
        assertThat(messages).anyMatch(message -> "Alice".equals(message.player())
                && "hello".equals(message.message())
                && !message.fromWeb());
        assertThat(messages).anyMatch(message -> "Bob".equals(message.player())
                && message.fromWeb());
        assertThat(buffer.since(System.currentTimeMillis() + 10_000)).isEmpty();
    }
}