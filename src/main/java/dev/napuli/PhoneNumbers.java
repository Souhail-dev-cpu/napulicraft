package dev.napuli;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.Locale;

/** Numbers are identifiers inside a Minecraft world; no external telephone network is used. */
public final class PhoneNumbers {
    private static final SecureRandom RANDOM = new SecureRandom();
    public static String normalize(String input) {
        if (input == null || input.length() > 32 || !input.matches("[+0-9\\s().-]+")) return "";
        String value = input.replaceAll("[\\s().-]", "");
        if (value.startsWith("0039")) value = "+39" + value.substring(4);
        if (!value.startsWith("+")) value = "+39" + value;
        return value.matches("\\+39[0-9]{9,10}") ? value : "";
    }
    static String allocate(Collection<String> used) {
        String number;
        do { number = "+393" + String.format(Locale.ROOT, "%09d", RANDOM.nextInt(1_000_000_000)); }
        while (used.contains(number));
        return number;
    }
    public static String format(String number) {
        String value = normalize(number);
        if (value.isEmpty()) return number;
        String digits = value.substring(3);
        return "+39 " + digits.substring(0, 3) + " " + digits.substring(3, 6) + " " + digits.substring(6);
    }
    private PhoneNumbers() {}
}
