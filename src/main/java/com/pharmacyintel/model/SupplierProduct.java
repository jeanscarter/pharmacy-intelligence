package com.pharmacyintel.model;

public class SupplierProduct {
    private String barcode;
    private String description;
    private double basePrice;
    private double offerPct;
    private double netPrice;
    private int stock;
    private Supplier supplier;
    private double iva;

    public SupplierProduct() {
    }

    public SupplierProduct(String barcode, String description, double basePrice, double offerPct, int stock,
            Supplier supplier) {
        this.barcode = barcode;
        this.description = description;
        this.basePrice = basePrice;
        this.offerPct = offerPct;
        this.netPrice = basePrice * (1.0 - offerPct / 100.0);
        this.stock = stock;
        this.supplier = supplier;
    }

    /** Recalculate netPrice from basePrice and offerPct */
    public void recalcNet() {
        this.netPrice = basePrice * (1.0 - offerPct / 100.0);
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

    public double getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(double basePrice) {
        this.basePrice = basePrice;
    }

    public double getOfferPct() {
        return offerPct;
    }

    public void setOfferPct(double offerPct) {
        this.offerPct = offerPct;
    }

    public double getNetPrice() {
        return netPrice;
    }

    public void setNetPrice(double netPrice) {
        this.netPrice = netPrice;
    }

    /** @deprecated Use getNetPrice() */
    public double getNetUsd() {
        return netPrice;
    }

    /** @deprecated Use setNetPrice() */
    public void setNetUsd(double net) {
        this.netPrice = net;
    }

    /** @deprecated Use getBasePrice() */
    public double getPriceUsd() {
        return basePrice;
    }

    /** @deprecated Use setBasePrice() */
    public void setPriceUsd(double price) {
        this.basePrice = price;
    }

    /** @deprecated Use getOfferPct() */
    public double getDiscountPct() {
        return offerPct;
    }

    /** @deprecated Use setOfferPct() */
    public void setDiscountPct(double pct) {
        this.offerPct = pct;
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
        return offerPct > 0;
    }
}
