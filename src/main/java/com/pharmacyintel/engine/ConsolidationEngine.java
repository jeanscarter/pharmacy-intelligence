package com.pharmacyintel.engine;

import com.pharmacyintel.model.*;

import java.util.*;
import java.util.stream.Collectors;

public class ConsolidationEngine {

    private final Map<String, MasterProduct> masterCatalog = new LinkedHashMap<>();
    private final Map<String, MasterProduct> universalCatalog = new LinkedHashMap<>();
    private Map<Supplier, List<SupplierProduct>> rawSupplierData;

    /**
     * Consolidate with mode selection.
     * 
     * @param includeAllProducts false = DroActiva-centric, true = Full Outer Join
     */
    public void consolidate(boolean includeAllProducts) {
        masterCatalog.clear();

        if (rawSupplierData == null || rawSupplierData.isEmpty())
            return;

        if (includeAllProducts) {
            // Full Outer Join: every barcode from every supplier
            for (var entry : rawSupplierData.entrySet()) {
                for (SupplierProduct sp : entry.getValue()) {
                    String key = sp.getBarcode();
                    if (key == null || key.isEmpty())
                        continue;
                    MasterProduct mp = masterCatalog.computeIfAbsent(key,
                            k -> new MasterProduct(k, sp.getDescription()));
                    mp.addSupplierProduct(sp);
                }
            }
        } else {
            // DroActiva-centric: build base catalog from DroActiva only
            List<SupplierProduct> droactivaList = rawSupplierData.getOrDefault(Supplier.DROACTIVA, List.of());
            for (SupplierProduct sp : droactivaList) {
                String key = sp.getBarcode();
                if (key == null || key.isEmpty())
                    continue;
                MasterProduct mp = masterCatalog.computeIfAbsent(key,
                        k -> new MasterProduct(k, sp.getDescription()));
                mp.addSupplierProduct(sp);
            }

            // Add other suppliers only if barcode already exists
            for (var entry : rawSupplierData.entrySet()) {
                if (entry.getKey() == Supplier.DROACTIVA)
                    continue;
                for (SupplierProduct sp : entry.getValue()) {
                    String key = sp.getBarcode();
                    if (key == null || key.isEmpty())
                        continue;
                    MasterProduct mp = masterCatalog.get(key);
                    if (mp != null) {
                        mp.addSupplierProduct(sp);
                    }
                }
            }
        }
    }

    public void consolidateUniversal() {
        universalCatalog.clear();

        if (rawSupplierData == null)
            return;

        for (var entry : rawSupplierData.entrySet()) {
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

    public void computeCompetitiveness() {
        for (MasterProduct mp : masterCatalog.values()) {
            mp.computeCompetitiveness();
        }
    }

    public void simulateMargin(double marginPct) {
        for (MasterProduct mp : masterCatalog.values()) {
            mp.simulateMargin(marginPct);
        }
    }

    /**
     * Full pipeline: store raw data, consolidate, analyze, margin.
     */
    public Map<String, MasterProduct> process(Map<Supplier, List<SupplierProduct>> supplierData,
            double marginPct, boolean includeAllProducts) {
        this.rawSupplierData = supplierData;
        consolidate(includeAllProducts);
        consolidateUniversal();
        fillDescriptions();
        computeCompetitiveness();
        simulateMargin(marginPct);
        return masterCatalog;
    }

    /**
     * Recalculate with new parameters without re-parsing files.
     */
    public void recalculate(double marginPct, boolean includeAllProducts) {
        consolidate(includeAllProducts);
        consolidateUniversal();
        fillDescriptions();
        computeCompetitiveness();
        simulateMargin(marginPct);
    }

    /**
     * Fill empty/invalid descriptions in the master catalog
     * by scanning ALL raw supplier data for the longest valid description per
     * barcode.
     */
    private void fillDescriptions() {
        if (rawSupplierData == null)
            return;

        // Build barcode -> best description from raw data
        Map<String, String> bestDescriptions = new HashMap<>();
        for (var entry : rawSupplierData.entrySet()) {
            for (SupplierProduct sp : entry.getValue()) {
                String barcode = sp.getBarcode();
                String desc = sp.getDescription();
                if (barcode == null || barcode.isEmpty())
                    continue;
                if (desc == null || desc.isBlank())
                    continue;
                String lower = desc.trim().toLowerCase();
                if (lower.equals("false") || lower.equals("true") || lower.equals("null")
                        || lower.equals("falso") || lower.equals("verdadero"))
                    continue;

                String existing = bestDescriptions.get(barcode);
                if (existing == null || desc.length() > existing.length()) {
                    bestDescriptions.put(barcode, desc);
                }
            }
        }

        // Fill empty descriptions in master catalog
        for (var entry : masterCatalog.entrySet()) {
            MasterProduct mp = entry.getValue();
            String fallback = bestDescriptions.get(entry.getKey());
            if (fallback != null) {
                mp.fillEmptyDescription(fallback);
            }
        }
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

    public Supplier getSupplierWithMostWins() {
        Map<Supplier, Integer> wins = getWinCountBySupplier();
        return wins.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public Supplier getSupplierWithMostLosses() {
        Map<Supplier, Integer> losses = new EnumMap<>(Supplier.class);
        for (Supplier s : Supplier.values())
            losses.put(s, 0);

        for (MasterProduct mp : masterCatalog.values()) {
            Supplier worst = mp.getLoserSupplier();
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
    // Molecule Search
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

    public Map<Supplier, Integer> getTotalStockBySupplier() {
        Map<Supplier, Integer> stock = new EnumMap<>(Supplier.class);
        for (MasterProduct mp : masterCatalog.values()) {
            for (var entry : mp.getSupplierPrices().entrySet()) {
                stock.merge(entry.getKey(), entry.getValue().getStock(), Integer::sum);
            }
        }
        return stock;
    }

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

    public int getTotalProducts() {
        return masterCatalog.size();
    }

    public int getUniversalProductCount() {
        return universalCatalog.size();
    }

    public long getComparableProducts() {
        return masterCatalog.values().stream().filter(mp -> mp.getSupplierCount() >= 2).count();
    }
}
