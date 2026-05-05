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
 * Parser for CristMedicals "LISTADO VIP" Excel files.
 * 
 * <p>File structure (Sheet: "CRIST_MEDICALS_C.A ", header at row 7):
 * <pre>
 *   Col 0:  CODIGO DE BARRA (barcode)
 *   Col 1:  CATEGORIA (category)
 *   Col 2:  LABORATORIO (laboratory / brand)
 *   Col 3:  V (vencimiento flag)
 *   Col 4:  PRODUCTO — LISTA ORDENADA POR: PRINCIPIO ACTIVO; PRESENTACION...
 *   Col 5:  E/G (genérico flag)
 *   Col 6:  % (percentage flag)
 *   Col 8:  STOCK
 *   Col 9:  PRECIOS (list price USD)
 *   Col 10: PRECIO CON % (price with line discount)
 *   Col 16: DESCUENTO (discount amount)
 *   Col 17: PRECIO SIN % (price without line discount)
 *   Col 18: PRECIO CON DESCT. (price with discount applied)
 * </pre>
 * 
 * <p><b>Financial discount logic:</b> Row 1 Col 14 contains the "Contado" discount
 * rate (0.07 = 7%). This is applied over the list price to calculate the cash-purchase
 * net price: {@code netPrice = PRECIOS × (1 - 0.07)}.
 */
public class CristMedicalsParser implements SupplierParser {

    /** Target sheet name in the CristMedicals workbook */
    private static final String SHEET_NAME = "CRIST_MEDICALS_C.A ";

    /** Default cash discount (Contado) — 7% */
    private static final double DEFAULT_CONTADO_DISCOUNT = 7.0;

    @Override
    public List<SupplierProduct> parse(File file) throws Exception {
        List<SupplierProduct> products = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheet(SHEET_NAME);
            if (sheet == null) {
                // Try trimmed name or fallback to first sheet
                for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                    if (wb.getSheetName(i).trim().toUpperCase().contains("CRIST")) {
                        sheet = wb.getSheetAt(i);
                        break;
                    }
                }
                if (sheet == null) {
                    sheet = wb.getSheetAt(0);
                }
                System.out.println("[CristMedicalsParser] Using sheet: " + sheet.getSheetName());
            }

            // Read the "Contado" discount from metadata rows (row 1, col 14)
            double contadoDiscount = readContadoDiscount(sheet);
            System.out.println("[CristMedicalsParser] Contado discount: " + contadoDiscount + "%");

            // Detect header row by scanning for "CODIGO DE BARRA" or "PRECIOS"
            int headerRow = -1;
            int colBarcode = -1, colProducto = -1, colPrecio = -1, colStock = -1;
            int colLaboratorio = -1;

            for (int r = 0; r <= Math.min(15, sheet.getLastRowNum()); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                for (int c = 0; c < row.getLastCellNum(); c++) {
                    String val = getCellString(row.getCell(c)).toLowerCase().trim();
                    if (val.isEmpty()) continue;

                    if (val.contains("codigo") && val.contains("barra"))
                        colBarcode = c;
                    else if (val.contains("lista ordenada") || val.contains("principio activo"))
                        colProducto = c;
                    else if (val.equals("precios"))
                        colPrecio = c;
                    else if (val.equals("stock"))
                        colStock = c;
                    else if (val.contains("laboratorio"))
                        colLaboratorio = c;
                }

                if (colBarcode >= 0 && colPrecio >= 0) {
                    headerRow = r;
                    break;
                }
            }

            if (headerRow == -1) {
                throw new Exception(
                        "No se detectó la fila de encabezados de CristMedicals. "
                        + "Se esperan columnas: CODIGO DE BARRA, PRECIOS");
            }

            System.out.println("[CristMedicalsParser] Header at row " + headerRow
                    + ", barcode=" + colBarcode + ", producto=" + colProducto
                    + ", precio=" + colPrecio + ", stock=" + colStock
                    + ", laboratorio=" + colLaboratorio);

            int skipped = 0;

            for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                try {
                    String barcode = DataSanitizer.cleanBarcode(getCellString(row.getCell(colBarcode)));
                    double listPrice = DataSanitizer.parseDecimal(getCellString(row.getCell(colPrecio)));

                    if (barcode.isEmpty() || listPrice <= 0) {
                        skipped++;
                        continue;
                    }

                    String desc = colProducto >= 0
                            ? DataSanitizer.cleanDescription(getCellString(row.getCell(colProducto)))
                            : "";

                    int stock = colStock >= 0
                            ? DataSanitizer.parseStock(getCellString(row.getCell(colStock)))
                            : 0;

                    String lab = colLaboratorio >= 0
                            ? getCellString(row.getCell(colLaboratorio)).trim()
                            : "";

                    // Build product with Contado (cash) discount applied
                    SupplierProduct sp = new SupplierProduct();
                    sp.setBarcode(barcode);
                    sp.setDescription(desc);
                    sp.setBasePrice(listPrice);
                    sp.setSupplier(Supplier.CRISTMEDICALS);
                    sp.setDiscounts(contadoDiscount); // 7% Contado discount
                    sp.setStock(stock);

                    if (!lab.isEmpty()) {
                        sp.setBrand(lab);
                    }

                    products.add(sp);
                } catch (Exception e) {
                    skipped++;
                }
            }

            System.out.println("[CristMedicalsParser] Parsed " + products.size()
                    + " products (skipped " + skipped + ")");
        }

        return products;
    }

    /**
     * Read the "Contado" discount rate from the file metadata.
     * Expected at Row 1, Col 14 as a decimal (0.07 = 7%).
     * Falls back to the default 7% if not found.
     */
    private double readContadoDiscount(Sheet sheet) {
        try {
            Row row = sheet.getRow(1);
            if (row != null) {
                Cell cell = row.getCell(14);
                if (cell != null) {
                    double val = DataSanitizer.parseDecimal(getCellString(cell));
                    if (val > 0 && val <= 1.0) {
                        return val * 100.0; // 0.07 → 7%
                    } else if (val > 1.0 && val <= 100.0) {
                        return val; // Already percentage
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[CristMedicalsParser] Could not read Contado discount, using default");
        }
        return DEFAULT_CONTADO_DISCOUNT;
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
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
                try { yield String.valueOf(cell.getNumericCellValue()); }
                catch (Exception e) {
                    try { yield cell.getStringCellValue(); }
                    catch (Exception e2) { yield ""; }
                }
            }
            default -> "";
        };
    }
}
