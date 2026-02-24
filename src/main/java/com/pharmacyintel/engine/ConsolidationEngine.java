package com.pharmacyintel.engine;

import com.pharmacyintel.model.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core analytics engine: consolidates supplier data, computes competitiveness,
 * and generates summaries.
 *
 * Uses Full Outer Join: every barcode from every supplier is included.
 */
public class ConsolidationEngine {

    private final Map<String, MasterProduct> masterCatalog = new LinkedHashMap<>();
    private final Map<String, MasterProduct> universalCatalog = new LinkedHashMap<>();

    /**
     * Full Outer Join consolidation: iterates over ALL suppliers
     * and registers ANY barcode that exists, regardless of origin supplier.
     */
    public void consolidate(Map<Supplier, List<SupplierProduct>> supplierData) {
        masterCatalog.clear();

        for (var entry : supplierData.entrySet()) {
            for (SupplierProduct sp : entry.getValue()) {
                String key = sp.getBarcode();
                if (key == null || key.isEmpty())
                    continue;
                MasterProduct mp = masterCatalog.computeIfAbsent(key,
                        k -> new MasterProduct(k, sp.getDescription()));
                mp.addSupplierProduct(sp);
            }
        }
    }

    /**
     * Universal consolidation: same as consolidate but stored separately
     * for gap analysis purposes.
     */
    public void consolidateUniversal(Map<Supplier, List<SupplierProduct>> supplierData) {
        universalCatalog.clear();

        for (var entry : supplierData.entrySet()) {
            for (SupplierProduct sp : entry.getValue()) {
                String key = sp.getBarcode();
                if (key == null || key.isEmpty())
                    continue;
                MasterProduct mp = universalCatalog.computeIfAbsent(key,
                        k -> new MasterProduct(k, sp.getDescription()));
                mp.addSupplierProduct(sp);
            }
        }

        for (MasterProduct mp : universalCatalog.values()) {
            mp.computeCompetitiveness();
        }
    }

    /**
     * Compute competitiveness metrics for all products.
     */
    public void computeCompetitiveness() {
        for (MasterProduct mp : masterCatalog.values()) {
            mp.computeCompetitiveness();
        }
    }

    /**
     * Run margin simulation for all products.
     */
    public void simulateMargin(double marginPct) {
        for (MasterProduct mp : masterCatalog.values()) {
            mp.simulateMargin(marginPct);
        }
    }

    /**
     * Full pipeline: consolidate + universal + competitiveness + margin sim
     */
    public Map<String, MasterProduct> process(Map<Supplier, List<SupplierProduct>> supplierData, double marginPct) {
        consolidate(supplierData);
        consolidateUniversal(supplierData);
        computeCompetitiveness();
        simulateMargin(marginPct);
        return masterCatalog;
    }

    public Map<String, MasterProduct> getMasterCatalog() {
        return masterCatalog;
    }

    public Map<String, MasterProduct> getUniversalCatalog() {
        return universalCatalog;
    }

    public List<MasterProduct> getMasterProductList() {
        return new ArrayList<>(masterCatalog.values());
    }

    // =============================================
    // Executive Summary Analytics
    // =============================================

