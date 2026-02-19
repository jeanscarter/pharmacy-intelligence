package com.pharmacyintel.engine;

import com.pharmacyintel.model.*;

import java.util.*;

/**
 * Core analytics engine: consolidates supplier data, computes competitiveness,
 * and generates summaries.
 * 
 * DroActiva-centric: only products that exist in DroActiva are included.
 */
public class ConsolidationEngine {

    private final Map<String, MasterProduct> masterCatalog = new LinkedHashMap<>();

    /**
     * DroActiva-centric consolidation: only products present in DroActiva's
     * catalog are included. Other suppliers' products are matched against
     * DroActiva's barcodes.
     */
    public void consolidate(Map<Supplier, List<SupplierProduct>> supplierData) {
        masterCatalog.clear();

        // Step 1: Build the base catalog from DroActiva
        List<SupplierProduct> droactivaProducts = supplierData.getOrDefault(Supplier.DROACTIVA, List.of());
        for (SupplierProduct sp : droactivaProducts) {
            String key = sp.getBarcode();
            if (key == null || key.isEmpty())
                continue;
            MasterProduct mp = masterCatalog.computeIfAbsent(key,
                    k -> new MasterProduct(k, sp.getDescription()));
            mp.addSupplierProduct(sp);
        }

        // Step 2: Match other suppliers' products ONLY against DroActiva's barcodes
        for (var entry : supplierData.entrySet()) {
            if (entry.getKey() == Supplier.DROACTIVA)
                continue; // Already added

            for (SupplierProduct sp : entry.getValue()) {
                String key = sp.getBarcode();
                if (key == null || key.isEmpty())
                    continue;

                // Only add if this barcode exists in DroActiva's catalog
                MasterProduct mp = masterCatalog.get(key);
                if (mp != null) {
                    mp.addSupplierProduct(sp);
                }
            }
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
     * Full pipeline: consolidate + competitiveness + margin sim
     */
    public Map<String, MasterProduct> process(Map<Supplier, List<SupplierProduct>> supplierData, double marginPct) {
        consolidate(supplierData);
        computeCompetitiveness();
        simulateMargin(marginPct);
        return masterCatalog;
    }

    public Map<String, MasterProduct> getMasterCatalog() {
        return masterCatalog;
    }

    public List<MasterProduct> getMasterProductList() {
        return new ArrayList<>(masterCatalog.values());
    }

    // --- Aggregate analytics ---

    /** Average net price per supplier (only products with stock) */
    public Map<Supplier, Double> getAveragePriceBySupplier() {
        Map<Supplier, List<Double>> prices = new EnumMap<>(Supplier.class);
        for (MasterProduct mp : masterCatalog.values()) {
            for (var entry : mp.getSupplierPrices().entrySet()) {
                if (entry.getValue().hasStock() && entry.getValue().getNetUsd() > 0) {
                    prices.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                            .add(entry.getValue().getNetUsd());
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

    /** Count of products with discounts per supplier */
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

    /** Average price considering discounts vs base price per supplier */
    public Map<Supplier, double[]> getBasePriceVsOfferPrice() {
        Map<Supplier, List<double[]>> data = new EnumMap<>(Supplier.class);
        for (MasterProduct mp : masterCatalog.values()) {
            for (var entry : mp.getSupplierPrices().entrySet()) {
                SupplierProduct sp = entry.getValue();
                if (sp.getPriceUsd() > 0 && sp.getNetUsd() > 0) {
                    data.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                            .add(new double[] { sp.getPriceUsd(), sp.getNetUsd() });
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

    /** Total unique products (DroActiva-only base) */
    public int getTotalProducts() {
        return masterCatalog.size();
    }

    /** Products with at least 2 suppliers */
    public long getComparableProducts() {
        return masterCatalog.values().stream().filter(mp -> mp.getSupplierCount() >= 2).count();
    }
}
