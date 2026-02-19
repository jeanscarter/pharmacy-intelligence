package com.pharmacyintel.model;

import java.time.LocalDateTime;

public class GlobalConfig {
    private static final GlobalConfig INSTANCE = new GlobalConfig();

    private double bcvRate = 1.0;
    private double targetMarginPct = 30.0;
    private LocalDateTime lastUpdated;

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
}
