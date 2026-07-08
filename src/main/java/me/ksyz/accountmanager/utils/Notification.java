package me.ksyz.accountmanager.utils;

public class Notification {
    private final String message;
    private final long duration;
    private final long startTime;

    public Notification(String message, long duration) {
        this.message = message;
        this.duration = duration;
        this.startTime = System.currentTimeMillis();
    }

    public String getMessage() {
        return message;
    }

    public boolean isExpired() {
        return duration >= 0 && duration < System.currentTimeMillis() - startTime;
    }
}