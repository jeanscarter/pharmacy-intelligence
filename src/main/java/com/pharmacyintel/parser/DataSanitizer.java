package com.pharmacyintel.parser;

/** Static utilities for data cleaning and normalization */
public final class DataSanitizer {

    private DataSanitizer() {
    }

    /** Remove leading zeros, spaces, and special characters from barcode */
    public static String cleanBarcode(String raw) {
        if (raw == null)
            return "";
        String cleaned = raw.trim().replaceAll("[^0-9a-zA-Z]", "");
        // Remove leading zeros only if very long numeric string
        if (cleaned.matches("^0+\\d{6,}$")) {
            cleaned = cleaned.replaceFirst("^0+", "");
        }
        return cleaned;
    }

    /**
     * Parse a decimal value that may use comma as decimal separator
     * and dot as thousands separator (Venezuelan/European format).
     * Examples: "1.351,75" -> 1351.75, "7,94" -> 7.94, "3.39" -> 3.39
     */
    public static double parseDecimal(String raw) {
        if (raw == null || raw.isBlank())
            return 0;
        String cleaned = raw.trim();
        // Remove pipe characters and extra spaces
        cleaned = cleaned.replaceAll("[|\\s]", "");

        if (cleaned.isEmpty() || cleaned.equals("-"))
            return 0;

        // Detect format: if contains both dot and comma, determine which is decimal
        boolean hasComma = cleaned.contains(",");
        boolean hasDot = cleaned.contains(".");

        if (hasComma && hasDot) {
            int lastComma = cleaned.lastIndexOf(',');
            int lastDot = cleaned.lastIndexOf('.');
            if (lastComma > lastDot) {
                // Comma is decimal: "1.351,75" → remove dots, replace comma with dot
                cleaned = cleaned.replace(".", "").replace(",", ".");
            } else {
                // Dot is decimal: "1,351.75" → remove commas
                cleaned = cleaned.replace(",", "");
            }
        } else if (hasComma) {
            // Only comma: could be "7,94" (decimal) or "1,000" (thousands)
            // If exactly 2 digits after comma, treat as decimal
            int commaPos = cleaned.lastIndexOf(',');
            String afterComma = cleaned.substring(commaPos + 1);
            if (afterComma.length() <= 2) {
                cleaned = cleaned.replace(",", ".");
            } else {
                cleaned = cleaned.replace(",", "");
            }
        }
        // If only dot: standard format, leave as-is

        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Parse integer stock value, tolerant of formatting */
    public static int parseStock(String raw) {
        if (raw == null || raw.isBlank())
            return 0;
        String cleaned = raw.trim().replaceAll("[^0-9\\-]", "");
        if (cleaned.isEmpty())
            return 0;
        try {
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Strip special characters but keep alphanumeric, spaces, accented chars */
    public static String cleanDescription(String raw) {
        if (raw == null)
            return "";
        return raw.trim().replaceAll("\\s+", " ");
    }

    /** Parse IVA percentage */
    public static double parseIva(String raw) {
        return parseDecimal(raw);
    }
}
