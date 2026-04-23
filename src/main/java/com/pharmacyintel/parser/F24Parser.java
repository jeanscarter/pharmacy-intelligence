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
 * Reconstructed: Now extracts directly from "NETO $" as USD, as the previous "PRECIO MAYOR (Bs)" was dropped.
 * Calculates basePrice reversing the discounts: DL (%) and PROMO (%).
 */
public class F24Parser implements SupplierParser {

    @Override
    public List<SupplierProduct> parse(File file) throws Exception {
        List<SupplierProduct> products = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
                Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheetAt(0);
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();

            int headerRow = -1;
            int colBarcode = -1, colNetoUsd = -1, colDesc = -1, colStock = -1;
            int colPromo = -1, colDl = -1, colBrand = -1;

            // Scan up to 20 rows for headers
            for (int r = 0; r <= Math.min(20, sheet.getLastRowNum()); r++) {
                Row row = sheet.getRow(r);
                if (row == null)
                    continue;

                int tempBarcode = -1, tempNetoUsd = -1, tempDesc = -1, tempStock = -1;
                int tempPromo = -1, tempDl = -1, tempBrand = -1;

                for (int c = 0; c < row.getLastCellNum(); c++) {
                    String val = getCellString(row.getCell(c), evaluator).trim();
                    String lower = val.toLowerCase().replaceAll("[áàä]", "a")
                            .replaceAll("[éèë]", "e")
                            .replaceAll("[íìï]", "i")
                            .replaceAll("[óòö]", "o")
                            .replaceAll("[úùü]", "u");

                    if (lower.contains("barra") || lower.contains("c. barra") || lower.contains("cod. barra")
                            || lower.contains("codigo barra") || lower.contains("ean")
                            || lower.equals("codigo") || lower.equals("cod")) {
                        tempBarcode = c;
                    } else if ((lower.contains("neto") && (lower.contains("$") || lower.contains("usd"))) || lower.equals("neto $") || lower.equals("neto usd")) {
                        tempNetoUsd = c;
                    } else if (lower.contains("dl") && lower.contains("%")) {
                        tempDl = c;
                    } else if (lower.contains("promo") && lower.contains("%")) {
                        tempPromo = c;
                    } else if (lower.contains("marca") || lower.equals("marca")) {
                        tempBrand = c;
                    } else if (lower.contains("descripcion") || lower.contains("producto")
                            || lower.contains("nombre") || lower.contains("articulo")) {
                        tempDesc = c;
                    } else if (lower.contains("existencia") || lower.contains("stock")
                            || lower.contains("exist") || lower.contains("cantidad")
                            || lower.contains("disp")) {
                        tempStock = c;
                    }
                }

                if (tempBarcode >= 0 && tempNetoUsd >= 0) {
                    headerRow = r;
                    colBarcode = tempBarcode;
                    colNetoUsd = tempNetoUsd;
                    colDesc = tempDesc;
                    colStock = tempStock;
                    colPromo = tempPromo;
                    colDl = tempDl;
                    colBrand = tempBrand;
                    break;
                }
                
                // Fallback (keep tracking if partial match is found)
                if (tempBarcode >= 0) {
                    headerRow = r;
                    colBarcode = tempBarcode;
                    colNetoUsd = tempNetoUsd;
                    colDesc = tempDesc;
                    colStock = tempStock;
                    colPromo = tempPromo;
                    colDl = tempDl;
                    colBrand = tempBrand;
                }
            }

            if (headerRow == -1 || colBarcode == -1) {
                throw new Exception("No se pudo detectar la fila de encabezados de F24. "
                        + "Buscado: columna con 'barra' o 'codigo' y 'neto $' en primeras 20 filas.");
            }

            System.out.println("[F24Parser] Header at row " + headerRow
                    + ", barcode=" + colBarcode + ", netoUsd=" + colNetoUsd
                    + ", desc=" + colDesc + ", stock=" + colStock
                    + ", promo=" + colPromo + ", dl=" + colDl + ", brand=" + colBrand);

            int skippedBarcode = 0, skippedNet = 0, skippedExc = 0;

            for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null)
                    continue;

