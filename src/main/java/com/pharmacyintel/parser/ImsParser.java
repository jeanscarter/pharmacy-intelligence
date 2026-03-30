package com.pharmacyintel.parser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Parser for the optional IMS CSV file.
 * Expected headers: COD INT,COD IMS,PROVEEDOR,DESCRIPCIÓN DEL PRODUCTO
 * Returns Map<COD_INT, COD_IMS> for fast merging.
 */
public class ImsParser {

    /**
     * Parse the IMS CSV file and return a mapping of internal code to IMS code.
     *
     * @param file the CSV file to parse
     * @return Map where key = COD INT, value = COD IMS
     */
    public Map<String, String> parse(File file) {
        Map<String, String> imsMap = new HashMap<>();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {

            String headerLine = br.readLine();
            if (headerLine == null)
                return imsMap;

            // Remove UTF-8 BOM if present
            headerLine = headerLine.replace("\uFEFF", "");

            // Detect delimiter: try comma first, then semicolon
            String delimiter = headerLine.contains(";") ? ";" : ",";
            String[] headers = headerLine.split(delimiter, -1);

            int colCodInt = findCol(headers, "COD INT");
            int colCodIms = findCol(headers, "COD IMS");

            if (colCodInt < 0 || colCodIms < 0) {
                System.err.println("[ImsParser] No se encontraron las columnas COD INT / COD IMS en el archivo.");
                return imsMap;
            }

            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank())
                    continue;
                try {
                    String[] cols = line.split(delimiter, -1);
                    String codInt = safeGet(cols, colCodInt).trim();
                    String codIms = safeGet(cols, colCodIms).trim();

                    if (!codInt.isEmpty() && !codIms.isEmpty()) {
                        imsMap.put(codInt, codIms);
                    }
                } catch (Exception e) {
                    // Skip malformed rows silently
                }
            }
        } catch (Exception e) {
            System.err.println("[ImsParser] Error al leer archivo IMS: " + e.getMessage());
        }

        return imsMap;
    }

    private int findCol(String[] headers, String keyword) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(keyword))
                return i;
        }
        return -1;
    }

    private String safeGet(String[] cols, int idx) {
        if (idx < 0 || idx >= cols.length)
            return "";
        return cols[idx];
    }
}
