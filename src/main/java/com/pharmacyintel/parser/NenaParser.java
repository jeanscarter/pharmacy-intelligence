package com.pharmacyintel.parser;

import com.pharmacyintel.model.Supplier;
import com.pharmacyintel.model.SupplierProduct;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for Nena Excel files.
 * Extracts: Base = PRECIO (REFERENCIAL) in Bs, Offer = from DCTO. NENA column.
 * Offer is extracted from text like "Dcto. nena del 7,00%".
 * NetPrice is computed: basePrice * (1 - offerPct / 100).
 * Prices are in Bs — the SyncOrchestrator handles conversion to USD.
 */
public class NenaParser implements SupplierParser {

    private static final Pattern DCTO_PATTERN = Pattern.compile("(\\d+[.,]?\\d*)\\s*%", Pattern.CASE_INSENSITIVE);

    @Override
    public List<SupplierProduct> parse(File file) throws Exception {
        List<SupplierProduct> products = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
                Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheetAt(0);

            int headerRow = -1;
            int colBarcode = -1, colPrice = -1, colDesc = -1, colStock = -1, colDcto = -1;

            for (int r = 0; r <= Math.min(15, sheet.getLastRowNum()); r++) {
                Row row = sheet.getRow(r);
                if (row == null)
                    continue;

                int tempBarcode = -1, tempPrice = -1, tempDesc = -1, tempStock = -1, tempDcto = -1;

                for (int c = 0; c < row.getLastCellNum(); c++) {
                    String val = getCellString(row.getCell(c)).trim();
                    String lower = val.toLowerCase().replaceAll("[áàä]", "a")
                            .replaceAll("[éèë]", "e")
                            .replaceAll("[íìï]", "i")
                            .replaceAll("[óòö]", "o")
                            .replaceAll("[úùü]", "u");

                    if (lower.contains("barra") || lower.contains("cod. barra") || lower.contains("codigo barra")
                            || lower.contains("ean") || lower.contains("upc")
                            || lower.equals("codigo") || lower.equals("cod")) {
                        tempBarcode = c;
                    } else if (lower.contains("precio") && lower.contains("referencial")
                            && !lower.contains("externo") && !lower.contains("promo")) {
                        tempPrice = c;
                    } else if (lower.contains("dcto") && lower.contains("nena")) {
                        tempDcto = c;
                    } else if (lower.contains("descripcion") || lower.contains("producto")
                            || lower.contains("nombre") || lower.contains("articulo")) {
                        tempDesc = c;
                    } else if (lower.contains("existencia") || lower.contains("stock")
                            || lower.contains("exist") || lower.contains("cantidad")
                            || lower.contains("disp")) {
                        tempStock = c;
                    } else if (tempPrice == -1 && (lower.contains("precio") || lower.contains("costo"))) {
                        // Fallback price detection
                        tempPrice = c;
                    }
                }

                if (tempBarcode >= 0 && tempPrice >= 0) {
                    headerRow = r;
                    colBarcode = tempBarcode;
                    colPrice = tempPrice;
                    colDesc = tempDesc;
                    colStock = tempStock;
                    colDcto = tempDcto;
                    break;
                }
                if (tempBarcode >= 0) {
                    headerRow = r;
                    colBarcode = tempBarcode;
                    colDesc = tempDesc;
                    colStock = tempStock;
                    colDcto = tempDcto;
                    break;
                }
            }

            if (headerRow == -1 || colBarcode == -1) {
                throw new Exception("No se pudo detectar la fila de encabezados de Nena. "
                        + "Buscado: columna con 'barra', 'codigo', 'ean' en primeras 15 filas.");
            }

            if (colPrice == -1) {
                colPrice = inferPriceColumn(sheet, headerRow, colBarcode, colDesc);
            }

            System.out.println("[NenaParser] Header at row " + headerRow
                    + ", barcode=" + colBarcode + ", price=" + colPrice
                    + ", desc=" + colDesc + ", stock=" + colStock + ", dcto=" + colDcto);

            for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null)
                    continue;

                try {
                    String barcode = DataSanitizer.cleanBarcode(getCellString(row.getCell(colBarcode)));
                    double basePrice = colPrice >= 0
                            ? DataSanitizer.parseDecimal(getCellString(row.getCell(colPrice)))
                            : 0;
                    String desc = colDesc >= 0 ? DataSanitizer.cleanDescription(getCellString(row.getCell(colDesc)))
                            : "";
                    int stock = colStock >= 0 ? DataSanitizer.parseStock(getCellString(row.getCell(colStock))) : 1;

                    // Parse discount from DCTO. EN FACTURA column
                    double offerPct = 0;
                    if (colDcto >= 0) {
                        String dctoRaw = getCellString(row.getCell(colDcto)).trim();
                        offerPct = extractDctoPercentage(dctoRaw);
                    }

                    if (barcode.isEmpty() || basePrice <= 0)
                        continue;

                    SupplierProduct sp = new SupplierProduct(barcode, desc, basePrice, offerPct, stock, Supplier.NENA);
                    products.add(sp);
                } catch (Exception e) {
                    // Skip malformed rows
                }
            }
        }

        System.out.println("[NenaParser] Parsed " + products.size() + " products");
        return products;
    }

    /**
     * Extract percentage from text like "Dcto en factura de 20,00%" or "15%".
     * Returns 0 if no percentage found.
     */
    private double extractDctoPercentage(String text) {
        if (text == null || text.isBlank())
            return 0;
        Matcher m = DCTO_PATTERN.matcher(text);
        if (m.find()) {
            String numStr = m.group(1).replace(",", ".");
            try {
                return Double.parseDouble(numStr);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        // Try parsing as plain number (if it's just a numeric value)
        try {
            double val = DataSanitizer.parseDecimal(text);
            return val > 0 && val <= 100 ? val : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private int inferPriceColumn(Sheet sheet, int headerRow, int skipCol1, int skipCol2) {
        for (int r = headerRow + 1; r <= Math.min(headerRow + 5, sheet.getLastRowNum()); r++) {
            Row row = sheet.getRow(r);
            if (row == null)
                continue;
            for (int c = 0; c < row.getLastCellNum(); c++) {
                if (c == skipCol1 || c == skipCol2)
                    continue;
                Cell cell = row.getCell(c);
                if (cell != null && cell.getCellType() == CellType.NUMERIC) {
                    double val = cell.getNumericCellValue();
                    if (val > 0.01 && val < 999999)
                        return c;
                }
            }
        }
        return -1;
    }

    private String getCellString(Cell cell) {
        if (cell == null)
            return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val))
                    yield String.valueOf((long) val);
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    try {
                        yield cell.getStringCellValue();
                    } catch (Exception e2) {
                        yield "";
                    }
                }
            }
            default -> "";
        };
    }
}
