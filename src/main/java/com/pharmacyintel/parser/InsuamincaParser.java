package com.pharmacyintel.parser;

import com.pharmacyintel.model.GlobalConfig;
import com.pharmacyintel.model.Supplier;
import com.pharmacyintel.model.SupplierProduct;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for Insuaminca "LISTADO 10%" Excel files.
 * 
 * <p>File structure (Sheet: "Precios", header at row 9):
 * <pre>
 *   Col 0: Codigo (internal code)
 *   Col 1: Cod/Barras (barcode)
 *   Col 2: Descripcion (product name)
 *   Col 3: Precio (list price USD)
 *   Col 4: Oferta (discount 1, e.g. 0.1 = 10%)
 *   Col 5: Oferta 2 (discount 2)
 *   Col 6: Oferta 3 (discount 3)
 *   Col 7: Precio F (final price, already includes discounts — used as net price reference)
 *   Col 8: Maturin (stock)
 *   Col 9: Guarenas (stock)
 *   Col 10: Barquisimeto (stock)
 *   Col 13: Fecha/Lote (expiration date)
 *   Col 14: Marca (brand)
 *   Col 16: Categoria (category)
 * </pre>
 * 
 * <p><b>Key design decision:</b> We use {@code Precio F} (col 7) as the pre-computed net price
 * since Insuaminca already calculates the cascading discounts. The {@code basePrice} is
 * {@code Precio} (col 3), and the individual discount percentages are extracted from
 * cols 4-6 for display in the UI, but the actual net price is directly from col 7.
 */
public class InsuamincaParser implements SupplierParser {

    /** Target sheet name in the Insuaminca workbook */
    private static final String SHEET_NAME = "Precios";

