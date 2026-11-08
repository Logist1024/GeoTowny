package com.geotowny;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class LogTest {

    private List<String> messages;

    @BeforeEach
    void setUp() {
        Logger logger = Logger.getLogger("GeoTownyTest-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);

        messages = new ArrayList<>();
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        Log.init(logger);
    }

    @Test
    void replacesPlaceholdersInOrder() {
        Log.info("余额 {} 于 {}，剩余 {}。", 100, "柏林", 50.5);

        assertThat(messages).containsExactly("余额 100 于 柏林，剩余 50.5。");
    }

    @Test
    void keepsMessageWhenNoArguments() {
        Log.warn("无参数消息");

        assertThat(messages).containsExactly("无参数消息");
    }

    @Test
    void leavesExtraPlaceholdersWhenArgumentsAreMissing() {
        Log.info("两个占位符 {} {}，只有一个参数", 1);

        assertThat(messages).containsExactly("两个占位符 1 {}，只有一个参数");
    }

    @Test
    void rendersNullArgumentAsText() {
        Log.error("值为 {}", (Object) null);

        assertThat(messages).containsExactly("值为 null");
    }
}