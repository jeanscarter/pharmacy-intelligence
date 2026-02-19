package com.pharmacyintel.parser;

import com.pharmacyintel.model.Supplier;
import com.pharmacyintel.model.SupplierProduct;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for Dromarko CSV (semicolon-delimited).
 * Header: DESCRIPCION; MARCA; CODIGO; EXISTENCIA; IVA; PRECIO(USD); PRECIO;
 * DA(%); DA2(%); DC(%); PP(%); DI(%); DP(%); DV(%); DVNETO(USD); DVNETO;
 * NETO(USD); NETO; BARRA; TASA; FECHVENC.
 */
public class DromarkoParser implements SupplierParser {

    @Override
    public List<SupplierProduct> parse(File file) throws Exception {
        List<SupplierProduct> products = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {

            String headerLine = br.readLine();
            if (headerLine == null)
                return products;

            String[] headers = headerLine.split(";");
            int colDesc = findCol(headers, "DESCRIPCION");
            int colBarcode = findCol(headers, "BARRA");
            int colNetUsd = findCol(headers, "NETO(USD)");
            int colPriceUsd = findCol(headers, "PRECIO(USD)");
            int colStock = findCol(headers, "EXISTENCIA");
            int colIva = findCol(headers, "IVA");
            int colDiscount = findCol(headers, "DA(%)");

            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank())
                    continue;
                String[] cols = line.split(";", -1);

                try {
                    SupplierProduct sp = new SupplierProduct();
                    sp.setSupplier(Supplier.DROMARKO);
                    sp.setBarcode(DataSanitizer.cleanBarcode(safeGet(cols, colBarcode)));
                    sp.setDescription(DataSanitizer.cleanDescription(safeGet(cols, colDesc)));
                    sp.setNetUsd(DataSanitizer.parseDecimal(safeGet(cols, colNetUsd)));
                    sp.setPriceUsd(DataSanitizer.parseDecimal(safeGet(cols, colPriceUsd)));
                    sp.setStock(DataSanitizer.parseStock(safeGet(cols, colStock)));
                    sp.setIva(DataSanitizer.parseIva(safeGet(cols, colIva)));
                    sp.setDiscountPct(DataSanitizer.parseDecimal(safeGet(cols, colDiscount)));

                    if (!sp.getBarcode().isEmpty() && sp.getNetUsd() > 0) {
                        products.add(sp);
                    }
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
