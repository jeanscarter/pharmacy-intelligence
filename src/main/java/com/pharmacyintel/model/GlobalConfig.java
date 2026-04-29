package com.pharmacyintel.model;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

public class GlobalConfig {
    private static final GlobalConfig INSTANCE = new GlobalConfig();

    private double bcvRate = 1.0;
    private double targetMarginPct = 30.0;
    private LocalDateTime lastUpdated;
    
    // Commercial Discount (DC) fields
    private final Map<Supplier, Double> commercialDiscounts = new EnumMap<>(Supplier.class);
    private boolean applyCommercialDiscount = false;

    private GlobalConfig() {
    }

    public static GlobalConfig getInstance() {
        return INSTANCE;
    }

    public double getBcvRate() {
        return bcvRate;
    }

    public void setBcvRate(double bcvRate) {
        this.bcvRate = bcvRate;
        this.lastUpdated = LocalDateTime.now();
    }

    public double getTargetMarginPct() {
        return targetMarginPct;
    }

    public void setTargetMarginPct(double targetMarginPct) {
        this.targetMarginPct = targetMarginPct;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public double usdToVes(double usd) {
        return usd * bcvRate;
    }

    public double vesToUsd(double ves) {
        return bcvRate > 0 ? ves / bcvRate : 0;
    }

    public void setCommercialDiscount(Supplier supplier, double discount) {
        commercialDiscounts.put(supplier, discount);
    }

    public double getCommercialDiscount(Supplier supplier) {
        return commercialDiscounts.getOrDefault(supplier, 0.0);
    }

    public boolean isApplyCommercialDiscount() {
        return applyCommercialDiscount;
    }

    public void setApplyCommercialDiscount(boolean applyCommercialDiscount) {
        this.applyCommercialDiscount = applyCommercialDiscount;
    }
}
