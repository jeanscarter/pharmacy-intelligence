package com.pharmacyintel.parser;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Parser for the optional IMS Excel file.
 * Expected headers: COD INT, COD IMS
 * Returns Map<COD_INT, COD_IMS> for fast merging.
 */
public class ImsParser {

    private final DataFormatter formatter = new DataFormatter();

    /**
     * Parse the IMS Excel file and return a mapping of internal code to IMS code.
     *
     * @param file the Excel file to parse
     * @return Map where key = COD INT, value = COD IMS
     */
    public Map<String, String> parse(File file) {
        Map<String, String> imsMap = new HashMap<>();

        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheetAt(0);

            int headerRow = -1;
            int colCodInt = -1;
            int colCodIms = -1;

            // Search for headers in the first 15 rows
            for (int r = 0; r <= Math.min(15, sheet.getLastRowNum()); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                int codIntHit = -1;
                int codImsHit = -1;

                for (int c = 0; c < row.getLastCellNum(); c++) {
                    String val = formatter.formatCellValue(row.getCell(c)).trim();
                    if (val.equalsIgnoreCase("COD INT") || val.equalsIgnoreCase("COD_INT") || val.equalsIgnoreCase("CODINT")) {
                        codIntHit = c;
                    }
                    if (val.equalsIgnoreCase("COD IMS") || val.equalsIgnoreCase("COD_IMS") || val.equalsIgnoreCase("CODIMS")) {
                        codImsHit = c;
                    }
                }

                if (codIntHit >= 0 && codImsHit >= 0) {
                    headerRow = r;
                    colCodInt = codIntHit;
                    colCodIms = codImsHit;
                    break;
                }
            }

            if (headerRow == -1 || colCodInt == -1 || colCodIms == -1) {
                System.err.println("[ImsParser] No se encontraron las columnas COD INT / COD IMS en el archivo.");
                return imsMap;
            }

            // Read the data rows
            for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                try {
                    String codInt = formatter.formatCellValue(row.getCell(colCodInt)).trim();
                    String codIms = formatter.formatCellValue(row.getCell(colCodIms)).trim();
                    
                    // Zero-pad numeric internal codes to 6 digits to match DroActiva format
                    if (codInt.matches("\\d+") && codInt.length() < 6) {
                        try {
                            codInt = String.format("%06d", Long.parseLong(codInt));
                        } catch (Exception ignored) {}
                    }

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
}
