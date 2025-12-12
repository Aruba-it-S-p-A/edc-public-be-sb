package edc.util;

import java.text.Normalizer;

public class EdcUtils {


    /**
     * Normalizes a string to a DNS hostname-compatible format (RFC 1123).
     *
     * @param input input string
     * @return normalized string
     */
    public static String normalizeForInnerDnsUse(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        // Convert to lowercase and remove accents/diacritics
        String normalized = Normalizer.normalize(input.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", ""); // removes accents, cedillas, etc.

        // Remove disallowed characters
        normalized = normalized.replaceAll("[^a-z0-9]", "");

        // Remove any leading/trailing '-' from each label
        String[] labels = normalized.split("\\.");
        for (int i = 0; i < labels.length; i++) {
            labels[i] = labels[i].replaceAll("^-+", "").replaceAll("-+$", "");
            if (labels[i].length() > 63) {
                labels[i] = labels[i].substring(0, 63); // RFC 1123
            }
        }

        // Reassemble and limit overall length
        String result = String.join(".", labels);
        if (result.length() > 63) {
            result = result.substring(0, 63);
        }

        return result;
    }

    public static String normalizeForDnsUse(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        // Convert to lowercase and remove accents/diacritics
        String normalized = Normalizer.normalize(input.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", ""); // removes accents, cedillas, etc.

        // Replace disallowed characters with '-'
        normalized = normalized.replaceAll("[^a-z0-9.-]", "-");

        // Remove any leading/trailing '-' from each label
        String[] labels = normalized.split("\\.");
        for (int i = 0; i < labels.length; i++) {
            labels[i] = labels[i].replaceAll("^-+", "").replaceAll("-+$", "");
            if (labels[i].length() > 63) {
                labels[i] = labels[i].substring(0, 63); // RFC 1123
            }
        }

        // Reassemble and limit overall length
        String result = String.join(".", labels);
        if (result.length() > 253) {
            result = result.substring(0, 253);
        }

        return result;
    }

}
