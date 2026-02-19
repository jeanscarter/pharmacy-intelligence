package com.pharmacyintel.model;

public class SupplierProduct {
    private String barcode;
    private String description;
    private double netUsd;
    private double priceUsd;
    private int stock;
    private Supplier supplier;
    private double discountPct;
    private double iva;

    public SupplierProduct() {
    }

    public SupplierProduct(String barcode, String description, double netUsd, int stock, Supplier supplier) {
        this.barcode = barcode;
        this.description = description;
        this.netUsd = netUsd;
        this.stock = stock;
        this.supplier = supplier;
    }

    // --- Getters & Setters ---
    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getNetUsd() {
        return netUsd;
    }

    public void setNetUsd(double netUsd) {
        this.netUsd = netUsd;
    }

    public double getPriceUsd() {
        return priceUsd;
    }

    public void setPriceUsd(double priceUsd) {
        this.priceUsd = priceUsd;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public void setSupplier(Supplier supplier) {
        this.supplier = supplier;
    }

    public double getDiscountPct() {
        return discountPct;
    }

    public void setDiscountPct(double discountPct) {
        this.discountPct = discountPct;
    }

    public double getIva() {
        return iva;
    }

    public void setIva(double iva) {
        this.iva = iva;
    }

    public boolean hasStock() {
        return stock > 0;
    }

    public boolean hasDiscount() {
        return discountPct > 0;
    }
}
