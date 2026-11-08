package com.geotowny.util;

/**
 * 服务层返回值：成功标记 + 面向玩家的消息。
 */
public final class Result {

    private final boolean success;
    private final String message;

    private Result(boolean success, String message) {
        this.success = success;
        this.message = message == null ? "" : message;
    }

    public static Result ok(String message) {
        return new Result(true, message);
    }

    public static Result fail(String message) {
        return new Result(false, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}