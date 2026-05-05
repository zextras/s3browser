package com.zextras.s3browser.application.usecase;

public final class PrefixUtils {

    private PrefixUtils() {
    }

    public static String parentPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return null;
        }

        String normalized = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash < 0) {
            return "";
        }
        return normalized.substring(0, lastSlash + 1);
    }
}