    @Override
    public List<SupplierProduct> parse(File file) throws Exception {
        List<SupplierProduct> products = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheet(SHEET_NAME);
            if (sheet == null) {
                // Fallback: try first sheet
                sheet = wb.getSheetAt(0);
                System.out.println("[InsuamincaParser] Sheet '" + SHEET_NAME
                        + "' not found, using: " + sheet.getSheetName());
            }

            // Detect header row by scanning for "Cod/Barras" or "Descripcion"
            int headerRow = -1;
            int colCodigo = -1, colBarcode = -1, colDesc = -1, colPrecio = -1;
            int colOferta1 = -1, colOferta2 = -1, colOferta3 = -1, colPrecioF = -1;
            int colMaturin = -1, colGuarenas = -1, colBarquisimeto = -1;
            int colFechaLote = -1, colMarca = -1;

            for (int r = 0; r <= Math.min(20, sheet.getLastRowNum()); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                for (int c = 0; c < row.getLastCellNum(); c++) {
                    String val = getCellString(row.getCell(c)).toLowerCase().trim();
                    if (val.isEmpty()) continue;

                    if (val.equals("codigo") || val.equals("código"))
                        colCodigo = c;
                    else if (val.contains("cod") && val.contains("barra"))
                        colBarcode = c;
                    else if (val.contains("descripcion") || val.contains("descripción"))
                        colDesc = c;
                    else if (val.equals("precio"))
                        colPrecio = c;
                    else if (val.equals("oferta") && colOferta1 == -1)
                        colOferta1 = c;
                    else if (val.equals("oferta 2"))
                        colOferta2 = c;
                    else if (val.equals("oferta 3"))
                        colOferta3 = c;
                    else if (val.equals("precio f") || val.equals("preciof"))
                        colPrecioF = c;
                    else if (val.contains("maturin") || val.contains("maturín"))
                        colMaturin = c;
                    else if (val.contains("guarenas"))
                        colGuarenas = c;
                    else if (val.contains("barquisimeto"))
                        colBarquisimeto = c;
                    else if (val.contains("fecha") || val.contains("lote"))
                        colFechaLote = c;
                    else if (val.equals("marca"))
                        colMarca = c;
                }

                if (colBarcode >= 0 && colDesc >= 0 && colPrecio >= 0) {
                    headerRow = r;
                    break;
                }
            }

            if (headerRow == -1) {
                throw new Exception(
                        "No se detectó la fila de encabezados de Insuaminca. "
                        + "Se esperan columnas: Cod/Barras, Descripcion, Precio");
            }

            System.out.println("[InsuamincaParser] Header at row " + headerRow
                    + ", barcode=" + colBarcode + ", precio=" + colPrecio
                    + ", precioF=" + colPrecioF + ", desc=" + colDesc
                    + ", maturin=" + colMaturin + ", guarenas=" + colGuarenas
                    + ", barquisimeto=" + colBarquisimeto);

            int skipped = 0;

            for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                try {
                    String barcode = DataSanitizer.cleanBarcode(getCellString(row.getCell(colBarcode)));
                    double basePrice = DataSanitizer.parseDecimal(getCellString(row.getCell(colPrecio)));

                    if (barcode.isEmpty() || basePrice <= 0) {
                        skipped++;
                        continue;
                    }

                    String desc = colDesc >= 0
                            ? DataSanitizer.cleanDescription(getCellString(row.getCell(colDesc)))
                            : "";

                    // Parse cascading discounts (Insuaminca stores them as 0.1 = 10%)
                    // The first "Oferta" column is treated as the Commercial Discount.
                    double rawD1 = colOferta1 >= 0
                            ? normalizeDiscount(DataSanitizer.parseDecimal(getCellString(row.getCell(colOferta1))))
                            : 0;
                    double d1 = GlobalConfig.getInstance().isApplyCommercialDiscount() ? rawD1 : 0;
                    
                    double d2 = colOferta2 >= 0
                            ? normalizeDiscount(DataSanitizer.parseDecimal(getCellString(row.getCell(colOferta2))))
                            : 0;
                    double d3 = colOferta3 >= 0
                            ? normalizeDiscount(DataSanitizer.parseDecimal(getCellString(row.getCell(colOferta3))))
                            : 0;

                    // Stock: sum of Maturin + Guarenas + Barquisimeto
                    int stockMaturin = colMaturin >= 0
                            ? DataSanitizer.parseStock(getCellString(row.getCell(colMaturin)))
                            : 0;
                    int stockGuarenas = colGuarenas >= 0
                            ? DataSanitizer.parseStock(getCellString(row.getCell(colGuarenas)))
                            : 0;
                    int stockBarquisimeto = colBarquisimeto >= 0
                            ? DataSanitizer.parseStock(getCellString(row.getCell(colBarquisimeto)))
                            : 0;
                    int totalStock = stockMaturin + stockGuarenas + stockBarquisimeto;

                    // Build product
                    SupplierProduct sp = new SupplierProduct();
                    sp.setBarcode(barcode);
                    sp.setDescription(desc);
                    sp.setBasePrice(basePrice);
                    sp.setSupplier(Supplier.INSUAMINCA);

                    // Always use discounts so hasDiscount()/getOfferPct() work correctly.
                    // The cascading calculation (base × (1-d1) × (1-d2) × (1-d3))
                    // should match "Precio F" from the spreadsheet.
                    sp.setDiscounts(d1, d2, d3);

                    sp.setStock(totalStock);

                    // Optional metadata
                    if (colCodigo >= 0) {
                        sp.setInternalCode(getCellString(row.getCell(colCodigo)).trim());
                    }
                    if (colMarca >= 0) {
                        sp.setBrand(getCellString(row.getCell(colMarca)).trim());
                    }
                    if (colFechaLote >= 0) {
                        sp.setExpirationDate(getCellString(row.getCell(colFechaLote)).trim());
                    }

                    products.add(sp);
                } catch (Exception e) {
                    skipped++;
                }
            }

            System.out.println("[InsuamincaParser] Parsed " + products.size()
                    + " products (skipped " + skipped + ")");
        }

        return products;
    }

    /**
     * Normalize discount: Insuaminca stores discounts as decimal (0.1 = 10%).
     * If value is <= 1, multiply by 100 to get percentage.
     */
    private double normalizeDiscount(double raw) {
        if (raw > 0 && raw <= 1.0) {
            return raw * 100.0; // 0.1 → 10%
        }
        return raw; // Already in percentage form
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
