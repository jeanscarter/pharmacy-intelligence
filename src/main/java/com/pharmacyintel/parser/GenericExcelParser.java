package com.pharmacyintel.parser;

import com.pharmacyintel.model.Supplier;
import com.pharmacyintel.model.SupplierProduct;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

/**
 * Generic Excel parser for 365/Dromarko and unrecognized supplier files.
 * Uses keyword-similarity header detection to map columns dynamically.
 */
public class GenericExcelParser implements SupplierParser {

    private final Supplier supplier;

    public GenericExcelParser(Supplier supplier) {
        this.supplier = supplier;
    }

    @Override
    public List<SupplierProduct> parse(File file) throws Exception {
        List<SupplierProduct> products = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
                Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheetAt(0);

            // Scan first 15 rows for header
            int headerRow = -1;
            int colBarcode = -1, colPrice = -1, colDesc = -1, colStock = -1;

            for (int r = 0; r <= Math.min(15, sheet.getLastRowNum()); r++) {
                Row row = sheet.getRow(r);
                if (row == null)
                    continue;

                int barcodeHit = -1, priceHit = -1, descHit = -1, stockHit = -1;

                for (int c = 0; c < row.getLastCellNum(); c++) {
                    String val = getCellString(row.getCell(c)).toLowerCase().trim();
                    if (val.isEmpty())
                        continue;

                    // Barcode detection
                    if (val.contains("barra") || val.contains("ean") || val.contains("upc")
                            || val.contains("codigo_barra") || val.contains("cod. barra")
                            || val.contains("código barra") || val.contains("cod barra")) {
                        barcodeHit = c;
                    }
                    // Price detection (neto, precio, price)
                    if ((val.contains("neto") && (val.contains("$") || val.contains("usd")))
                            || val.contains("precio") && (val.contains("$") || val.contains("usd")
                                    || val.contains("final") || val.contains("referencial"))) {
                        priceHit = c;
                    }
                    // Description detection
                    if (val.contains("descripcion") || val.contains("producto") || val.contains("nombre")
                            || val.contains("articulo")) {
                        descHit = c;
                    }
                    // Stock detection
                    if (val.contains("existencia") || val.contains("stock") || val.contains("disponible")
                            || val.contains("cantidad")) {
                        stockHit = c;
                    }
                }

                if (barcodeHit >= 0 && priceHit >= 0) {
                    headerRow = r;
                    colBarcode = barcodeHit;
                    colPrice = priceHit;
                    colDesc = descHit;
                    colStock = stockHit;
                    break;
                }
            }

            if (headerRow == -1 || colBarcode == -1) {
                throw new Exception("Could not detect headers for " + supplier.getDisplayName()
                        + ". Expected columns with keywords: barra, neto/precio");
            }

            for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null)
                    continue;

                try {
                    String barcode = DataSanitizer.cleanBarcode(getCellString(row.getCell(colBarcode)));
                    double price = DataSanitizer.parseDecimal(getCellString(row.getCell(colPrice)));
                    String desc = colDesc >= 0 ? DataSanitizer.cleanDescription(getCellString(row.getCell(colDesc)))
                            : "";
                    int stock = colStock >= 0 ? DataSanitizer.parseStock(getCellString(row.getCell(colStock))) : 1;

                    if (barcode.isEmpty() || price <= 0)
                        continue;

                    SupplierProduct sp = new SupplierProduct(barcode, desc, price, stock, supplier);
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
