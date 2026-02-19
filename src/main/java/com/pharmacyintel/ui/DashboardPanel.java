package com.pharmacyintel.ui;

import com.pharmacyintel.engine.ConsolidationEngine;
import com.pharmacyintel.model.GlobalConfig;
import com.pharmacyintel.model.MasterProduct;
import com.pharmacyintel.model.Supplier;
import com.pharmacyintel.report.ExcelExporter;
import net.miginfocom.swing.MigLayout;
import org.jfree.chart.ChartPanel;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Main dashboard with 6 analytics panels + product table + export button.
 */
public class DashboardPanel extends JPanel {

    private static final Color BG = new Color(30, 33, 40);
    private static final Color CARD_BG = new Color(40, 44, 52);
    private static final Color ACCENT = new Color(100, 160, 255);

    public DashboardPanel(ConsolidationEngine engine) {
        setLayout(new MigLayout("insets 16, fill, wrap", "[grow]", "[]8[]8[grow]8[]"));
        setBackground(BG);

        // --- Summary Cards Row ---
        JPanel summaryRow = new JPanel(new MigLayout("insets 0, fillx", "[grow][grow][grow][grow]", ""));
        summaryRow.setOpaque(false);

        summaryRow.add(createSummaryCard("📦 Total Productos",
                String.valueOf(engine.getTotalProducts()), ACCENT), "grow");
        summaryRow.add(createSummaryCard("📊 Productos Comparables",
                String.valueOf(engine.getComparableProducts()), new Color(52, 168, 83)), "grow");
        summaryRow.add(createSummaryCard("💱 Tasa BCV",
                String.format("%.4f", GlobalConfig.getInstance().getBcvRate()), new Color(251, 188, 4)), "grow");
        summaryRow.add(createSummaryCard("📈 Margen Objetivo",
                String.format("%.0f%%", GlobalConfig.getInstance().getTargetMarginPct()), new Color(171, 71, 188)),
                "grow");

        add(summaryRow, "growx, h 80!");

        // --- Charts Grid (3x2) ---
        JPanel chartsGrid = new JPanel(new MigLayout("insets 0, gap 8", "[grow][grow][grow]", "[grow][grow]"));
        chartsGrid.setOpaque(false);

        // Chart 1: Average Price by Supplier
        Map<Supplier, Double> avgPrices = engine.getAveragePriceBySupplier();
        ChartPanel avgPriceChart = PharmacyChartFactory.createBarChart(
                "Precio Promedio por Droguería", avgPrices, "USD");
        chartsGrid.add(wrapChart(avgPriceChart), "grow");

        // Chart 2: Market Position (Wins)
        Map<Supplier, Integer> wins = engine.getWinCountBySupplier();
        ChartPanel winsChart = PharmacyChartFactory.createHorizontalBarChart(
                "Posición de Mercado (Victorias)", wins);
        chartsGrid.add(wrapChart(winsChart), "grow");

        // Chart 3: Inventory Summary
        Map<Supplier, Integer> stock = engine.getTotalStockBySupplier();
        ChartPanel stockChart = PharmacyChartFactory.createBarChart(
                "Inventario Total por Droguería", stock, "Unidades");
        chartsGrid.add(wrapChart(stockChart), "grow");

        // Chart 4: Offers (Pie)
        Map<Supplier, Integer> offers = engine.getOfferCountBySupplier();
        ChartPanel offersChart = PharmacyChartFactory.createPieChart(
                "Productos con Oferta", offers);
        chartsGrid.add(wrapChart(offersChart), "grow");

        // Chart 5: Base vs Offer Price
        Map<Supplier, double[]> baseVsOffer = engine.getBasePriceVsOfferPrice();
        ChartPanel baseVsOfferChart = PharmacyChartFactory.createGroupedBarChart(
                "Precio Base vs Precio Neto", baseVsOffer);
        chartsGrid.add(wrapChart(baseVsOfferChart), "grow");

        // Chart 6: Win distribution by supplier (alternate view)
        ChartPanel winsPieChart = PharmacyChartFactory.createPieChart(
                "Distribución de Victorias", wins);
        chartsGrid.add(wrapChart(winsPieChart), "grow");

        add(chartsGrid, "grow, h 45%!");

        // --- Product Table ---
        List<MasterProduct> products = engine.getMasterProductList();
        ProductTablePanel tablePanel = new ProductTablePanel(products);
        add(tablePanel, "grow");

        // --- Export Button ---
        JPanel buttonBar = new JPanel(new MigLayout("insets 8, fillx", "push[]16[]push", ""));
        buttonBar.setOpaque(false);

        JButton exportBtn = createStyledButton("📥  Exportar Excel", new Color(52, 168, 83));
        exportBtn.addActionListener(e -> exportExcel(engine));
        buttonBar.add(exportBtn);

        JButton refreshBtn = createStyledButton("🔄  Recalcular con Nuevo Margen", ACCENT);
        refreshBtn.addActionListener(e -> {
            double margin = GlobalConfig.getInstance().getTargetMarginPct();
            engine.simulateMargin(margin);
            Toast.show("Margen recalculado al " + String.format("%.0f%%", margin), Toast.Type.SUCCESS);
            // Refresh table
            removeAll();
            DashboardPanel newDash = new DashboardPanel(engine);
            setLayout(new BorderLayout());
            add(newDash, BorderLayout.CENTER);
            revalidate();
            repaint();
        });
        buttonBar.add(refreshBtn);

        add(buttonBar, "growx, h 56!");
    }

    private JPanel wrapChart(ChartPanel chartPanel) {
        RoundedPanel wrapper = new RoundedPanel(14);
        wrapper.setLayout(new BorderLayout());
        wrapper.setBackground(CARD_BG);
        wrapper.add(chartPanel, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel createSummaryCard(String label, String value, Color accentColor) {
        RoundedPanel card = new RoundedPanel(14);
        card.setLayout(new MigLayout("insets 12 16 12 16, wrap", "[grow]", "[]4[]"));
        card.setBackground(CARD_BG);

        JLabel titleLabel = new JLabel(label);
        titleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        titleLabel.setForeground(new Color(150, 160, 175));
        card.add(titleLabel);

        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        valueLabel.setForeground(accentColor);
        card.add(valueLabel);

        return card;
    }

    private JButton createStyledButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(300, 42));
        return btn;
    }

    private void exportExcel(ConsolidationEngine engine) {
        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
        chooser.setDialogTitle("Seleccionar carpeta de destino");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                ExcelExporter exporter = new ExcelExporter();
                File output = exporter.export(engine.getMasterCatalog(),
                        GlobalConfig.getInstance().getBcvRate(), chooser.getSelectedFile());
                Toast.show("Excel generado: " + output.getName(), Toast.Type.SUCCESS);

                // Open the file
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(output);
                }
            } catch (Exception ex) {
                Toast.show("Error al exportar: " + ex.getMessage(), Toast.Type.ERROR);
                ex.printStackTrace();
            }
        }
    }
}
