package com.pharmacyintel.model;

import java.util.EnumMap;
import java.util.Map;

public class MasterProduct {
    private String barcode;
    private String description;
    private final Map<Supplier, SupplierProduct> supplierPrices = new EnumMap<>(Supplier.class);

    // Computed fields
    private double bestPrice = Double.MAX_VALUE;
    private Supplier winnerSupplier;
    private double diffPct;
    private double simulatedSalePrice;
    private double simulatedMargin;

    public MasterProduct(String barcode, String description) {
        this.barcode = barcode;
        this.description = description;
    }

    public void addSupplierProduct(SupplierProduct sp) {
        supplierPrices.put(sp.getSupplier(), sp);
        // DroActiva description always takes priority
        if (sp.getSupplier() == Supplier.DROACTIVA && sp.getDescription() != null && !sp.getDescription().isBlank()) {
            description = sp.getDescription();
        } else if ((description == null || description.isBlank()) && sp.getDescription() != null) {
            description = sp.getDescription();
        }
    }

    /** Recalculate best price considering only suppliers with stock > 0 */
    public void computeCompetitiveness() {
        bestPrice = Double.MAX_VALUE;
        winnerSupplier = null;
        double secondBest = Double.MAX_VALUE;

        for (var entry : supplierPrices.entrySet()) {
            SupplierProduct sp = entry.getValue();
            if (!sp.hasStock())
                continue;
            if (sp.getNetUsd() <= 0)
                continue;

            if (sp.getNetUsd() < bestPrice) {
                secondBest = bestPrice;
                bestPrice = sp.getNetUsd();
                winnerSupplier = entry.getKey();
            } else if (sp.getNetUsd() < secondBest) {
                secondBest = sp.getNetUsd();
            }
        }

        // DIF% = difference between best and second best (or avg if no second)
        if (secondBest < Double.MAX_VALUE && bestPrice < Double.MAX_VALUE && bestPrice > 0) {
            diffPct = ((secondBest - bestPrice) / bestPrice) * 100.0;
        } else {
            diffPct = 0;
        }
    }

    /** Simulate margin: sale price = bestPrice * (1 + marginPct/100) */
    public void simulateMargin(double marginPct) {
        if (bestPrice < Double.MAX_VALUE && bestPrice > 0) {
            simulatedSalePrice = bestPrice * (1.0 + marginPct / 100.0);
            simulatedMargin = simulatedSalePrice - bestPrice;
        }
    }

    public double getPriceForSupplier(Supplier s) {
        SupplierProduct sp = supplierPrices.get(s);
        return sp != null ? sp.getNetUsd() : 0;
    }

    public int getStockForSupplier(Supplier s) {
        SupplierProduct sp = supplierPrices.get(s);
        return sp != null ? sp.getStock() : 0;
    }

    public int getSupplierCount() {
        return (int) supplierPrices.values().stream().filter(sp -> sp.getNetUsd() > 0).count();
    }

    // --- Getters ---
    public String getBarcode() {
        return barcode;
    }

    public String getDescription() {
        return description;
    }

    public Map<Supplier, SupplierProduct> getSupplierPrices() {
        return supplierPrices;
    }

    public double getBestPrice() {
        return bestPrice == Double.MAX_VALUE ? 0 : bestPrice;
    }

    public Supplier getWinnerSupplier() {
        return winnerSupplier;
    }

    public double getDiffPct() {
        return diffPct;
    }

    public double getSimulatedSalePrice() {
        return simulatedSalePrice;
    }

    public double getSimulatedMargin() {
        return simulatedMargin;
    }
}
