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

        // --- Ordinal ranking: strictly sequential 1, 2, 3... with stock as tie-breaker ---
        validPrices.sort((e1, e2) -> {
            int c = Double.compare(e1.getValue(), e2.getValue());
            if (c == 0) {
                // Secondary sort: higher stock wins
                SupplierProduct sp1 = supplierPrices.get(e1.getKey());
                SupplierProduct sp2 = supplierPrices.get(e2.getKey());
                int s1 = sp1 != null ? sp1.getStock() : 0;
                int s2 = sp2 != null ? sp2.getStock() : 0;
                return Integer.compare(s2, s1);
            }
            return c;
        });

        for (int i = 0; i < validPrices.size(); i++) {
            supplierPositions.put(validPrices.get(i).getKey(), i + 1);
        }

        // --- Ordinal ranking for STOCK ONLY ---
        List<Map.Entry<Supplier, Double>> stockPrices = new ArrayList<>();
        for (var vp : validPrices) {
            SupplierProduct sp = supplierPrices.get(vp.getKey());
            if (sp != null && sp.hasStock()) {
                stockPrices.add(vp);
            }
        }
        
        stockPrices.sort((e1, e2) -> {
            int c = Double.compare(e1.getValue(), e2.getValue());
            if (c == 0) {
                SupplierProduct sp1 = supplierPrices.get(e1.getKey());
                SupplierProduct sp2 = supplierPrices.get(e2.getKey());
                int s1 = sp1 != null ? sp1.getStock() : 0;
                int s2 = sp2 != null ? sp2.getStock() : 0;
                return Integer.compare(s2, s1);
            }
            return c;
        });

        if (!stockPrices.isEmpty()) {
            for (int i = 0; i < stockPrices.size(); i++) {
                supplierPositionsStockOnly.put(stockPrices.get(i).getKey(), i + 1);
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

                // DIF USD = Precio_DroActiva - Mejor_Precio_Competencia
                double netDro = getNetPriceForSupplier(Supplier.DROACTIVA);
                double bestOther = Double.MAX_VALUE;
                for (var vp : validPrices) {
                    if (vp.getKey() != Supplier.DROACTIVA && vp.getValue() > 0) {
                        bestOther = Math.min(bestOther, vp.getValue());
                    }
                }
                
                if (netDro > 0 && bestOther < Double.MAX_VALUE && bestOther > 0) {
                    diffAmount = netDro - bestOther;
                    diffPct = (diffAmount / netDro) * 100.0;
                } else {
                    diffAmount = 0;
                    diffPct = 0;
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

                double netDroStock = 0;
                SupplierProduct spDro = supplierPrices.get(Supplier.DROACTIVA);
                if (spDro != null && spDro.hasStock()) {
                    netDroStock = spDro.getNetPrice();
                }

                double bestOtherStock = Double.MAX_VALUE;
                for (var vp : stockPrices) {
                    if (vp.getKey() != Supplier.DROACTIVA && vp.getValue() > 0) {
                        bestOtherStock = Math.min(bestOtherStock, vp.getValue());
                    }
                }

                if (netDroStock > 0 && bestOtherStock < Double.MAX_VALUE && bestOtherStock > 0) {
                    diffAmountStockOnly = netDroStock - bestOtherStock;
                    diffPctStockOnly = (diffAmountStockOnly / netDroStock) * 100.0;
                } else {
                    diffAmountStockOnly = 0;
                    diffPctStockOnly = 0;
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
     * Returns the internal product code from DroActiva (if available).
     */
    public String getInternalCode() {
        SupplierProduct sp = supplierPrices.get(Supplier.DROACTIVA);
        return sp != null ? sp.getInternalCode() : null;
    }

    /**
     * Returns the brand from DroActiva (if available).
     */
    public String getBrand() {
        SupplierProduct sp = supplierPrices.get(Supplier.DROACTIVA);
        return sp != null ? sp.getBrand() : null;
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
