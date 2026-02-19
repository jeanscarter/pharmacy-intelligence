package com.pharmacyintel.parser;

import com.pharmacyintel.model.Supplier;
import com.pharmacyintel.model.SupplierProduct;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for Nena Excel files.
 * Very flexible header detection: scans first 15 rows for any column containing
 * "barra" keyword for barcode, and "precio"/"neto" for price.
 * Prices are in Bs — the SyncOrchestrator handles conversion to USD.
 */
public class NenaParser implements SupplierParser {

    @Override
    public List<SupplierProduct> parse(File file) throws Exception {
        List<SupplierProduct> products = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
                Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheetAt(0);

            // Flexible header detection: scan up to 15 rows
            int headerRow = -1;
            int colBarcode = -1, colPrice = -1, colDesc = -1, colStock = -1;

            for (int r = 0; r <= Math.min(15, sheet.getLastRowNum()); r++) {
                Row row = sheet.getRow(r);
                if (row == null)
                    continue;

                int tempBarcode = -1, tempPrice = -1, tempDesc = -1, tempStock = -1;

                for (int c = 0; c < row.getLastCellNum(); c++) {
                    String val = getCellString(row.getCell(c)).trim();
                    String lower = val.toLowerCase().replaceAll("[áàä]", "a")
                            .replaceAll("[éèë]", "e")
                            .replaceAll("[íìï]", "i")
                            .replaceAll("[óòö]", "o")
                            .replaceAll("[úùü]", "u");

                    // Barcode column detection (broadest match)
                    if (lower.contains("barra") || lower.contains("cod. barra") || lower.contains("codigo barra")
                            || lower.contains("ean") || lower.contains("upc")
                            || lower.equals("codigo") || lower.equals("cod")) {
                        tempBarcode = c;
                    }
                    // Price column detection
                    else if (lower.contains("precio") || lower.contains("neto")
                            || lower.contains("monto") || lower.contains("valor")
                            || lower.contains("pvp") || lower.contains("costo")) {
                        if (tempPrice == -1)
                            tempPrice = c; // Take first price column
                    }
                    // Description column
                    else if (lower.contains("descripcion") || lower.contains("producto")
                            || lower.contains("nombre") || lower.contains("articulo")) {
                        tempDesc = c;
                    }
                    // Stock column
                    else if (lower.contains("existencia") || lower.contains("stock")
                            || lower.contains("exist") || lower.contains("cantidad")
                            || lower.contains("disp")) {
                        tempStock = c;
                    }
                }

                // Found a valid header with at least barcode
                if (tempBarcode >= 0 && tempPrice >= 0) {
                    headerRow = r;
                    colBarcode = tempBarcode;
                    colPrice = tempPrice;
                    colDesc = tempDesc;
                    colStock = tempStock;
                    break;
                }
                // Accept even if we only found barcode (price may be numeric-only)
                if (tempBarcode >= 0) {
                    headerRow = r;
                    colBarcode = tempBarcode;
                    colDesc = tempDesc;
                    colStock = tempStock;
                    // Try to find price by looking for first numeric column after barcode
                    break;
                }
            }

            if (headerRow == -1 || colBarcode == -1) {
                throw new Exception("No se pudo detectar la fila de encabezados de Nena. "
                        + "Buscado: columna con 'barra', 'codigo', 'ean' en primeras 15 filas.");
            }

            // If price column wasn't detected by header, try to infer from data
            if (colPrice == -1) {
                colPrice = inferPriceColumn(sheet, headerRow, colBarcode, colDesc);
            }

            System.out.println("[NenaParser] Header at row " + headerRow
                    + ", barcode=" + colBarcode + ", price=" + colPrice
                    + ", desc=" + colDesc + ", stock=" + colStock);

            for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null)
                    continue;

                try {
                    String barcode = DataSanitizer.cleanBarcode(getCellString(row.getCell(colBarcode)));
                    double price = colPrice >= 0 ? DataSanitizer.parseDecimal(getCellString(row.getCell(colPrice))) : 0;
                    String desc = colDesc >= 0 ? DataSanitizer.cleanDescription(getCellString(row.getCell(colDesc)))
                            : "";
                    int stock = colStock >= 0 ? DataSanitizer.parseStock(getCellString(row.getCell(colStock))) : 1;

                    if (barcode.isEmpty() || price <= 0)
                        continue;

                    SupplierProduct sp = new SupplierProduct(barcode, desc, price, stock, Supplier.NENA);
                    sp.setPriceUsd(price); // Will be converted Bs->USD by orchestrator
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
     * Infer price column from data rows: find first column with consistent numeric
     * values
     */
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
                    if (val > 0.01 && val < 999999) { // Reasonable price range
                        return c;
                    }
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
