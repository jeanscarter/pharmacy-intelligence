package com.pharmacyintel.ui;

import com.pharmacyintel.model.MasterProduct;

import java.util.List;

/**
 * Listener notified whenever the filter in ProductTablePanel changes.
 * Carries the visible product list and the name of the active filter.
 */
@FunctionalInterface
public interface FilterChangeListener {
    void onFilterChanged(List<MasterProduct> visibleProducts, String filterName, boolean stockOnly);
}