                try {
                    String barcode = DataSanitizer.cleanBarcode(getCellString(row.getCell(colBarcode), evaluator));
                    String desc = colDesc >= 0 ? DataSanitizer.cleanDescription(getCellString(row.getCell(colDesc), evaluator)) : "";
                    int stock = colStock >= 0 ? DataSanitizer.parseStock(getCellString(row.getCell(colStock), evaluator)) : 1;

                    // Read individual discount percentages
                    double promo = 0, dl = 0;
                    if (colPromo >= 0) {
                        String raw = getCellString(row.getCell(colPromo), evaluator).replace("%", "").trim();
                        promo = DataSanitizer.parseDecimal(raw);
                    }
                    if (colDl >= 0) {
                        String raw = getCellString(row.getCell(colDl), evaluator).replace("%", "").trim();
                        dl = DataSanitizer.parseDecimal(raw);
                    }

                    double netUsd = 0;
                    if (colNetoUsd >= 0) {
                        Cell netCell = row.getCell(colNetoUsd);
                        String cellStr = getCellString(netCell, evaluator);
                        String raw = cellStr.replace("$", "").replace(",", "").trim();
                        try {
                            netUsd = DataSanitizer.parseDecimal(raw);
                        } catch (Exception e) {
                            // Fallback: try direct numeric read
                            if (netCell != null) {
                                try {
                                    netUsd = netCell.getNumericCellValue();
                                } catch (Exception ignored) {}
                            }
                        }
                        
                        // RESCUE: If POI failed to evaluate and cached is 0, parse the formula string directly
                        if (netUsd <= 0 && netCell != null && netCell.getCellType() == CellType.FORMULA) {
                            netUsd = extractPriceFromFormula(netCell, row);
                        }
                    }

                    if (barcode.isEmpty()) {
                        skippedBarcode++;
                        continue;
                    }
                    if (netUsd <= 0) {
                        skippedNet++;
                        continue;
                    }

                    // Reverse calculate the base price in USD
                    double discountMultiplier = (1.0 - promo / 100.0) * (1.0 - dl / 100.0);
                    double basePriceUsd = (discountMultiplier > 0 && discountMultiplier <= 1.0) 
                            ? (netUsd / discountMultiplier) 
                            : netUsd;

                    SupplierProduct sp = new SupplierProduct(barcode, desc, basePriceUsd, 0, stock, Supplier.F24);
                    sp.setDiscounts(promo, dl);
                    
                    if (colBrand >= 0) {
                        String brandText = getCellString(row.getCell(colBrand), evaluator).trim();
                        if (!brandText.isEmpty()) {
                            sp.setBrand(brandText);
                        }
                    }

                    products.add(sp);
                } catch (Exception e) {
                    skippedExc++;
                    if (skippedExc <= 3) {
                        System.out.println("[F24Parser] EXCEPTION row " + r + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    }
                }
            }
            System.out.println("[F24Parser] Summary: skippedBarcode=" + skippedBarcode 
                    + ", skippedNetUsd=" + skippedNet + ", skippedExceptions=" + skippedExc);
        }

        System.out.println("[F24Parser] Parsed " + products.size() + " products");
        return products;
    }

    private String getCellString(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null)
            return "";
        
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            try {
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val))
                    return String.valueOf((long) val);
                return String.valueOf(val);
            } catch (Exception e) {
                // Continuar a evaluar si falla la caché
            }
            
            if (evaluator != null) {
                try {
                    CellValue cellValue = evaluator.evaluate(cell);
                    return switch (cellValue.getCellType()) {
                        case STRING -> cellValue.getStringValue();
                        case NUMERIC -> {
                            double val = cellValue.getNumberValue();
                            if (val == Math.floor(val) && !Double.isInfinite(val))
                                yield String.valueOf((long) val);
                            yield String.valueOf(val);
                        }
                        case BOOLEAN -> String.valueOf(cellValue.getBooleanValue());
                        default -> "";
                    };
                } catch (Exception e) {
                    // Fallback a string
                }
            }
            
            try {
                return cell.getStringCellValue();
            } catch (Exception e) {
                return "";
            }
        }

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val))
                    yield String.valueOf((long) val);
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    /** Extracts USD price directly from the formula AST when POI fails to evaluate and cache is 0 */
    private double extractPriceFromFormula(Cell netUsdCell, Row row) {
        try {
            String usdFormula = netUsdCell.getCellFormula().toUpperCase();
            
            // Expected format: ROUND(I3 / 483.34, 2)
            // 1. Extract the exchange rate (the divisor)
            double exchangeRate = 0;
            java.util.regex.Matcher mRate = java.util.regex.Pattern.compile("/\\s*([0-9]+\\.?[0-9]*)").matcher(usdFormula);
            if (mRate.find()) {
                exchangeRate = Double.parseDouble(mRate.group(1));
            }

            if (exchangeRate > 0) {
                // 2. Extract the cell reference for the Bolivar Neto (e.g., 'I3')
                java.util.regex.Matcher mCell = java.util.regex.Pattern.compile("([A-Z]+)[0-9]+").matcher(usdFormula);
                if (mCell.find()) {
                    String colStr = mCell.group(1);
                    int bsColIdx = org.apache.poi.ss.util.CellReference.convertColStringToIndex(colStr);
                    Cell bsCell = row.getCell(bsColIdx);
                    
                    // 3. Inspect the Bolivar cell formula
                    if (bsCell != null && bsCell.getCellType() == CellType.FORMULA) {
                        String bsFormula = bsCell.getCellFormula().toUpperCase();
                        
                        // Expected format: IF(ISBLANK(K3), ROUND(3815.2928, 2), ...)
                        // We find all ROUND(number, 2) and take the maximum as the base price
                        java.util.regex.Matcher mBs = java.util.regex.Pattern.compile("ROUND\\(\\s*([0-9]+\\.?[0-9]*)\\s*,").matcher(bsFormula);
                        double maxBs = 0;
                        while (mBs.find()) {
                            double val = Double.parseDouble(mBs.group(1));
                            if (val > maxBs) {
                                maxBs = val;
                            }
                        }
                        
                        if (maxBs > 0) {
                            return maxBs / exchangeRate;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Silently fall through if formula parsing fails
        }
        return 0;
    }
}
