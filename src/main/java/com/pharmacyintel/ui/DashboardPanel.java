package com.pharmacyintel.ui;

import com.pharmacyintel.engine.ConsolidationEngine;
import com.pharmacyintel.model.GlobalConfig;
import com.pharmacyintel.model.MasterProduct;
import com.pharmacyintel.report.ExcelExporter;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;

/**
 * Main dashboard: summary cards + expanded product table + action buttons.
 * Charts are accessed via a dedicated "Ver Gráficos" button that opens
 * ChartCarouselDialog.
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

        // --- Executive Summary Panel ---
        ExecutiveSummaryPanel execSummary = new ExecutiveSummaryPanel(engine);
        add(execSummary, "growx, h 90!");

        // --- Product Table (expanded) ---
        List<MasterProduct> products = engine.getMasterProductList();
        ProductTablePanel tablePanel = new ProductTablePanel(products);
        add(tablePanel, "grow");

        // --- Button Bar ---
        JPanel buttonBar = new JPanel(new MigLayout("insets 8, fillx", "push[]16[]16[]push", ""));
        buttonBar.setOpaque(false);

        // Charts button (big and flashy)
        JButton chartsBtn = createStyledButton("📊  Ver Gráficos de Análisis", new Color(63, 81, 181));
        chartsBtn.setPreferredSize(new Dimension(320, 48));
        chartsBtn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        chartsBtn.addActionListener(e -> {
            Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
            ChartCarouselDialog dialog = new ChartCarouselDialog(owner, engine);
            dialog.setVisible(true);
        });
        buttonBar.add(chartsBtn);

        JButton exportBtn = createStyledButton("📥  Exportar Excel", new Color(52, 168, 83));
        exportBtn.addActionListener(e -> exportExcel(engine));
        buttonBar.add(exportBtn);

        JButton refreshBtn = createStyledButton("🔄  Recalcular con Nuevo Margen", ACCENT);
        refreshBtn.addActionListener(e -> {
            double margin = GlobalConfig.getInstance().getTargetMarginPct();
            engine.simulateMargin(margin);
            Toast.show("Margen recalculado al " + String.format("%.0f%%", margin), Toast.Type.SUCCESS);
            removeAll();
            DashboardPanel newDash = new DashboardPanel(engine);
            setLayout(new BorderLayout());
            add(newDash, BorderLayout.CENTER);
            revalidate();
            repaint();
        });
        buttonBar.add(refreshBtn);

        add(buttonBar, "growx, h 60!");
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
