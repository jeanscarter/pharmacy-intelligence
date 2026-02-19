package com.pharmacyintel.model;

import java.awt.Color;

public enum Supplier {
    DROACTIVA("Droactiva", new Color(66, 133, 244)),
    DROMARKO("Dromarko", new Color(234, 67, 53)),
    COBECA("Cobeca", new Color(52, 168, 83)),
    NENA("Nena", new Color(251, 188, 4)),
    F24("F24", new Color(171, 71, 188)),
    P365("365", new Color(255, 112, 67));

    private final String displayName;
    private final Color color;

    Supplier(String displayName, Color color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Color getColor() {
        return color;
    }
}
