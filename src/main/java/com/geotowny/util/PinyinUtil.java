package com.geotowny.util;

import java.io.UnsupportedEncodingException;

/**
 * 中文转拼音首字母工具类。
 * <p>利用 GB2312 一级汉字按拼音排序的特性近似匹配首字母；无法判定时返回 'X'。
 */
public final class PinyinUtil {

    private static final int[] GB2312_BOUNDS = {
            0xB0A1, 0xB0C5, 0xB2C1, 0xB4EE, 0xB6EA, 0xB7A2, 0xB8C1, 0xB9FE,
            0xBBF7, 0xBFA6, 0xC0AC, 0xC2E8, 0xC4C3, 0xC5B6, 0xC5BE, 0xC6DA,
            0xC8BB, 0xC8F6, 0xCBFA, 0xCDDA, 0xCEF4, 0xD1B9, 0xD4D1
    };

    private static final char[] INITIALS = {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'L', 'M', 'N',
            'O', 'P', 'Q', 'R', 'S', 'T', 'W', 'X', 'Y', 'Z'
    };

    private static final int GB2312_HANZI_END = 0xF7FE;
    private static final char UNKNOWN = 'X';

    private PinyinUtil() {
    }

    public static String getInitials(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            sb.append(isHanzi(ch) ? initialOf(ch) : ch);
        }
        return sb.toString();
    }

    public static String getFirstThreeInitials(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(3);
        for (int i = 0; i < text.length() && sb.length() < 3; i++) {
            char ch = text.charAt(i);
            if (isHanzi(ch)) {
                sb.append(initialOf(ch));
            }
        }
        return sb.toString();
    }

    private static boolean isHanzi(char ch) {
        return ch >= 0x4E00 && ch <= 0x9FFF;
    }

    private static char initialOf(char ch) {
        int code = toGb2312Code(ch);
        if (code < GB2312_BOUNDS[0] || code > GB2312_HANZI_END) {
            return UNKNOWN;
        }
        int idx = floorIndex(GB2312_BOUNDS, code);
        return idx >= 0 ? INITIALS[idx] : UNKNOWN;
    }

    private static int toGb2312Code(char ch) {
        try {
            byte[] bytes = String.valueOf(ch).getBytes("GB2312");
            if (bytes.length < 2) {
                return -1;
            }
            return ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
        } catch (UnsupportedEncodingException e) {
            return -1;
        }
    }

    private static int floorIndex(int[] arr, int key) {
        int lo = 0;
        int hi = arr.length - 1;
        int ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (arr[mid] <= key) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ans;
    }
}