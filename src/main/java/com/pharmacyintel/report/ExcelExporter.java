package com.pharmacyintel.report;

import com.pharmacyintel.model.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Generates styled .xlsx export replicating the "Análisis de Precio" format.
 */
public class ExcelExporter {

    private static final Supplier[] SUPPLIERS = Supplier.values();

    public File export(Map<String, MasterProduct> catalog, double bcvRate, File outputDir) throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet sheet = wb.createSheet("Análisis de Precio");

        // --- Styles ---
        CellStyle headerStyle = createHeaderStyle(wb);
        CellStyle supplierHeaderStyle = createSupplierHeaderStyle(wb);
        CellStyle winnerStyle = createWinnerStyle(wb);
        CellStyle priceStyle = createPriceStyle(wb);
        CellStyle pctStyle = createPercentStyle(wb);
        CellStyle textStyle = createTextStyle(wb);

        // --- Title row ---
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("ANÁLISIS COMPARATIVO DE PRECIOS — DROGUERÍAS");
        CellStyle titleStyle = wb.createCellStyle();
        XSSFFont titleFont = wb.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleFont.setColor(IndexedColors.WHITE.getIndex());
        titleStyle.setFont(titleFont);
        titleStyle.setFillForegroundColor(new XSSFColor(new byte[] { 30, 33, 40 }, null));
        titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4 + SUPPLIERS.length * 2));

        // Info row
        Row infoRow = sheet.createRow(1);
        infoRow.createCell(0).setCellValue("Tasa BCV: " + String.format("%.4f", bcvRate));
        infoRow.createCell(3).setCellValue("Generado: " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

        // --- Header row ---
        Row header = sheet.createRow(3);
        int col = 0;
        String[] baseHeaders = { "Código de Barras", "Descripción", "# Proveedores" };
        for (String h : baseHeaders) {
            Cell c = header.createCell(col++);
            c.setCellValue(h);
            c.setCellStyle(headerStyle);
        }

        // Supplier price + stock columns
        for (Supplier s : SUPPLIERS) {
            Cell priceHeader = header.createCell(col++);
            priceHeader.setCellValue(s.getDisplayName() + " USD");
            priceHeader.setCellStyle(supplierHeaderStyle);

            Cell stockHeader = header.createCell(col++);
            stockHeader.setCellValue(s.getDisplayName() + " Stock");
            stockHeader.setCellStyle(supplierHeaderStyle);
        }

        // Analytics columns
        String[] analyticsHeaders = { "Mejor Precio", "Droguería Ganadora", "DIF %", "P. Venta Simulado",
                "Margen USD" };
        for (String h : analyticsHeaders) {
            Cell c = header.createCell(col++);
            c.setCellValue(h);
            c.setCellStyle(headerStyle);
        }

        // --- Data rows ---
        List<MasterProduct> products = catalog.values().stream()
                .sorted((a, b) -> {
                    // Sort by winner then description
                    if (a.getWinnerSupplier() != null && b.getWinnerSupplier() != null) {
                        int cmp = a.getWinnerSupplier().compareTo(b.getWinnerSupplier());
                        if (cmp != 0)
                            return cmp;
                    }
                    return (a.getDescription() != null ? a.getDescription() : "")
                            .compareToIgnoreCase(b.getDescription() != null ? b.getDescription() : "");
                }).toList();

        int rowIdx = 4;
        for (MasterProduct mp : products) {
            Row row = sheet.createRow(rowIdx++);
            col = 0;

            Cell barcodeCell = row.createCell(col++);
            barcodeCell.setCellValue(mp.getBarcode());
            barcodeCell.setCellStyle(textStyle);

            Cell descCell = row.createCell(col++);
            descCell.setCellValue(mp.getDescription() != null ? mp.getDescription() : "");
            descCell.setCellStyle(textStyle);

            Cell countCell = row.createCell(col++);
            countCell.setCellValue(mp.getSupplierCount());

            // Supplier prices
            for (Supplier s : SUPPLIERS) {
                double price = mp.getPriceForSupplier(s);
                int stock = mp.getStockForSupplier(s);

                Cell pc = row.createCell(col++);
                if (price > 0) {
                    pc.setCellValue(price);
                    // Highlight winner
                    pc.setCellStyle(s == mp.getWinnerSupplier() ? winnerStyle : priceStyle);
                }

                Cell sc = row.createCell(col++);
                if (stock > 0)
                    sc.setCellValue(stock);
            }

            // Analytics
            Cell bestPriceCell = row.createCell(col++);
            if (mp.getBestPrice() > 0) {
                bestPriceCell.setCellValue(mp.getBestPrice());
                bestPriceCell.setCellStyle(winnerStyle);
            }

            Cell winnerCell = row.createCell(col++);
            if (mp.getWinnerSupplier() != null) {
                winnerCell.setCellValue(mp.getWinnerSupplier().getDisplayName());
            }

            Cell diffCell = row.createCell(col++);
            if (mp.getDiffPct() > 0) {
                diffCell.setCellValue(mp.getDiffPct() / 100.0);
                diffCell.setCellStyle(pctStyle);
            }

            Cell salePriceCell = row.createCell(col++);
            if (mp.getSimulatedSalePrice() > 0) {
                salePriceCell.setCellValue(mp.getSimulatedSalePrice());
                salePriceCell.setCellStyle(priceStyle);
            }

            Cell marginCell = row.createCell(col++);
            if (mp.getSimulatedMargin() > 0) {
                marginCell.setCellValue(mp.getSimulatedMargin());
                marginCell.setCellStyle(priceStyle);
            }
        }

        // Auto-size columns
        for (int i = 0; i < col; i++) {
            sheet.autoSizeColumn(i);
        }
        sheet.createFreezePane(2, 4);

        // Write file
        String filename = "Analisis_Precio_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".xlsx";
        File outputFile = new File(outputDir, filename);
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            wb.write(fos);
        }
        wb.close();
        return outputFile;
    }

    private CellStyle createHeaderStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(new byte[] { 33, 37, 41 }, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createSupplierHeaderStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(new byte[] { 52, 73, 94 }, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createWinnerStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(new byte[] { 39, (byte) 174, 96 }, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        return style;
    }

    private CellStyle createPriceStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        return style;
    }

    private CellStyle createPercentStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setDataFormat(wb.createDataFormat().getFormat("0.00%"));
        return style;
    }

    private CellStyle createTextStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        return style;
    }
}
