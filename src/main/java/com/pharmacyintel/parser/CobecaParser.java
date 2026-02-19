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
 * Parser for Cobeca Excel files.
 * Detects header row by keyword matching: Codigo_Barra,
 * Precio_Referencial_Final, Existencia.
 */
public class CobecaParser implements SupplierParser {

    @Override
    public List<SupplierProduct> parse(File file) throws Exception {
        List<SupplierProduct> products = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
                Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheetAt(0);

            // Scan for header row (first 10 rows)
            int headerRow = -1;
            int colBarcode = -1, colPrice = -1, colStock = -1, colDesc = -1;

            for (int r = 0; r <= Math.min(10, sheet.getLastRowNum()); r++) {
                Row row = sheet.getRow(r);
                if (row == null)
                    continue;
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    String val = getCellString(row.getCell(c)).toLowerCase().trim();
                    if (val.contains("codigo") && val.contains("barra"))
                        colBarcode = c;
                    else if (val.contains("codigo_barra"))
                        colBarcode = c;
                    else if (val.contains("precio_referencial_final") || val.contains("precio referencial final"))
                        colPrice = c;
                    else if (val.contains("precio") && val.contains("final") && colPrice == -1)
                        colPrice = c;
                    else if (val.contains("existencia") || val.contains("exist"))
                        colStock = c;
                    else if (val.contains("descripcion") || val.contains("producto") || val.contains("nombre"))
                        colDesc = c;
                }
                if (colBarcode >= 0 && colPrice >= 0) {
                    headerRow = r;
                    break;
                }
            }

            if (headerRow == -1) {
                // Fallback: try generic detection
                throw new Exception(
                        "Could not detect Cobeca header row. Expected columns: Codigo_Barra, Precio_Referencial_Final");
            }

            // Parse data rows
            for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null)
                    continue;

                try {
                    String barcode = DataSanitizer.cleanBarcode(getCellString(row.getCell(colBarcode)));
                    double price = DataSanitizer.parseDecimal(getCellString(row.getCell(colPrice)));
                    int stock = colStock >= 0 ? DataSanitizer.parseStock(getCellString(row.getCell(colStock))) : 1;
                    String desc = colDesc >= 0 ? DataSanitizer.cleanDescription(getCellString(row.getCell(colDesc)))
                            : "";

                    if (barcode.isEmpty() || price <= 0)
                        continue;

                    SupplierProduct sp = new SupplierProduct(barcode, desc, price, stock, Supplier.COBECA);
                    sp.setPriceUsd(price);
                    products.add(sp);
                } catch (Exception e) {
                    // Skip malformed rows
                }
            }
        }
        return products;
    }

    private String getCellString(Cell cell) {
        if (cell == null)
            return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    yield String.valueOf((long) val);
                }
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
