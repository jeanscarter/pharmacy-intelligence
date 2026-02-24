package com.pharmacyintel.ui;

import com.pharmacyintel.engine.ConsolidationEngine;
import com.pharmacyintel.model.MasterProduct;
import com.pharmacyintel.model.Supplier;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * Executive Summary Panel — "El Foco"
 * Shows 4 strategic KPI cards answering key business questions:
 * 1. Best price supplier (most wins)
 * 2. Worst price supplier (most losses)
 * 3. Best discount supplier
 * 4. Gap opportunity (products missing from DroActiva)
 */
public class ExecutiveSummaryPanel extends JPanel {

    private static final Color CARD_BG = new Color(40, 44, 52);
    private static final Color WIN_COLOR = new Color(39, 174, 96);
    private static final Color LOSS_COLOR = new Color(235, 87, 87);
    private static final Color DISCOUNT_COLOR = new Color(100, 160, 255);
    private static final Color GAP_COLOR = new Color(251, 188, 4);
    private static final Font TITLE_FONT = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font VALUE_FONT = new Font("Segoe UI", Font.BOLD, 20);
    private static final Font DETAIL_FONT = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font ICON_FONT = new Font("Segoe UI Emoji", Font.PLAIN, 28);

    public ExecutiveSummaryPanel(ConsolidationEngine engine) {
        setLayout(new MigLayout("insets 4 0 4 0, fillx", "[grow][grow][grow][grow]", "[]"));
        setOpaque(false);

        // --- Card 1: Best Price Supplier ---
        Supplier bestPrice = engine.getSupplierWithMostWins();
        Map<Supplier, Integer> wins = engine.getWinCountBySupplier();
        int winCount = bestPrice != null ? wins.getOrDefault(bestPrice, 0) : 0;
        int totalProducts = engine.getTotalProducts();
        String winPct = totalProducts > 0 ? String.format("%.0f%%", (winCount * 100.0 / totalProducts)) : "—";

        add(createKpiCard(
                "🏆", "MEJOR PRECIO",
                bestPrice != null ? bestPrice.getDisplayName() : "N/A",
                winCount + " victorias (" + winPct + ")",
                bestPrice != null ? bestPrice.getColor() : WIN_COLOR,
                WIN_COLOR), "grow");

        // --- Card 2: Worst Price Supplier ---
        Supplier worstPrice = engine.getSupplierWithMostLosses();
        int lossCount = 0;
        if (worstPrice != null) {
            for (MasterProduct mp : engine.getMasterProductList()) {
                if (worstPrice == mp.getWorstPriceSupplier())
                    lossCount++;
            }
        }
        String lossPct = totalProducts > 0 ? String.format("%.0f%%", (lossCount * 100.0 / totalProducts)) : "—";

        add(createKpiCard(
                "⚠️", "PEOR PRECIO",
                worstPrice != null ? worstPrice.getDisplayName() : "N/A",
                lossCount + " productos más caros (" + lossPct + ")",
                worstPrice != null ? worstPrice.getColor() : LOSS_COLOR,
                LOSS_COLOR), "grow");

        // --- Card 3: Best Discount Supplier ---
        Supplier bestDiscount = engine.getSupplierWithBestAvgDiscount();
        Map<Supplier, Integer> offers = engine.getOfferCountBySupplier();
        int offerCount = bestDiscount != null ? offers.getOrDefault(bestDiscount, 0) : 0;

        add(createKpiCard(
                "💎", "MEJOR DESCUENTO",
                bestDiscount != null ? bestDiscount.getDisplayName() : "N/A",
                offerCount + " productos con oferta",
                bestDiscount != null ? bestDiscount.getColor() : DISCOUNT_COLOR,
                DISCOUNT_COLOR), "grow");

        // --- Card 4: Gap Opportunity ---
        List<MasterProduct> gaps = engine.getGapProducts(Supplier.DROACTIVA);
        int gapCount = gaps.size();
        Map<Supplier, Integer> gapSummary = engine.getGapSummaryBySupplier();
        // Find which supplier contributes the most gaps
        Supplier topGapSupplier = gapSummary.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .filter(e -> e.getValue() > 0)
                .map(Map.Entry::getKey)
                .orElse(null);
        String gapDetail = topGapSupplier != null
                ? topGapSupplier.getDisplayName() + " tiene " + gapSummary.get(topGapSupplier) + " exclusivos"
                : "Sin brechas detectadas";

        add(createKpiCard(
                "🔔", "OPORTUNIDAD",
                gapCount + " productos",
                gapDetail,
                GAP_COLOR,
                GAP_COLOR), "grow");
    }

    private JPanel createKpiCard(String icon, String title, String value, String detail,
            Color valueColor, Color accentColor) {
        RoundedPanel card = new RoundedPanel(14);
        card.setLayout(new MigLayout("insets 12 16 12 16, fillx", "[]12[grow]", "[]2[]2[]"));
        card.setBackground(CARD_BG);

        // Left: icon
        JLabel iconLabel = new JLabel(icon);
        iconLabel.setFont(ICON_FONT);
        card.add(iconLabel, "span 1 3, ay center");

        // Right: title
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(TITLE_FONT);
        titleLabel.setForeground(new Color(150, 160, 175));
        card.add(titleLabel, "wrap");

        // Right: value
        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(VALUE_FONT);
        valueLabel.setForeground(valueColor);
        card.add(valueLabel, "wrap");

        // Right: detail
        JLabel detailLabel = new JLabel(detail);
        detailLabel.setFont(DETAIL_FONT);
        detailLabel.setForeground(new Color(120, 130, 145));
        card.add(detailLabel);

        // Bottom accent line
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 0, 0, 0),
                BorderFactory.createMatteBorder(0, 0, 2, 0, accentColor)));

        return card;
    }
}
