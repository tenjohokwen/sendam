package com.softropic.sendam.client.service;

import java.util.Set;

/**
 * Utility for calculating the number of SMS segments a message will consume.
 * Uses GSM 03.38 basic character set detection to choose between GSM-7 and UCS-2 encoding.
 *
 * GSM-7 thresholds:  single segment = 160 chars; multi-segment = ceil(length / 153.0)
 * UCS-2 thresholds:  single segment = 70 chars;  multi-segment = ceil(length / 67.0)
 */
final class SmsSegmentCalculator {

    private static final Set<Character> GSM7_CHARS = Set.of(
        // Letters A-Z
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
        'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
        // Letters a-z
        'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
        'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
        // Digits 0-9
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        // Space
        ' ',
        // Special characters from GSM 03.38 basic set
        '@', '\u00A3', '$', '\u00A5', '\u00E8', '\u00E9', '\u00F9', '\u00EC', '\u00F2',
        '\u00C7', '\u00D8', '\u00F8', '\u00C5', '\u00E5',
        // Greek capital letters
        '\u0394', '_', '\u03A6', '\u0393', '\u039B', '\u03A9', '\u03A0', '\u03A8',
        '\u03A3', '\u0398', '\u039E',
        // More special characters
        '\u00C6', '\u00E6', '\u00DF', '\u00C9',
        // Punctuation
        '!', '"', '#', '\u00A4', '%', '&', '\'', '(', ')', '*', '+', ',',
        '-', '.', '/', ':', ';', '<', '=', '>', '?',
        // More special chars
        '\u00A1', '\u00C4', '\u00D6', '\u00D1', '\u00DC', '\u00A7', '\u00BF',
        '\u00E4', '\u00F6', '\u00F1', '\u00FC', '\u00E0',
        // Newlines and carriage return (valid in GSM-7)
        '\n', '\r'
    );

    private SmsSegmentCalculator() {
        // utility class — no instances
    }

    /**
     * Calculates the number of SMS segments required for the given message.
     *
     * @param message the message text
     * @return number of segments (minimum 1)
     */
    static int calculate(String message) {
        if (message == null || message.isEmpty()) {
            return 1;
        }
        int length = message.length();
        if (isAllGsm7(message)) {
            return length <= 160 ? 1 : (int) Math.ceil(length / 153.0);
        } else {
            return length <= 70 ? 1 : (int) Math.ceil(length / 67.0);
        }
    }

    private static boolean isAllGsm7(String message) {
        for (int i = 0; i < message.length(); i++) {
            if (!GSM7_CHARS.contains(message.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
