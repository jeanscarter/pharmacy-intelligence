package com.pharmacyintel.service;

import com.pharmacyintel.engine.ConsolidationEngine;
import com.pharmacyintel.model.*;
import com.pharmacyintel.parser.*;
import com.pharmacyintel.report.ExcelExporter;

import java.io.File;
import java.util.*;

/**
 * Orchestrates the full pipeline: BCV fetch → Parse files → Consolidate →
 * Analyze → Export.
 */
public class SyncOrchestrator {

    public interface ProgressListener {
        void onProgress(String stage, int percent);

        void onError(String stage, String message);

        void onComplete(File excelFile, ConsolidationEngine engine);
    }

    private final BcvService bcvService = new BcvService();
    private final ConsolidationEngine engine = new ConsolidationEngine();
    private final ExcelExporter exporter = new ExcelExporter();
    private ProgressListener listener;

    public void setProgressListener(ProgressListener listener) {
        this.listener = listener;
    }

    /**
     * Execute full pipeline.
     *
     * @param supplierFiles map of Supplier → File
     * @param outputDir     directory for Excel output
     * @param fetchBcv      whether to fetch BCV rate online
     */
    public void execute(Map<Supplier, File> supplierFiles, File outputDir, boolean fetchBcv) {
        try {
            // Phase 1: BCV Rate
            reportProgress("Obteniendo tasa BCV...", 5);
            if (fetchBcv) {
                double rate = bcvService.fetchRate();
                if (rate <= 0) {
                    double currentRate = GlobalConfig.getInstance().getBcvRate();
                    if (currentRate <= 1.0) {
                        reportError("BCV", "No se pudo obtener la tasa BCV. Configure la tasa manual.");
                    } else {
                        reportProgress("Usando tasa manual: " + String.format("%.4f", currentRate), 10);
                    }
                }
            }
            double bcvRate = GlobalConfig.getInstance().getBcvRate();
            reportProgress("Tasa BCV: " + String.format("%.4f", bcvRate), 10);

            // Phase 2: Parse supplier files
            Map<Supplier, List<SupplierProduct>> supplierData = new EnumMap<>(Supplier.class);
            int fileIdx = 0;
            int totalFiles = supplierFiles.size();

            for (var entry : supplierFiles.entrySet()) {
                Supplier supplier = entry.getKey();
                File file = entry.getValue();
                fileIdx++;

                reportProgress("Procesando " + supplier.getDisplayName() + "...", 10 + (fileIdx * 60 / totalFiles));

                try {
                    SupplierParser parser = createParser(supplier);
                    List<SupplierProduct> products = parser.parse(file);

                    // Convert Bs prices to USD for suppliers that report in Bs
                    if (isSupplierInBs(supplier) && bcvRate > 1) {
                        for (SupplierProduct sp : products) {
                            sp.setBasePrice(sp.getBasePrice() / bcvRate);
                            sp.recalcNet(); // Recalculate netPrice after Bs→USD conversion
                        }
                        reportProgress(supplier.getDisplayName() + ": " + products.size()
                                + " productos (convertidos de Bs a USD)", 10 + (fileIdx * 60 / totalFiles));
                    } else {
                        reportProgress(supplier.getDisplayName() + ": " + products.size() + " productos",
                                10 + (fileIdx * 60 / totalFiles));
                    }

                    supplierData.put(supplier, products);
                } catch (Exception e) {
                    reportError(supplier.getDisplayName(), "Error: " + e.getMessage());
                }
            }

            // Phase 3: Consolidate and analyze (Full Outer Join)
            reportProgress("Consolidando datos (Full Outer Join)...", 75);
            double margin = GlobalConfig.getInstance().getTargetMarginPct();
            engine.process(supplierData, margin);

            reportProgress("Análisis: " + engine.getTotalProducts() + " productos, "
                    + engine.getComparableProducts() + " comparables", 85);

            // Phase 4: Export to Excel
            reportProgress("Generando reporte Excel...", 90);
            File excelFile = exporter.export(engine.getMasterCatalog(),
                    GlobalConfig.getInstance().getBcvRate(), outputDir);

            reportProgress("¡Reporte generado exitosamente!", 100);

            if (listener != null) {
                listener.onComplete(excelFile, engine);
            }

        } catch (Exception e) {
            reportError("General", "Error crítico: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Returns true for suppliers whose files report prices in Bs (Bolívares).
     */
    private boolean isSupplierInBs(Supplier supplier) {
        return supplier == Supplier.NENA || supplier == Supplier.F24;
    }

    public ConsolidationEngine getEngine() {
        return engine;
    }

    private SupplierParser createParser(Supplier supplier) {
        return switch (supplier) {
            case DROACTIVA -> new DroactivaParser();
            case DROMARKO -> new DromarkoParser();
            case COBECA -> new CobecaParser();
            case NENA -> new NenaParser();
            case F24 -> new F24Parser();
            case P365 -> new GenericExcelParser(Supplier.P365);
        };
    }

    private void reportProgress(String stage, int percent) {
        if (listener != null)
            listener.onProgress(stage, percent);
    }

    private void reportError(String stage, String message) {
        if (listener != null)
            listener.onError(stage, message);
    }
}
