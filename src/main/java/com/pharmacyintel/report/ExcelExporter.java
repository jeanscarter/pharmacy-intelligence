package com.pharmacyintel.report;

import com.pharmacyintel.model.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Strategic Excel Export — adapts columns, sorting and filtering based on the
 * active filter.
 */
public class ExcelExporter {

    private static final Supplier[] SUPPLIERS = Supplier.values();
    private static final int SUPPLIER_COUNT = SUPPLIERS.length;
    private static final Supplier BASE_SUPPLIER = Supplier.DROACTIVA;

    // Filter constants (must match ProductTablePanel)
    private static final String FILTER_MEJOR_PRECIO = "Mejor Precio DroActiva";
    private static final String FILTER_MEJOR_OFERTA = "Mejor Oferta DroActiva";
    private static final String FILTER_PEOR_NETO = "Peor Neto DroActiva";

    public File export(Map<String, MasterProduct> catalog, double bcvRate, File outputDir, String activeFilter)
            throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet sheet = wb.createSheet("Análisis de Precio");

        // --- Styles ---
        CellStyle headerStyle = createHeaderStyle(wb);
        CellStyle supplierHeaderStyle = createSupplierHeaderStyle(wb);
        CellStyle stockHeaderStyle = createStockHeaderStyle(wb);
        CellStyle winnerStyle = createWinnerStyle(wb);
        CellStyle loserStyle = createLoserStyle(wb);
        CellStyle priceStyle = createPriceStyle(wb);
        CellStyle pctStyle = createPercentStyle(wb);
        CellStyle textStyle = createTextStyle(wb);
        CellStyle stockCellStyle = createStockCellStyle(wb);
        CellStyle posWinStyle = createPositionWinStyle(wb);
        CellStyle posLoseStyle = createPositionLoseStyle(wb);
        CellStyle posNeutralStyle = createPositionNeutralStyle(wb);

        boolean isStrategic = FILTER_MEJOR_PRECIO.equals(activeFilter)
                || FILTER_MEJOR_OFERTA.equals(activeFilter)
                || FILTER_PEOR_NETO.equals(activeFilter);

        // --- Title row ---
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("ANÁLISIS COMPARATIVO DE PRECIOS — " + activeFilter.toUpperCase());
        CellStyle titleStyle = wb.createCellStyle();
        XSSFFont titleFont = wb.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleFont.setColor(IndexedColors.WHITE.getIndex());
        titleStyle.setFont(titleFont);
        titleStyle.setFillForegroundColor(new XSSFColor(new byte[] { 30, 33, 40 }, null));
        titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        titleCell.setCellStyle(titleStyle);

