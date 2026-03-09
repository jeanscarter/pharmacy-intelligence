package com.pharmacyintel.model;

import java.util.*;

public class MasterProduct {
    private String barcode;
    private String description;
    private final Map<Supplier, SupplierProduct> supplierPrices = new EnumMap<>(Supplier.class);

    // Computed fields
    private double bestPrice = Double.MAX_VALUE;
    private double bestPriceStockOnly = Double.MAX_VALUE;
    private Supplier winnerSupplier;
    private Supplier winnerSupplierStockOnly;
    private Supplier loserSupplier;
    private Supplier loserSupplierStockOnly;
    private double diffPct;
    private double diffPctStockOnly;
    private double diffAmount;
    private double diffAmountStockOnly;
    private double simulatedSalePrice;
    private double simulatedSalePriceStockOnly;
    private double simulatedMargin;
    private double simulatedMarginStockOnly;
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
        bestPriceStockOnly = Double.MAX_VALUE;
        winnerSupplier = null;
        winnerSupplierStockOnly = null;
        loserSupplier = null;
        loserSupplierStockOnly = null;
        diffAmount = 0;
        diffAmountStockOnly = 0;
        diffPct = 0;
        diffPctStockOnly = 0;
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

        // --- NORMAL CALCULATIONS ---
        if (!validPrices.isEmpty()) {
            winnerSupplier = validPrices.get(0).getKey();
            bestPrice = validPrices.get(0).getValue();

            if (validPrices.size() >= 2) {
                loserSupplier = validPrices.get(validPrices.size() - 1).getKey();
                if (loserSupplier == winnerSupplier)
                    loserSupplier = null;

                double secondBest = validPrices.get(1).getValue();
                diffAmount = secondBest - bestPrice;
                if (bestPrice > 0) {
                    diffPct = (diffAmount / bestPrice) * 100.0;
                }
            }
        }

        // --- STOCK ONLY CALCULATIONS ---
        if (!stockPrices.isEmpty()) {
            winnerSupplierStockOnly = stockPrices.get(0).getKey();
            bestPriceStockOnly = stockPrices.get(0).getValue();

            if (stockPrices.size() >= 2) {
                loserSupplierStockOnly = stockPrices.get(stockPrices.size() - 1).getKey();
                if (loserSupplierStockOnly == winnerSupplierStockOnly)
                    loserSupplierStockOnly = null;

                double secondBestStock = stockPrices.get(1).getValue();
                diffAmountStockOnly = secondBestStock - bestPriceStockOnly;
                if (bestPriceStockOnly > 0) {
                    diffPctStockOnly = (diffAmountStockOnly / bestPriceStockOnly) * 100.0;
                }
            }
        } else if (!validPrices.isEmpty()) {
            // fallback if no one has stock
            bestPriceStockOnly = bestPrice;
            winnerSupplierStockOnly = winnerSupplier;
            loserSupplierStockOnly = loserSupplier;
            diffAmountStockOnly = diffAmount;
            diffPctStockOnly = diffPct;
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
        if (bestPriceStockOnly < Double.MAX_VALUE && bestPriceStockOnly > 0) {
            simulatedSalePriceStockOnly = bestPriceStockOnly * (1.0 + marginPct / 100.0);
            simulatedMarginStockOnly = simulatedSalePriceStockOnly - bestPriceStockOnly;
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

    public double getBestPrice(boolean stockOnly) {
        double bp = stockOnly ? bestPriceStockOnly : bestPrice;
        return bp == Double.MAX_VALUE ? 0 : bp;
    }

    public Supplier getWinnerSupplier() {
        return winnerSupplier;
    }

    public Supplier getWinnerSupplier(boolean stockOnly) {
        return stockOnly ? winnerSupplierStockOnly : winnerSupplier;
    }

    public Supplier getWorstPriceSupplier() {
        return loserSupplier;
    }

    public Supplier getWorstPriceSupplier(boolean stockOnly) {
        return stockOnly ? loserSupplierStockOnly : loserSupplier;
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

    public double getDiffPct(boolean stockOnly) {
        return stockOnly ? diffPctStockOnly : diffPct;
    }

    public double getDiffAmount() {
        return diffAmount;
    }

    public double getDiffAmount(boolean stockOnly) {
        return stockOnly ? diffAmountStockOnly : diffAmount;
    }

    public double getSimulatedSalePrice() {
        return simulatedSalePrice;
    }

    public double getSimulatedSalePrice(boolean stockOnly) {
        return stockOnly ? simulatedSalePriceStockOnly : simulatedSalePrice;
    }

    public double getSimulatedMargin() {
        return simulatedMargin;
    }

    public double getSimulatedMargin(boolean stockOnly) {
        return stockOnly ? simulatedMarginStockOnly : simulatedMargin;
    }

    public Map<Supplier, Integer> getSupplierPositions() {
        return supplierPositions;
    }
}
