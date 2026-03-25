package com.pharmacyintel.parser;

import com.pharmacyintel.model.Supplier;
import com.pharmacyintel.model.SupplierProduct;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for 365 supplier CSV files (semicolon-delimited).
 * Same column layout as Droactiva.
 * Extracts: Base = PRECIO(USD), Discounts = DA(%), DA2(%), DV(%).
 */
public class P365CsvParser implements SupplierParser {

    @Override
    public List<SupplierProduct> parse(File file) throws Exception {
        List<SupplierProduct> products = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {

            String headerLine = br.readLine();
            if (headerLine == null)
                return products;

            // Remove UTF-8 BOM if present
            headerLine = headerLine.replace("\uFEFF", "");

            String[] headers = headerLine.split(";");
            int colDesc = findCol(headers, "DESCRIPCION");
            int colBarcode = findCol(headers, "BARRA");
            int colPriceUsd = findCol(headers, "PRECIO(USD)");
            int colStock = findCol(headers, "EXISTENCIA");
            int colIva = findCol(headers, "IVA");
            int colDa = findCol(headers, "DA(%)");
            int colDa2 = findCol(headers, "DA2(%)");
            int colDv = findCol(headers, "DV(%)");

            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank())
                    continue;
                String[] cols = line.split(";", -1);

                try {
                    String barcode = DataSanitizer.cleanBarcode(safeGet(cols, colBarcode));
                    double basePrice = DataSanitizer.parseDecimal(safeGet(cols, colPriceUsd));
                    double da = DataSanitizer.parseDecimal(safeGet(cols, colDa));
                    double da2 = DataSanitizer.parseDecimal(safeGet(cols, colDa2));
                    double dv = DataSanitizer.parseDecimal(safeGet(cols, colDv));
                    String desc = DataSanitizer.cleanDescription(safeGet(cols, colDesc));
                    int stock = DataSanitizer.parseStock(safeGet(cols, colStock));
                    double iva = DataSanitizer.parseIva(safeGet(cols, colIva));

                    if (barcode.isEmpty() || basePrice <= 0)
                        continue;

                    SupplierProduct sp = new SupplierProduct(barcode, desc, basePrice, 0, stock,
                            Supplier.P365);
                    sp.setDiscounts(da, da2, dv);
                    sp.setIva(iva);
                    products.add(sp);
                } catch (Exception e) {
                    // Skip malformed rows
                }
            }
        }
        return products;
    }

    private int findCol(String[] headers, String keyword) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(keyword))
                return i;
        }
        return -1;
    }

    private String safeGet(String[] cols, int idx) {
        if (idx < 0 || idx >= cols.length)
            return "";
        return cols[idx];
    }
}