    /** Returns the supplier that wins the most price comparisons */
    public Supplier getSupplierWithMostWins() {
        Map<Supplier, Integer> wins = getWinCountBySupplier();
        return wins.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * Returns the supplier that appears most as the worst-priced (most expensive)
     */
    public Supplier getSupplierWithMostLosses() {
        Map<Supplier, Integer> losses = new EnumMap<>(Supplier.class);
        for (Supplier s : Supplier.values())
            losses.put(s, 0);

        for (MasterProduct mp : masterCatalog.values()) {
            Supplier worst = mp.getWorstPriceSupplier();
            if (worst != null) {
                losses.merge(worst, 1, Integer::sum);
            }
        }
        return losses.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /** Returns the supplier with the best average discount across products */
    public Supplier getSupplierWithBestAvgDiscount() {
        Map<Supplier, List<Double>> discounts = new EnumMap<>(Supplier.class);
        for (MasterProduct mp : masterCatalog.values()) {
            for (var entry : mp.getSupplierPrices().entrySet()) {
                if (entry.getValue().hasDiscount()) {
                    discounts.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                            .add(entry.getValue().getOfferPct());
                }
            }
        }
        return discounts.entrySet().stream()
                .max(Comparator.comparingDouble(e -> e.getValue().stream()
                        .mapToDouble(d -> d).average().orElse(0)))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /** Returns the supplier with the worst average discount position */
    public Supplier getSupplierWithWorstAvgDiscount() {
        Map<Supplier, List<Double>> discounts = new EnumMap<>(Supplier.class);
        for (MasterProduct mp : masterCatalog.values()) {
            for (var entry : mp.getSupplierPrices().entrySet()) {
                discounts.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                        .add(entry.getValue().getOfferPct());
            }
        }
        return discounts.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .min(Comparator.comparingDouble(e -> e.getValue().stream()
                        .mapToDouble(d -> d).average().orElse(0)))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    // =============================================
    // Molecule Search (Fuzzy by keyword)
    // =============================================

    public List<MasterProduct> getCheapestByMolecule(String keyword) {
        if (keyword == null || keyword.isBlank())
            return List.of();
        String[] tokens = keyword.toLowerCase().trim().split("\\s+");

        return universalCatalog.values().stream()
                .filter(mp -> {
                    if (mp.getDescription() == null)
                        return false;
                    String desc = mp.getDescription().toLowerCase();
                    for (String token : tokens) {
                        if (!desc.contains(token))
                            return false;
                    }
                    return true;
                })
                .sorted(Comparator.comparingDouble(mp -> mp.getBestPrice() > 0 ? mp.getBestPrice() : Double.MAX_VALUE))
                .collect(Collectors.toList());
    }

    // =============================================
    // Gap Analysis
    // =============================================

    public List<MasterProduct> getGapProducts(Supplier target) {
        return universalCatalog.values().stream()
                .filter(mp -> {
                    SupplierProduct sp = mp.getSupplierPrices().get(target);
                    return sp == null || !sp.hasStock();
                })
                .filter(mp -> {
                    return mp.getSupplierPrices().entrySet().stream()
                            .anyMatch(e -> e.getKey() != target && e.getValue().hasStock());
                })
                .collect(Collectors.toList());
    }

    public Map<Supplier, Integer> getGapSummaryBySupplier() {
        List<MasterProduct> gaps = getGapProducts(Supplier.DROACTIVA);
        Map<Supplier, Integer> summary = new EnumMap<>(Supplier.class);
        for (Supplier s : Supplier.values()) {
            if (s == Supplier.DROACTIVA)
                continue;
            summary.put(s, 0);
        }
        for (MasterProduct mp : gaps) {
            for (var entry : mp.getSupplierPrices().entrySet()) {
                if (entry.getKey() != Supplier.DROACTIVA && entry.getValue().hasStock()) {
                    summary.merge(entry.getKey(), 1, Integer::sum);
                }
            }
        }
        return summary;
    }

    // =============================================
    // Aggregate Analytics
    // =============================================

    /** Average net price per supplier (only products with netPrice > 0) */
    public Map<Supplier, Double> getAveragePriceBySupplier() {
        Map<Supplier, List<Double>> prices = new EnumMap<>(Supplier.class);
        for (MasterProduct mp : masterCatalog.values()) {
            for (var entry : mp.getSupplierPrices().entrySet()) {
                if (entry.getValue().getNetPrice() > 0) {
                    prices.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                            .add(entry.getValue().getNetPrice());
                }
            }
        }
        Map<Supplier, Double> avg = new EnumMap<>(Supplier.class);
        for (var entry : prices.entrySet()) {
            avg.put(entry.getKey(), entry.getValue().stream().mapToDouble(d -> d).average().orElse(0));
        }
        return avg;
    }

    /** Win count per supplier */
    public Map<Supplier, Integer> getWinCountBySupplier() {
        Map<Supplier, Integer> wins = new EnumMap<>(Supplier.class);
        for (Supplier s : Supplier.values())
            wins.put(s, 0);

        for (MasterProduct mp : masterCatalog.values()) {
            if (mp.getWinnerSupplier() != null) {
                wins.merge(mp.getWinnerSupplier(), 1, Integer::sum);
            }
        }
        return wins;
    }

    /** Total stock per supplier */
    public Map<Supplier, Integer> getTotalStockBySupplier() {
        Map<Supplier, Integer> stock = new EnumMap<>(Supplier.class);
        for (MasterProduct mp : masterCatalog.values()) {
            for (var entry : mp.getSupplierPrices().entrySet()) {
                stock.merge(entry.getKey(), entry.getValue().getStock(), Integer::sum);
            }
        }
        return stock;
    }

    /** Count of products with offers per supplier */
    public Map<Supplier, Integer> getOfferCountBySupplier() {
        Map<Supplier, Integer> offers = new EnumMap<>(Supplier.class);
        for (MasterProduct mp : masterCatalog.values()) {
            for (var entry : mp.getSupplierPrices().entrySet()) {
                if (entry.getValue().hasDiscount()) {
                    offers.merge(entry.getKey(), 1, Integer::sum);
                }
            }
        }
        return offers;
    }

    /** Average base price vs net price per supplier */
    public Map<Supplier, double[]> getBasePriceVsOfferPrice() {
        Map<Supplier, List<double[]>> data = new EnumMap<>(Supplier.class);
        for (MasterProduct mp : masterCatalog.values()) {
            for (var entry : mp.getSupplierPrices().entrySet()) {
                SupplierProduct sp = entry.getValue();
                if (sp.getBasePrice() > 0 && sp.getNetPrice() > 0) {
                    data.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                            .add(new double[] { sp.getBasePrice(), sp.getNetPrice() });
                }
            }
        }
        Map<Supplier, double[]> result = new EnumMap<>(Supplier.class);
        for (var entry : data.entrySet()) {
            double avgBase = entry.getValue().stream().mapToDouble(d -> d[0]).average().orElse(0);
            double avgNet = entry.getValue().stream().mapToDouble(d -> d[1]).average().orElse(0);
            result.put(entry.getKey(), new double[] { avgBase, avgNet });
        }
        return result;
    }

    /** Total unique products */
    public int getTotalProducts() {
        return masterCatalog.size();
    }

    /** Total products in universal catalog */
    public int getUniversalProductCount() {
        return universalCatalog.size();
    }

    /** Products with at least 2 suppliers */
    public long getComparableProducts() {
        return masterCatalog.values().stream().filter(mp -> mp.getSupplierCount() >= 2).count();
    }
}
