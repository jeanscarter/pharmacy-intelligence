package com.pharmacyintel.parser;

import com.pharmacyintel.model.SupplierProduct;
import java.io.File;
import java.util.List;

/** Strategy interface for supplier-specific file parsers */
public interface SupplierParser {
    List<SupplierProduct> parse(File file) throws Exception;
}
