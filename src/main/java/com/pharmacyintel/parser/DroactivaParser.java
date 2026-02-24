package com.pharmacyintel.parser;

import com.pharmacyintel.model.Supplier;
import com.pharmacyintel.model.SupplierProduct;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for Droactiva CSV (semicolon-delimited).
 * Extracts: Base = PRECIO(USD), Offer = DA(%).
 * NetPrice is computed: basePrice * (1 - offerPct / 100).
 */
public class DroactivaParser implements SupplierParser {

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
            int colPriceUsd = findCol(headers, "PRECIO(USD)");
            int colStock = findCol(headers, "EXISTENCIA");
            int colIva = findCol(headers, "IVA");
            int colOffer = findCol(headers, "DA(%)");

            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank())
                    continue;
                String[] cols = line.split(";", -1);

                try {
                    String barcode = DataSanitizer.cleanBarcode(safeGet(cols, colBarcode));
                    double basePrice = DataSanitizer.parseDecimal(safeGet(cols, colPriceUsd));
                    double offerPct = DataSanitizer.parseDecimal(safeGet(cols, colOffer));
                    String desc = DataSanitizer.cleanDescription(safeGet(cols, colDesc));
                    int stock = DataSanitizer.parseStock(safeGet(cols, colStock));
                    double iva = DataSanitizer.parseIva(safeGet(cols, colIva));

                    if (barcode.isEmpty() || basePrice <= 0)
                        continue;

                    SupplierProduct sp = new SupplierProduct(barcode, desc, basePrice, offerPct, stock,
                            Supplier.DROACTIVA);
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
