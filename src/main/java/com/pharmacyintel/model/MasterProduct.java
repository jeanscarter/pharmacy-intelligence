package com.pharmacyintel.model;

import java.util.*;

public class MasterProduct {
    private String barcode;
    private String description;
    private final Map<Supplier, SupplierProduct> supplierPrices = new EnumMap<>(Supplier.class);

    // Computed fields
    private double bestPrice = Double.MAX_VALUE;
    private Supplier winnerSupplier;
    private Supplier loserSupplier;
    private double diffPct;
    private double diffAmount; // NEW: absolute USD difference between 2nd best and best
    private double simulatedSalePrice;
    private double simulatedMargin;
    private final Map<Supplier, Integer> supplierPositions = new EnumMap<>(Supplier.class);
    private final Map<Supplier, Integer> supplierPositionsStockOnly = new EnumMap<>(Supplier.class);

    public MasterProduct(String barcode, String description) {
        this.barcode = barcode;
        this.description = description;
    }

    public void addSupplierProduct(SupplierProduct sp) {
        supplierPrices.put(sp.getSupplier(), sp);

        // Description priority: F24 or Cobeca (they include lab brand), else longest
        if (sp.getSupplier() == Supplier.F24 || sp.getSupplier() == Supplier.COBECA) {
            if (isValidDescription(sp.getDescription())) {
                description = sp.getDescription();
            }
        } else if (!isValidDescription(description)) {
            if (isValidDescription(sp.getDescription())) {
                description = sp.getDescription();
            }
        } else if (isValidDescription(sp.getDescription()) && sp.getDescription().length() > description.length()) {
            boolean currentFromPriority = false;
            for (var entry : supplierPrices.entrySet()) {
                if ((entry.getKey() == Supplier.F24 || entry.getKey() == Supplier.COBECA)
                        && isValidDescription(entry.getValue().getDescription())
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

    private boolean isValidDescription(String desc) {
        if (desc == null || desc.isBlank())
            return false;
        String lower = desc.trim().toLowerCase();
        return !lower.equals("false") && !lower.equals("true") && !lower.equals("null")
                && !lower.equals("falso") && !lower.equals("verdadero");
    }

    public void computeCompetitiveness() {
        bestPrice = Double.MAX_VALUE;
        winnerSupplier = null;
        loserSupplier = null;
        diffAmount = 0;
        supplierPositions.clear();
        supplierPositionsStockOnly.clear();

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

        // --- Dense ranking: suppliers with the same price share the same position ---
        int rank = 1;
        supplierPositions.put(validPrices.get(0).getKey(), rank);
        for (int i = 1; i < validPrices.size(); i++) {
            double prevPrice = validPrices.get(i - 1).getValue();
            double currPrice = validPrices.get(i).getValue();
            // Only bump rank if price is strictly different (tolerance 0.001)
            if (Math.abs(currPrice - prevPrice) > 0.001) {
                rank++;
            }
            supplierPositions.put(validPrices.get(i).getKey(), rank);
        }

        // --- Dense ranking for STOCK ONLY ---
        List<Map.Entry<Supplier, Double>> stockPrices = new ArrayList<>();
        for (var vp : validPrices) {
            SupplierProduct sp = supplierPrices.get(vp.getKey());
            if (sp != null && sp.hasStock()) {
                stockPrices.add(vp);
            }
        }
        if (!stockPrices.isEmpty()) {
            int rankStock = 1;
            supplierPositionsStockOnly.put(stockPrices.get(0).getKey(), rankStock);
            for (int i = 1; i < stockPrices.size(); i++) {
                double prevPrice = stockPrices.get(i - 1).getValue();
                double currPrice = stockPrices.get(i).getValue();
                if (Math.abs(currPrice - prevPrice) > 0.001) {
                    rankStock++;
                }
                supplierPositionsStockOnly.put(stockPrices.get(i).getKey(), rankStock);
            }
        }

        // --- Ghost Price logic: winner must have stock ---
        // Find the first supplier (by lowest price) that actually has stock
        Supplier effectiveWinner = null;
        double effectiveBestPrice = Double.MAX_VALUE;
        for (var vp : validPrices) {
            SupplierProduct sp = supplierPrices.get(vp.getKey());
            if (sp != null && sp.hasStock()) {
                effectiveWinner = vp.getKey();
                effectiveBestPrice = vp.getValue();
                break;
            }
        }
        // Fallback: if nobody has stock, use the absolute lowest price
        if (effectiveWinner == null) {
            effectiveWinner = validPrices.get(0).getKey();
            effectiveBestPrice = validPrices.get(0).getValue();
        }

        bestPrice = effectiveBestPrice;
        winnerSupplier = effectiveWinner;

        // --- Loser: highest price among suppliers WITH stock ---
        Supplier effectiveLoser = null;
        for (int i = validPrices.size() - 1; i >= 0; i--) {
            Supplier s = validPrices.get(i).getKey();
            if (s == winnerSupplier)
                continue;
            SupplierProduct sp = supplierPrices.get(s);
            if (sp != null && sp.hasStock()) {
                effectiveLoser = s;
                break;
            }
        }
        // Fallback: if nobody else has stock, use highest price regardless
        if (effectiveLoser == null && validPrices.size() > 1) {
            effectiveLoser = validPrices.get(validPrices.size() - 1).getKey();
            if (effectiveLoser == winnerSupplier)
                effectiveLoser = null;
        }
        loserSupplier = effectiveLoser;

        // DIF% = difference between best and second best (with stock)
        if (validPrices.size() >= 2) {
            // Find second best price (with stock, different from winner)
            double secondBest = -1;
            for (var vp : validPrices) {
                if (vp.getKey() != winnerSupplier) {
                    SupplierProduct sp = supplierPrices.get(vp.getKey());
                    if (sp != null && sp.hasStock()) {
                        secondBest = vp.getValue();
                        break;
                    }
                }
            }
            // Fallback: use absolute second if no stocked second exists
            if (secondBest < 0) {
                for (var vp : validPrices) {
                    if (vp.getKey() != winnerSupplier) {
                        secondBest = vp.getValue();
                        break;
                    }
                }
            }
            if (secondBest > 0 && bestPrice > 0) {
                diffPct = ((secondBest - bestPrice) / bestPrice) * 100.0;
                diffAmount = secondBest - bestPrice;
            } else {
                diffPct = 0;
                diffAmount = 0;
            }
        } else {
            diffPct = 0;
            diffAmount = 0;
        }
    }

    /**
     * Returns the USD difference between the given supplier's net price and the
     * best price.
     * Positive = supplier is more expensive. Zero or negative = supplier IS the
     * best.
     */
    public double getDiffAmountForSupplier(Supplier s) {
        double net = getNetPriceForSupplier(s);
        if (net <= 0 || bestPrice <= 0 || bestPrice >= Double.MAX_VALUE)
            return 0;
        return net - bestPrice;
    }

    /**
     * Returns the percentage difference between the given supplier's price and the
     * best price.
     */
    public double getDiffPctForSupplier(Supplier s) {
        double net = getNetPriceForSupplier(s);
        if (net <= 0 || bestPrice <= 0 || bestPrice >= Double.MAX_VALUE)
            return 0;
        return ((net - bestPrice) / bestPrice) * 100.0;
    }

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
    @Deprecated
    public double getPriceForSupplier(Supplier s) {
        return getNetPriceForSupplier(s);
    }

    public int getPositionForSupplier(Supplier s) {
        return supplierPositions.getOrDefault(s, 0);
    }

    public int getStockOnlyPositionForSupplier(Supplier s) {
        return supplierPositionsStockOnly.getOrDefault(s, 0);
    }

    public int getStockForSupplier(Supplier s) {
        SupplierProduct sp = supplierPrices.get(s);
        return sp != null ? sp.getStock() : 0;
    }

    public int getSupplierCount() {
        return (int) supplierPrices.values().stream().filter(sp -> sp.getNetPrice() > 0).count();
    }

    public double getDiscountForSupplier(Supplier s) {
        SupplierProduct sp = supplierPrices.get(s);
        return sp != null ? sp.getOfferPct() : 0;
    }

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

    public Supplier getWorstPriceSupplier() {
        return loserSupplier;
    }

    // --- Getters ---
    public String getBarcode() {
        return barcode;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Fill empty/invalid description from another source (e.g. universal catalog).
     */
    public void fillEmptyDescription(String fallbackDesc) {
        if (!isValidDescription(description) && isValidDescription(fallbackDesc)) {
            description = fallbackDesc;
        }
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

    public Supplier getLoserSupplier() {
        return loserSupplier;
    }

    public void setLoserSupplier(Supplier loserSupplier) {
        this.loserSupplier = loserSupplier;
    }

    public double getDiffPct() {
        return diffPct;
    }

    public double getDiffAmount() {
        return diffAmount;
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
