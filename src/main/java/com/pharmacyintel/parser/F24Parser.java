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
 * Parser for F24 (Farma 24) Excel files.
 * Extracts: Base = PRECIO MAYOR (Bs), Offer = sum of PROMO(%) + OFERTA(%) +
 * DA(%).
 * NetPrice is computed: basePrice * (1 - offerPct / 100).
 * Prices are in Bs — the SyncOrchestrator handles conversion to USD.
 */
public class F24Parser implements SupplierParser {

    @Override
    public List<SupplierProduct> parse(File file) throws Exception {
        List<SupplierProduct> products = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
                Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheetAt(0);

            int headerRow = -1;
            int colBarcode = -1, colPrice = -1, colDesc = -1, colStock = -1;
            int colPromo = -1, colOferta = -1, colDa = -1;

            // Scan up to 20 rows for headers
            for (int r = 0; r <= Math.min(20, sheet.getLastRowNum()); r++) {
                Row row = sheet.getRow(r);
                if (row == null)
                    continue;

                int tempBarcode = -1, tempPrice = -1, tempDesc = -1, tempStock = -1;
                int tempPromo = -1, tempOferta = -1, tempDa = -1;

                for (int c = 0; c < row.getLastCellNum(); c++) {
                    String val = getCellString(row.getCell(c)).trim();
                    String lower = val.toLowerCase().replaceAll("[áàä]", "a")
                            .replaceAll("[éèë]", "e")
                            .replaceAll("[íìï]", "i")
                            .replaceAll("[óòö]", "o")
                            .replaceAll("[úùü]", "u");

                    if (lower.contains("barra") || lower.contains("c. barra") || lower.contains("cod. barra")
                            || lower.contains("codigo barra") || lower.contains("ean")
                            || lower.equals("codigo") || lower.equals("cod")) {
                        tempBarcode = c;
                    } else if (lower.contains("precio") && lower.contains("mayor") && lower.contains("bs")) {
                        // PRECIO MAYOR (Bs) — explicitly in Bs
                        tempPrice = c;
                    } else if (lower.contains("promo") && lower.contains("%")) {
                        tempPromo = c;
                    } else if (lower.contains("oferta") && lower.contains("%")) {
                        tempOferta = c;
                    } else if (lower.contains("da") && lower.contains("%")) {
                        tempDa = c;
                    } else if (lower.contains("descripcion") || lower.contains("producto")
                            || lower.contains("nombre") || lower.contains("articulo")) {
                        tempDesc = c;
                    } else if (lower.contains("existencia") || lower.contains("stock")
                            || lower.contains("exist") || lower.contains("cantidad")
                            || lower.contains("disp")) {
                        tempStock = c;
                    }
                }

                if (tempBarcode >= 0 && tempPrice >= 0) {
                    headerRow = r;
                    colBarcode = tempBarcode;
                    colPrice = tempPrice;
                    colDesc = tempDesc;
                    colStock = tempStock;
                    colPromo = tempPromo;
                    colOferta = tempOferta;
                    colDa = tempDa;
                    break;
                }
                if (tempBarcode >= 0) {
                    headerRow = r;
                    colBarcode = tempBarcode;
                    colDesc = tempDesc;
                    colStock = tempStock;
                    colPromo = tempPromo;
                    colOferta = tempOferta;
                    colDa = tempDa;
                    break;
                }
            }

            if (headerRow == -1 || colBarcode == -1) {
                throw new Exception("No se pudo detectar la fila de encabezados de F24. "
                        + "Buscado: columna con 'barra', 'codigo', 'ean' en primeras 20 filas.");
            }

            // Infer price column from data if not detected
            if (colPrice == -1) {
                colPrice = inferPriceColumn(sheet, headerRow, colBarcode, colDesc);
            }

            System.out.println("[F24Parser] Header at row " + headerRow
                    + ", barcode=" + colBarcode + ", price=" + colPrice
                    + ", desc=" + colDesc + ", stock=" + colStock
                    + ", promo=" + colPromo + ", oferta=" + colOferta + ", da=" + colDa);

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

                    // Sum discount from PROMO(%) + OFERTA(%) + DA(%)
                    double offerPct = 0;
                    if (colPromo >= 0) {
                        String raw = getCellString(row.getCell(colPromo)).replace("%", "").trim();
                        offerPct += DataSanitizer.parseDecimal(raw);
                    }
                    if (colOferta >= 0) {
                        String raw = getCellString(row.getCell(colOferta)).replace("%", "").trim();
                        offerPct += DataSanitizer.parseDecimal(raw);
                    }
                    if (colDa >= 0) {
                        String raw = getCellString(row.getCell(colDa)).replace("%", "").trim();
                        offerPct += DataSanitizer.parseDecimal(raw);
                    }

                    if (barcode.isEmpty() || basePrice <= 0)
                        continue;

                    SupplierProduct sp = new SupplierProduct(barcode, desc, basePrice, offerPct, stock, Supplier.F24);
                    products.add(sp);
                } catch (Exception e) {
                    // Skip malformed rows
                }
            }
        }

        System.out.println("[F24Parser] Parsed " + products.size() + " products");
        return products;
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
