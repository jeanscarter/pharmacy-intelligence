package com.pharmacyintel.ui;

import com.pharmacyintel.engine.ConsolidationEngine;
import com.pharmacyintel.model.*;
import net.miginfocom.swing.MigLayout;
import org.jfree.chart.ChartPanel;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Chart carousel dialog: modal window with CardLayout to navigate
 * through analytics charts at full size.
 */
public class ChartCarouselDialog extends JDialog {

    private static final Color BG = new Color(30, 33, 40);
    private static final Color CARD_BG = new Color(40, 44, 52);
    private static final Color ACCENT = new Color(100, 160, 255);

    private final CardLayout cardLayout;
    private final JPanel cardsPanel;
    private final JLabel titleLabel;
    private final List<String> chartTitles = new ArrayList<>();
    private int currentIndex = 0;

    public ChartCarouselDialog(Frame owner, ConsolidationEngine engine) {
        super(owner, "📊 Gráficos de Análisis", true);
        setSize(900, 700);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getContentPane().setBackground(BG);
        setLayout(new MigLayout("insets 12, fill, wrap", "[grow]", "[]8[grow]8[]"));

        // Title label (top)
        titleLabel = new JLabel("", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(Color.WHITE);
        add(titleLabel, "growx, h 36!");

        // Card panel with charts
        cardLayout = new CardLayout();
        cardsPanel = new JPanel(cardLayout);
        cardsPanel.setBackground(BG);

        // Build charts
        buildCharts(engine);

        add(cardsPanel, "grow");

        // Navigation panel (bottom)
        JPanel navPanel = new JPanel(new MigLayout("insets 8, fillx", "push[]24[]push", ""));
        navPanel.setOpaque(false);

        JButton prevBtn = createNavButton("⬅ Anterior");
        prevBtn.addActionListener(e -> navigatePrev());
        navPanel.add(prevBtn);

        JButton nextBtn = createNavButton("Siguiente ➡");
        nextBtn.addActionListener(e -> navigateNext());
        navPanel.add(nextBtn);

        add(navPanel, "growx, h 50!");

        // Show first chart
        if (!chartTitles.isEmpty()) {
            titleLabel.setText(chartTitles.get(0));
        }
    }

    private void buildCharts(ConsolidationEngine engine) {
        // Chart 1: Average Price by Supplier
        Map<Supplier, Double> avgPrices = engine.getAveragePriceBySupplier();
        addChart("Precio Promedio por Droguería",
                PharmacyChartFactory.createBarChart("Precio Promedio por Droguería", avgPrices, "USD"));

        // Chart 2: Market Position (Wins)
        Map<Supplier, Integer> wins = engine.getWinCountBySupplier();
        addChart("Posición de Mercado (Victorias)",
                PharmacyChartFactory.createHorizontalBarChart("Posición de Mercado (Victorias)", wins));

        // Chart 3: Inventory Summary
        Map<Supplier, Integer> stock = engine.getTotalStockBySupplier();
        addChart("Inventario Total por Droguería",
                PharmacyChartFactory.createBarChart("Inventario Total por Droguería", stock, "Unidades"));

        // Chart 4: Offers (Pie)
        Map<Supplier, Integer> offers = engine.getOfferCountBySupplier();
        addChart("Productos con Oferta",
                PharmacyChartFactory.createPieChart("Productos con Oferta", offers));

        // Chart 5: Base vs Offer Price
        Map<Supplier, double[]> baseVsOffer = engine.getBasePriceVsOfferPrice();
        addChart("Precio Base vs Precio Neto",
                PharmacyChartFactory.createGroupedBarChart("Precio Base vs Precio Neto", baseVsOffer));

        // Chart 6: Win Distribution (Pie)
        addChart("Distribución de Victorias",
                PharmacyChartFactory.createPieChart("Distribución de Victorias", wins));
    }

    private void addChart(String title, ChartPanel chartPanel) {
        String cardName = "chart_" + chartTitles.size();
        chartTitles.add(title);

        RoundedPanel wrapper = new RoundedPanel(14);
        wrapper.setLayout(new BorderLayout());
        wrapper.setBackground(CARD_BG);
        wrapper.add(chartPanel, BorderLayout.CENTER);

        cardsPanel.add(wrapper, cardName);
    }

    private void navigateNext() {
        if (chartTitles.isEmpty())
            return;
        currentIndex = (currentIndex + 1) % chartTitles.size();
        cardLayout.show(cardsPanel, "chart_" + currentIndex);
        titleLabel.setText(chartTitles.get(currentIndex));
    }

    private void navigatePrev() {
        if (chartTitles.isEmpty())
            return;
        currentIndex = (currentIndex - 1 + chartTitles.size()) % chartTitles.size();
        cardLayout.show(cardsPanel, "chart_" + currentIndex);
        titleLabel.setText(chartTitles.get(currentIndex));
    }

    private JButton createNavButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setBackground(ACCENT);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(180, 40));
        return btn;
    }
}