        // Info row
        Row infoRow = sheet.createRow(1);
        infoRow.createCell(0).setCellValue("Tasa BCV: " + String.format("%.4f", bcvRate));
        infoRow.createCell(3).setCellValue("Generado: " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        infoRow.createCell(6).setCellValue("Filtro: " + activeFilter);

        // --- Filter + Sort products ---
        List<MasterProduct> products = filterAndSort(catalog, activeFilter);

        // --- Write data based on mode ---
        int colCount;
        if (isStrategic) {
            colCount = writeStrategicReport(sheet, products, headerStyle, supplierHeaderStyle, stockHeaderStyle,
                    winnerStyle, loserStyle, priceStyle, pctStyle, textStyle, stockCellStyle,
                    posWinStyle, posLoseStyle, posNeutralStyle, activeFilter);
        } else {
            colCount = writeFullReport(sheet, products, headerStyle, supplierHeaderStyle, stockHeaderStyle,
                    winnerStyle, loserStyle, priceStyle, pctStyle, textStyle, stockCellStyle,
                    posWinStyle, posLoseStyle, posNeutralStyle);
        }

        // Merge title
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, Math.max(colCount - 1, 0)));

        // Auto-size columns
        for (int i = 0; i < colCount; i++) {
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

    // ====================================================================
    // FILTERING & SORTING
    // ====================================================================

    private List<MasterProduct> filterAndSort(Map<String, MasterProduct> catalog, String activeFilter) {
        List<MasterProduct> all = new ArrayList<>(catalog.values());

        List<MasterProduct> filtered;
        Comparator<MasterProduct> comparator;

        switch (activeFilter) {
            case FILTER_MEJOR_PRECIO -> {
                // DroActiva is winner — sort by higher discount first
                filtered = all.stream()
                        .filter(mp -> mp.getWinnerSupplier() == BASE_SUPPLIER)
                        .toList();
                comparator = Comparator.comparingDouble(
                        (MasterProduct mp) -> -mp.getOfferPctForSupplier(BASE_SUPPLIER));
            }
            case FILTER_MEJOR_OFERTA -> {
                // DroActiva has the best OF% among all suppliers
                filtered = all.stream()
                        .filter(mp -> {
                            double ofDro = mp.getOfferPctForSupplier(BASE_SUPPLIER);
                            if (ofDro <= 0)
                                return false;
                            for (Supplier s : SUPPLIERS) {
                                if (s == BASE_SUPPLIER)
                                    continue;
                                if (mp.getOfferPctForSupplier(s) > ofDro)
                                    return false;
                            }
                            return true;
                        })
                        .toList();
                comparator = Comparator.comparingDouble(
                        (MasterProduct mp) -> -mp.getOfferPctForSupplier(BASE_SUPPLIER));
            }
            case FILTER_PEOR_NETO -> {
                // DroActiva is NOT the winner and has a price
                filtered = all.stream()
                        .filter(mp -> mp.getWinnerSupplier() != null
                                && mp.getWinnerSupplier() != BASE_SUPPLIER
                                && mp.getNetPriceForSupplier(BASE_SUPPLIER) > 0)
                        .toList();
                // Sort by highest net of DroActiva first (worst first)
                comparator = Comparator.comparingDouble(
                        (MasterProduct mp) -> -mp.getNetPriceForSupplier(BASE_SUPPLIER));
            }
            default -> {
                // "Todos" — no filter, worst positioning first
                filtered = all;
                comparator = Comparator
                        .comparingDouble((MasterProduct mp) -> -mp.getDiffPctForSupplier(BASE_SUPPLIER))
                        .thenComparingInt(mp -> -mp.getPositionForSupplier(BASE_SUPPLIER))
                        .thenComparing(mp -> mp.getDescription() != null ? mp.getDescription() : "");
            }
        }

        return filtered.stream().sorted(comparator).toList();
    }

    // ====================================================================
    // FULL REPORT (all suppliers)
    // ====================================================================

    private int writeFullReport(XSSFSheet sheet, List<MasterProduct> products,
            CellStyle headerStyle, CellStyle supplierHeaderStyle, CellStyle stockHeaderStyle,
            CellStyle winnerStyle, CellStyle loserStyle, CellStyle priceStyle,
            CellStyle pctStyle, CellStyle textStyle, CellStyle stockCellStyle,
            CellStyle posWinStyle, CellStyle posLoseStyle, CellStyle posNeutralStyle) {

        int colCount = 2 + (SUPPLIER_COUNT * 4) + 7;

        // Header
        Row header = sheet.createRow(3);
        int col = 0;
        setCellStyled(header, col++, "Código de Barras", headerStyle);
        setCellStyled(header, col++, "Descripción", headerStyle);
        for (Supplier s : SUPPLIERS) {
            setCellStyled(header, col++, "PV " + s.getDisplayName(), supplierHeaderStyle);
            setCellStyled(header, col++, "OF% " + s.getDisplayName(), supplierHeaderStyle);
            setCellStyled(header, col++, "Neto " + s.getDisplayName(), supplierHeaderStyle);
            setCellStyled(header, col++, "Stock " + s.getDisplayName(), stockHeaderStyle);
        }
        setCellStyled(header, col++, "Posición " + BASE_SUPPLIER.getDisplayName(), headerStyle);
        setCellStyled(header, col++, "# Prov", headerStyle);
        setCellStyled(header, col++, "Droguería Ganadora", headerStyle);
        setCellStyled(header, col++, "DIF %", headerStyle);
        setCellStyled(header, col++, "DIF USD", headerStyle);
        setCellStyled(header, col++, "P. Venta Simulado", headerStyle);
        setCellStyled(header, col++, "Margen USD", headerStyle);

        // Data
        int rowIdx = 4;
        for (MasterProduct mp : products) {
            Row row = sheet.createRow(rowIdx++);
            col = 0;

            setCellText(row, col++, mp.getBarcode(), textStyle);
            setCellText(row, col++, mp.getDescription() != null ? mp.getDescription() : "", textStyle);

            for (Supplier s : SUPPLIERS) {
                col = writeSupplierBlock(row, col, mp, s, priceStyle, pctStyle, stockCellStyle, winnerStyle,
                        loserStyle);
            }

            // Analytics
            int basePos = mp.getPositionForSupplier(BASE_SUPPLIER);
            int supplierCount = mp.getSupplierCount();

            Cell posCell = row.createCell(col++);
            if (basePos > 0) {
                posCell.setCellValue(basePos + " de " + supplierCount);
                if (basePos == 1)
                    posCell.setCellStyle(posWinStyle);
                else if (basePos >= supplierCount && supplierCount > 1)
                    posCell.setCellStyle(posLoseStyle);
                else
                    posCell.setCellStyle(posNeutralStyle);
            }

            row.createCell(col++).setCellValue(supplierCount);

            Cell winnerCell = row.createCell(col++);
            if (mp.getWinnerSupplier() != null)
                winnerCell.setCellValue(mp.getWinnerSupplier().getDisplayName());

            Cell diffPctCell = row.createCell(col++);
            if (mp.getDiffPct() > 0) {
                diffPctCell.setCellValue(mp.getDiffPct() / 100.0);
                diffPctCell.setCellStyle(pctStyle);
            }

            Cell diffAmtCell = row.createCell(col++);
            if (mp.getDiffAmount() > 0) {
                diffAmtCell.setCellValue(mp.getDiffAmount());
                diffAmtCell.setCellStyle(priceStyle);
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

        return colCount;
    }

    // ====================================================================
    // STRATEGIC REPORT (Mejor Precio / Mejor Oferta / Peor Neto)
    // ====================================================================

    private int writeStrategicReport(XSSFSheet sheet, List<MasterProduct> products,
            CellStyle headerStyle, CellStyle supplierHeaderStyle, CellStyle stockHeaderStyle,
            CellStyle winnerStyle, CellStyle loserStyle, CellStyle priceStyle,
            CellStyle pctStyle, CellStyle textStyle, CellStyle stockCellStyle,
            CellStyle posWinStyle, CellStyle posLoseStyle, CellStyle posNeutralStyle,
            String activeFilter) {

        // Determine mode
        boolean isPrecio = FILTER_MEJOR_PRECIO.equals(activeFilter);
        boolean isOferta = FILTER_MEJOR_OFERTA.equals(activeFilter);
        // isPeorNeto is the remaining case

        // Columns: Código | Descripción | [Metric per supplier] | Diferencial $ | %
        // Diferencial |
        // [Inv per supplier] | [Posición per supplier]
        int colCount = 2 + SUPPLIER_COUNT + 2 + SUPPLIER_COUNT + SUPPLIER_COUNT;

        Row header = sheet.createRow(3);
        int col = 0;
        setCellStyled(header, col++, "Código de Barras", headerStyle);
        setCellStyled(header, col++, "Descripción", headerStyle);

        // Metric columns per supplier
        String metricPrefix;
        if (isPrecio) {
            metricPrefix = "PV";
        } else if (isOferta) {
            metricPrefix = "OF%";
        } else {
            metricPrefix = "Neto";
        }
        for (Supplier s : SUPPLIERS) {
            setCellStyled(header, col++, metricPrefix + " " + s.getDisplayName(), supplierHeaderStyle);
        }

        // Differentials
        setCellStyled(header, col++, "Diferencial $", headerStyle);
        setCellStyled(header, col++, "% Diferencial", headerStyle);

        // Inventory per supplier
        for (Supplier s : SUPPLIERS) {
            setCellStyled(header, col++, "Inv. " + s.getDisplayName(), stockHeaderStyle);
        }

        // Position per supplier
        for (Supplier s : SUPPLIERS) {
            setCellStyled(header, col++, "Pos. " + s.getDisplayName(), headerStyle);
        }

        // --- Data rows ---
        int rowIdx = 4;
        for (MasterProduct mp : products) {
            Row row = sheet.createRow(rowIdx++);
            col = 0;

            setCellText(row, col++, mp.getBarcode(), textStyle);
            setCellText(row, col++, mp.getDescription() != null ? mp.getDescription() : "", textStyle);

            // Metric values per supplier
            double droValue = 0;
            double bestOtherValue = isPrecio || !isOferta ? Double.MAX_VALUE : 0;

            for (Supplier s : SUPPLIERS) {
                double val;
                if (isPrecio) {
                    val = mp.getBasePriceForSupplier(s);
                } else if (isOferta) {
                    val = mp.getOfferPctForSupplier(s);
                } else {
                    val = mp.getNetPriceForSupplier(s);
                }

                Cell cell = row.createCell(col++);
                if (val > 0) {
                    if (isOferta) {
                        cell.setCellValue(val / 100.0);
                        cell.setCellStyle(pctStyle);
                    } else {
                        cell.setCellValue(val);
                        cell.setCellStyle(priceStyle);
                    }

                    // Highlight winner/loser for the metric
                    if (s == mp.getWinnerSupplier()) {
                        cell.setCellStyle(isOferta ? pctStyle : winnerStyle);
                    } else if (s == mp.getLoserSupplier()) {
                        cell.setCellStyle(isOferta ? pctStyle : loserStyle);
                    }
                }

                if (s == BASE_SUPPLIER) {
                    droValue = val;
                } else if (val > 0) {
                    if (isOferta) {
                        // For offers, "next best" is the next highest OF%
                        // We want to find second-highest offer to compute differential
                    } else {
                        // For PV/Neto, "next best" = lowest value among others
                        if (val < bestOtherValue)
                            bestOtherValue = val;
                    }
                }
            }

            // Compute differentials: DroActiva vs next best
            double diffAmt = 0;
            double diffPctVal = 0;
            if (isPrecio) {
                // Diff = PV DroActiva - PV mejor competidor
                if (droValue > 0 && bestOtherValue < Double.MAX_VALUE && bestOtherValue > 0) {
                    diffAmt = droValue - bestOtherValue;
                    diffPctVal = droValue > 0 ? (diffAmt / droValue) : 0;
                }
            } else if (isOferta) {
                // Diff = OF% DroActiva - OF% del segundo mejor
                // Find second-highest offer
                double secondBest = 0;
                for (Supplier s : SUPPLIERS) {
                    if (s == BASE_SUPPLIER)
                        continue;
                    double of = mp.getOfferPctForSupplier(s);
                    if (of > secondBest)
                        secondBest = of;
                }
                if (droValue > 0 && secondBest > 0) {
                    diffAmt = droValue - secondBest;
                    diffPctVal = droValue > 0 ? (diffAmt / droValue) / 100.0 : 0;
                }
            } else {
                // Peor Neto: Diff = Neto DroActiva - Neto Ganador
                double netWinner = mp.getWinnerSupplier() != null
                        ? mp.getNetPriceForSupplier(mp.getWinnerSupplier())
                        : 0;
                if (droValue > 0 && netWinner > 0) {
                    diffAmt = droValue - netWinner;
                    diffPctVal = droValue > 0 ? (diffAmt / droValue) : 0;
                }
            }

            Cell diffAmtCell = row.createCell(col++);
            if (diffAmt != 0) {
                if (isOferta) {
                    diffAmtCell.setCellValue(diffAmt / 100.0);
                    diffAmtCell.setCellStyle(pctStyle);
                } else {
                    diffAmtCell.setCellValue(diffAmt);
                    diffAmtCell.setCellStyle(priceStyle);
                }
            }

            Cell diffPctCell = row.createCell(col++);
            if (diffPctVal != 0) {
                diffPctCell.setCellValue(isOferta ? diffPctVal : diffPctVal);
                diffPctCell.setCellStyle(pctStyle);
            }

            // Inventory per supplier
            for (Supplier s : SUPPLIERS) {
                int inv = mp.getStockForSupplier(s);
                Cell invCell = row.createCell(col++);
                if (inv > 0) {
                    invCell.setCellValue(inv);
                    invCell.setCellStyle(stockCellStyle);
                }
            }

            // Position per supplier
            for (Supplier s : SUPPLIERS) {
                int pos = mp.getPositionForSupplier(s);
                int supplierCount = mp.getSupplierCount();
                Cell posCell = row.createCell(col++);
                if (pos > 0) {
                    posCell.setCellValue(pos + "/" + supplierCount);
                    if (pos == 1)
                        posCell.setCellStyle(posWinStyle);
                    else if (pos >= supplierCount && supplierCount > 1)
                        posCell.setCellStyle(posLoseStyle);
                    else
                        posCell.setCellStyle(posNeutralStyle);
                }
            }
        }

        return colCount;
    }

    // ====================================================================
    // Helper methods
    // ====================================================================

    /** Write PV, OF%, Neto, Stock for a single supplier */
    private int writeSupplierBlock(Row row, int col, MasterProduct mp, Supplier s,
            CellStyle priceStyle, CellStyle pctStyle, CellStyle stockCellStyle,
            CellStyle winnerStyle, CellStyle loserStyle) {
        double basePrice = mp.getBasePriceForSupplier(s);
        double offerPct = mp.getOfferPctForSupplier(s);
        double netPrice = mp.getNetPriceForSupplier(s);
        int stock = mp.getStockForSupplier(s);

        Cell pvCell = row.createCell(col++);
        if (basePrice > 0) {
            pvCell.setCellValue(basePrice);
            pvCell.setCellStyle(priceStyle);
        }

        Cell ofCell = row.createCell(col++);
        if (offerPct > 0) {
            ofCell.setCellValue(offerPct / 100.0);
            ofCell.setCellStyle(pctStyle);
        }

        Cell netCell = row.createCell(col++);
        if (netPrice > 0) {
            netCell.setCellValue(netPrice);
            if (s == mp.getWinnerSupplier())
                netCell.setCellStyle(winnerStyle);
            else if (s == mp.getLoserSupplier())
                netCell.setCellStyle(loserStyle);
            else
                netCell.setCellStyle(priceStyle);
        }

        Cell stockCell = row.createCell(col++);
        if (stock > 0) {
            stockCell.setCellValue(stock);
            stockCell.setCellStyle(stockCellStyle);
        }

        return col;
    }

    private void setCellStyled(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private void setCellText(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    // ====================================================================
    // Styles
    // ====================================================================

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

    private CellStyle createStockHeaderStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(new byte[] { 120, 60, 20 }, null));
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

    private CellStyle createLoserStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(new byte[] { (byte) 198, 40, 40 }, null));
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

    private CellStyle createStockCellStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(new byte[] { (byte) 255, (byte) 235, (byte) 205 }, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createPositionWinStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(new byte[] { (byte) 198, (byte) 239, (byte) 206 }, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createPositionLoseStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(new byte[] { (byte) 255, (byte) 199, (byte) 206 }, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createPositionNeutralStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setFillForegroundColor(new XSSFColor(new byte[] { (byte) 255, (byte) 252, (byte) 225 }, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
}
