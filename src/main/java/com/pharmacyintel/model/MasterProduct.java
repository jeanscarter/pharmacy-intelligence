package com.pharmacyintel.model;

import java.util.*;

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
    private final Map<Supplier, Integer> supplierPositions = new EnumMap<>(Supplier.class);

    public MasterProduct(String barcode, String description) {
        this.barcode = barcode;
        this.description = description;
    }

    public void addSupplierProduct(SupplierProduct sp) {
        supplierPrices.put(sp.getSupplier(), sp);

        // Description priority: F24 or Cobeca (they include lab brand), else longest
        // string
        if (sp.getSupplier() == Supplier.F24 || sp.getSupplier() == Supplier.COBECA) {
            if (sp.getDescription() != null && !sp.getDescription().isBlank()) {
                description = sp.getDescription();
            }
        } else if (description == null || description.isBlank()) {
            if (sp.getDescription() != null && !sp.getDescription().isBlank()) {
                description = sp.getDescription();
            }
        } else if (sp.getDescription() != null && sp.getDescription().length() > description.length()) {
            // Only override with longer if current desc didn't come from F24/Cobeca
            boolean currentFromPriority = false;
            for (var entry : supplierPrices.entrySet()) {
                if ((entry.getKey() == Supplier.F24 || entry.getKey() == Supplier.COBECA)
                        && entry.getValue().getDescription() != null
                        && entry.getValue().getDescription().equals(description)) {
                    currentFromPriority = true;
                    break;
                }
            }
            if (!currentFromPriority) {
                description = sp.getDescription();
            }
        }
    }

    /** Recalculate best price and ranking positions based on netPrice > 0 */
    public void computeCompetitiveness() {
        bestPrice = Double.MAX_VALUE;
        winnerSupplier = null;
        supplierPositions.clear();

        // Collect valid prices
        List<Map.Entry<Supplier, Double>> validPrices = new ArrayList<>();
        for (var entry : supplierPrices.entrySet()) {
            SupplierProduct sp = entry.getValue();
            if (sp.getNetPrice() > 0) {
                validPrices.add(Map.entry(entry.getKey(), sp.getNetPrice()));
            }
        }

        if (validPrices.isEmpty()) {
            diffPct = 0;
            return;
        }

        // Sort ascending by price
        validPrices.sort(Comparator.comparingDouble(Map.Entry::getValue));

        // Assign positions (1-based ranking)
        for (int i = 0; i < validPrices.size(); i++) {
            supplierPositions.put(validPrices.get(i).getKey(), i + 1);
        }

        // Winner = position 1
        bestPrice = validPrices.get(0).getValue();
        winnerSupplier = validPrices.get(0).getKey();

        // DIF% = difference between best and second best
        if (validPrices.size() >= 2) {
            double secondBest = validPrices.get(1).getValue();
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

    public double getBasePriceForSupplier(Supplier s) {
        SupplierProduct sp = supplierPrices.get(s);
        return sp != null ? sp.getBasePrice() : 0;
    }

    public double getOfferPctForSupplier(Supplier s) {
        SupplierProduct sp = supplierPrices.get(s);
        return sp != null ? sp.getOfferPct() : 0;
    }

    public double getNetPriceForSupplier(Supplier s) {
        SupplierProduct sp = supplierPrices.get(s);
        return sp != null ? sp.getNetPrice() : 0;
    }

    /** @deprecated Use getNetPriceForSupplier */
    public double getPriceForSupplier(Supplier s) {
        return getNetPriceForSupplier(s);
    }

    public int getPositionForSupplier(Supplier s) {
        return supplierPositions.getOrDefault(s, 0);
    }

    public int getStockForSupplier(Supplier s) {
        SupplierProduct sp = supplierPrices.get(s);
        return sp != null ? sp.getStock() : 0;
    }

    public int getSupplierCount() {
        return (int) supplierPrices.values().stream().filter(sp -> sp.getNetPrice() > 0).count();
    }

    /** Get discount/offer for a specific supplier */
    public double getDiscountForSupplier(Supplier s) {
        SupplierProduct sp = supplierPrices.get(s);
        return sp != null ? sp.getOfferPct() : 0;
    }

    /** Returns the supplier offering the best discount on this product */
    public Supplier getBestDiscountSupplier() {
        Supplier best = null;
        double bestDiscount = 0;
        for (var entry : supplierPrices.entrySet()) {
            if (entry.getValue().getOfferPct() > bestDiscount) {
                bestDiscount = entry.getValue().getOfferPct();
                best = entry.getKey();
            }
        }
        return best;
    }

    /** Returns the supplier with the highest (worst) price for this product */
    public Supplier getWorstPriceSupplier() {
        Supplier worst = null;
        double worstPrice = 0;
        for (var entry : supplierPrices.entrySet()) {
            SupplierProduct sp = entry.getValue();
            if (sp.getNetPrice() <= 0)
                continue;
            if (sp.getNetPrice() > worstPrice) {
                worstPrice = sp.getNetPrice();
                worst = entry.getKey();
            }
        }
        return worst;
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

    public Map<Supplier, Integer> getSupplierPositions() {
        return supplierPositions;
    }
}
