package com.pharmacyintel.model;

public class SupplierProduct {
    private String barcode;
    private String description;
    private String internalCode;
    private String brand;
    private double basePrice;
    /** Individual discount percentages applied in cascade (e.g. DA, DA2, DV). */
    private double[] discounts = {};
    private double netPrice;
    private int stock;
    private Supplier supplier;
    private double iva;
    private String expirationDate;

    public SupplierProduct() {
    }

    /**
     * Legacy constructor — treats the single offerPct as the only discount.
     */
    public SupplierProduct(String barcode, String description, double basePrice, double offerPct, int stock,
            Supplier supplier) {
        this.barcode = barcode;
        this.description = description;
        this.basePrice = basePrice;
        this.discounts = offerPct != 0 ? new double[] { offerPct } : new double[] {};
        this.stock = stock;
        this.supplier = supplier;
        recalcNet();
    }

    /**
     * Set individual discount percentages (cascading).
     * Example: setDiscounts(10, 5, 2) → applies 10%, then 5%, then 2% sequentially.
     * Zero-value discounts at the end are ignored.
     */
    public void setDiscounts(double... discounts) {
        this.discounts = discounts != null ? discounts : new double[] {};
        recalcNet();
    }

    /** @return the individual discount percentages array */
    public double[] getDiscounts() {
        return discounts;
    }

    /**
     * Recalculate netPrice using cascading multiplicative discounts.
     * NetPrice = basePrice × (1 - d1/100) × (1 - d2/100) × ...
     */
    public void recalcNet() {
        double price = basePrice;
        for (double d : discounts) {
            price *= (1.0 - d / 100.0);
        }
        this.netPrice = price;
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

    /**
     * Returns the effective total discount percentage computed from cascading.
     * Formula: (1 - netPrice / basePrice) × 100
     */
    public double getOfferPct() {
        if (basePrice <= 0) return 0;
        return (1.0 - netPrice / basePrice) * 100.0;
    }

    /**
     * Legacy setter — sets a single discount value.
     */
    public void setOfferPct(double offerPct) {
        this.discounts = offerPct != 0 ? new double[] { offerPct } : new double[] {};
        recalcNet();
    }

    public double getNetPrice() {
        return netPrice;
    }

    public void setNetPrice(double netPrice) {
        this.netPrice = netPrice;
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

    public String getInternalCode() {
        return internalCode;
    }

    public void setInternalCode(String internalCode) {
        this.internalCode = internalCode;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public double getIva() {
        return iva;
    }

    public void setIva(double iva) {
        this.iva = iva;
    }

    public String getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(String expirationDate) {
        this.expirationDate = expirationDate;
    }

    public boolean hasStock() {
        return stock > 0;
    }

    public boolean hasDiscount() {
        for (double d : discounts) {
            if (d > 0) return true;
        }
        return false;
    }
}
