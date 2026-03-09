package com.pharmacyintel.ui;

import com.pharmacyintel.model.MasterProduct;
import com.pharmacyintel.model.Supplier;
import com.pharmacyintel.model.SupplierProduct;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * Executive Summary Panel — Dynamic KPI cards.
 * Updates in real-time based on the active filter in ProductTablePanel.
 */
public class ExecutiveSummaryPanel extends JPanel {

        private static final Color CARD_BG = new Color(40, 44, 52);
        private static final Color WIN_COLOR = new Color(39, 174, 96);
        private static final Color LOSS_COLOR = new Color(235, 87, 87);
        private static final Color DISCOUNT_COLOR = new Color(100, 160, 255);
        private static final Color GAP_COLOR = new Color(251, 188, 4);
        private static final Font TITLE_FONT = new Font("Segoe UI Emoji", Font.PLAIN, 11);
        private static final Font VALUE_FONT = new Font("Segoe UI", Font.BOLD, 20);
        private static final Font DETAIL_FONT = new Font("Segoe UI", Font.PLAIN, 11);
        private static final Font ICON_FONT = new Font("Segoe UI Emoji", Font.PLAIN, 28);

        // Mutable KPI label references
        private JLabel icon1, title1, value1, detail1;
        private JLabel icon2, title2, value2, detail2;
        private JLabel icon3, title3, value3, detail3;
        private JLabel icon4, title4, value4, detail4;
        private JPanel card1, card2, card3, card4;

        public ExecutiveSummaryPanel() {
                setLayout(new MigLayout("insets 4 0 4 0, fillx", "[grow][grow][grow][grow]", "[]"));
                setOpaque(false);

                // Build 4 empty KPI cards
                card1 = buildKpiCard(1);
                card2 = buildKpiCard(2);
                card3 = buildKpiCard(3);
                card4 = buildKpiCard(4);

                add(card1, "grow");
                add(card2, "grow");
                add(card3, "grow");
                add(card4, "grow");
        }

        private JPanel buildKpiCard(int idx) {
                RoundedPanel card = new RoundedPanel(14);
                card.setLayout(new MigLayout("insets 12 16 12 16, fillx", "[]12[grow]", "[]2[]2[]"));
                card.setBackground(CARD_BG);

                JLabel iconLbl = new JLabel("");
                iconLbl.setFont(ICON_FONT);
                card.add(iconLbl, "span 1 3, ay center");

                JLabel titleLbl = new JLabel("");
                titleLbl.setFont(TITLE_FONT);
                titleLbl.setForeground(new Color(150, 160, 175));
                card.add(titleLbl, "wrap");

                JLabel valueLbl = new JLabel("—");
                valueLbl.setFont(VALUE_FONT);
                valueLbl.setForeground(Color.WHITE);
                card.add(valueLbl, "wrap");

                JLabel detailLbl = new JLabel("");
                detailLbl.setFont(DETAIL_FONT);
                detailLbl.setForeground(new Color(120, 130, 145));
                card.add(detailLbl);

                Color accent;
                switch (idx) {
                        case 1:
                                icon1 = iconLbl;
                                title1 = titleLbl;
                                value1 = valueLbl;
                                detail1 = detailLbl;
                                accent = WIN_COLOR;
                                break;
                        case 2:
                                icon2 = iconLbl;
                                title2 = titleLbl;
                                value2 = valueLbl;
                                detail2 = detailLbl;
                                accent = LOSS_COLOR;
                                break;
                        case 3:
                                icon3 = iconLbl;
                                title3 = titleLbl;
                                value3 = valueLbl;
                                detail3 = detailLbl;
                                accent = DISCOUNT_COLOR;
                                break;
                        default:
                                icon4 = iconLbl;
                                title4 = titleLbl;
                                value4 = valueLbl;
                                detail4 = detailLbl;
                                accent = GAP_COLOR;
                                break;
                }

                card.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createEmptyBorder(0, 0, 0, 0),
                                BorderFactory.createMatteBorder(0, 0, 2, 0, accent)));
                return card;
        }

        /**
         * Update KPIs dynamically based on the visible products and the current filter.
         */
        public void updateMetrics(List<MasterProduct> visibleProducts, String filterName, boolean stockOnly) {
                if (visibleProducts == null || visibleProducts.isEmpty()) {
                        clearCards();
                        return;
                }

                // Dedicated mode for "Peor Neto DroActiva"
                if ("Peor Neto DroActiva".equals(filterName)) {
                        updateForPeorNeto(visibleProducts);
                        revalidate();
                        repaint();
                        return;
                }

                // Detect if a specific supplier is selected
                Supplier targetSupplier = extractSupplier(filterName);

                if (targetSupplier != null) {
                        updateForSupplier(visibleProducts, targetSupplier, stockOnly);
                } else {
                        updateGlobal(visibleProducts, stockOnly);
                }

                revalidate();
                repaint();
        }

        // ============ GLOBAL mode (filter = "Todos") ============
        private void updateGlobal(List<MasterProduct> products, boolean stockOnly) {
                int total = products.size();

                // KPI 1: Best Price Supplier (most wins)
                Map<Supplier, Integer> wins = new java.util.EnumMap<>(Supplier.class);
                for (Supplier s : Supplier.values())
                        wins.put(s, 0);
                for (MasterProduct mp : products) {
                        if (mp.getWinnerSupplier(stockOnly) != null)
                                wins.merge(mp.getWinnerSupplier(stockOnly), 1, Integer::sum);
                }
                Supplier bestPrice = wins.entrySet().stream()
                                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
                int winCount = bestPrice != null ? wins.getOrDefault(bestPrice, 0) : 0;
                String winPct = total > 0 ? String.format("%.0f%%", (winCount * 100.0 / total)) : "—";

                setCard(1, " 🏆 ", " MEJOR PRECIO ",
                                bestPrice != null ? bestPrice.getDisplayName() : "N/A",
                                winCount + " victorias (" + winPct + ")",
                                bestPrice != null ? bestPrice.getColor() : WIN_COLOR, WIN_COLOR);

                // KPI 2: Worst Price Supplier (most losses)
                Map<Supplier, Integer> losses = new java.util.EnumMap<>(Supplier.class);
                for (Supplier s : Supplier.values())
                        losses.put(s, 0);
                for (MasterProduct mp : products) {
                        if (mp.getWorstPriceSupplier(stockOnly) != null)
                                losses.merge(mp.getWorstPriceSupplier(stockOnly), 1, Integer::sum);
                }
                Supplier worstPrice = losses.entrySet().stream()
                                .filter(e -> e.getValue() > 0)
                                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
                int lossCount = worstPrice != null ? losses.getOrDefault(worstPrice, 0) : 0;
                String lossPct = total > 0 ? String.format("%.0f%%", (lossCount * 100.0 / total)) : "—";

                setCard(2, " ⚠️ ", " PEOR PRECIO ",
                                worstPrice != null ? worstPrice.getDisplayName() : "N/A",
                                lossCount + " productos más caros (" + lossPct + ")",
                                worstPrice != null ? worstPrice.getColor() : LOSS_COLOR, LOSS_COLOR);

                // KPI 3: Best Discount
                Map<Supplier, Integer> offers = new java.util.EnumMap<>(Supplier.class);
                for (MasterProduct mp : products) {
                        for (var entry : mp.getSupplierPrices().entrySet()) {
                                if (entry.getValue().hasDiscount()) {
                                        offers.merge(entry.getKey(), 1, Integer::sum);
                                }
                        }
                }
                Supplier bestDiscount = offers.entrySet().stream()
                                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
                int offerCount = bestDiscount != null ? offers.getOrDefault(bestDiscount, 0) : 0;

                setCard(3, " 💎 ", " MEJOR DESCUENTO ",
                                bestDiscount != null ? bestDiscount.getDisplayName() : "N/A",
                                offerCount + " productos con oferta",
                                bestDiscount != null ? bestDiscount.getColor() : DISCOUNT_COLOR, DISCOUNT_COLOR);

                // KPI 4: Total products
                long comparable = products.stream().filter(mp -> mp.getSupplierCount() >= 2).count();
                setCard(4, " 🔔 ", " OPORTUNIDAD ",
                                total + " productos",
                                comparable + " comparables con 2+ proveedores",
                                GAP_COLOR, GAP_COLOR);
        }

        // ============ SUPPLIER-SPECIFIC mode ============
        private void updateForSupplier(List<MasterProduct> products, Supplier target, boolean stockOnly) {
                int total = products.size();

                // KPI 1: Victories — products where this supplier wins
                int victories = 0;
                for (MasterProduct mp : products) {
                        if (target == mp.getWinnerSupplier(stockOnly))
                                victories++;
                }
                String victPct = total > 0 ? String.format("%.0f%%", (victories * 100.0 / total)) : "—";

                setCard(1, " 🏆 ", " VICTORIAS ",
                                String.valueOf(victories),
                                "Gané en " + victories + " de " + total + " (" + victPct + ")",
                                WIN_COLOR, WIN_COLOR);

                // KPI 2: Defeats — products where this supplier is the loser
                int defeats = 0;
                for (MasterProduct mp : products) {
                        if (target == mp.getWorstPriceSupplier(stockOnly))
                                defeats++;
                }
                String defPct = total > 0 ? String.format("%.0f%%", (defeats * 100.0 / total)) : "—";

                setCard(2, " ⚠️ ", " DERROTAS ",
                                String.valueOf(defeats),
                                "Perdí en " + defeats + " de " + total + " (" + defPct + ")",
                                LOSS_COLOR, LOSS_COLOR);

                // KPI 3: Active discounts for this supplier in filtered set
                int discountCount = 0;
                double totalDiscount = 0;
                for (MasterProduct mp : products) {
                        SupplierProduct sp = mp.getSupplierPrices().get(target);
                        if (sp != null && sp.hasDiscount()) {
                                discountCount++;
                                totalDiscount += sp.getOfferPct();
                        }
                }
                double avgDiscount = discountCount > 0 ? totalDiscount / discountCount : 0;

                setCard(3, " 💎 ", " DESCUENTOS ",
                                discountCount + " ofertas",
                                "Promedio: " + String.format("%.1f%%", avgDiscount),
                                DISCOUNT_COLOR, DISCOUNT_COLOR);

                // KPI 4: Average position of this supplier
                int posSum = 0;
                int posCount = 0;
                int maxProviders = 0;
                for (MasterProduct mp : products) {
                        int pos = stockOnly ? mp.getStockOnlyPositionForSupplier(target)
                                        : mp.getPositionForSupplier(target);
                        if (pos > 0) {
                                posSum += pos;
                                posCount++;
                                int sc = mp.getSupplierCount();
                                if (sc > maxProviders)
                                        maxProviders = sc;
                        }
                }
                double avgPos = posCount > 0 ? (double) posSum / posCount : 0;

                setCard(4, " 📊 ", " POSICIONAMIENTO ",
                                posCount > 0 ? String.format("%.1f de %d", avgPos, maxProviders) : "N/A",
                                posCount + " productos con precio",
                                GAP_COLOR, GAP_COLOR);
        }

        // ============ PEOR NETO DROACTIVA mode ============
        private void updateForPeorNeto(List<MasterProduct> products) {
                Supplier dro = Supplier.DROACTIVA;
                int total = products.size();

                // Card 1: Products where DroActiva has the WORST (highest) PV
                int worstPvCount = 0;
                for (MasterProduct mp : products) {
                        double pvDro = mp.getBasePriceForSupplier(dro);
                        if (pvDro <= 0)
                                continue;
                        boolean isWorst = true;
                        boolean hasComparison = false;
                        for (Supplier s : Supplier.values()) {
                                if (s == dro)
                                        continue;
                                double pvOther = mp.getBasePriceForSupplier(s);
                                if (pvOther > 0) {
                                        hasComparison = true;
                                        if (pvOther >= pvDro) {
                                                isWorst = false;
                                                break;
                                        }
                                }
                        }
                        if (isWorst && hasComparison)
                                worstPvCount++;
                }
                String pvPct = total > 0 ? String.format("%.0f%%", (worstPvCount * 100.0 / total)) : "—";
                setCard(1, " ⚠️ ", " PEOR PRECIO VENTA ",
                                String.valueOf(worstPvCount),
                                worstPvCount + " de " + total + " productos (" + pvPct + ")",
                                LOSS_COLOR, LOSS_COLOR);

                // Card 2: Products where DroActiva has the WORST (lowest) OF%
                int worstOfCount = 0;
                for (MasterProduct mp : products) {
                        double ofDro = mp.getOfferPctForSupplier(dro);
                        boolean isWorst = true;
                        boolean hasComparison = false;
                        for (Supplier s : Supplier.values()) {
                                if (s == dro)
                                        continue;
                                double ofOther = mp.getOfferPctForSupplier(s);
                                if (ofOther > 0) {
                                        hasComparison = true;
                                        if (ofOther <= ofDro) {
                                                isWorst = false;
                                                break;
                                        }
                                }
                        }
                        if (isWorst && hasComparison)
                                worstOfCount++;
                }
                String ofPct = total > 0 ? String.format("%.0f%%", (worstOfCount * 100.0 / total)) : "—";
                setCard(2, " 📉 ", " PEOR OFERTA ",
                                String.valueOf(worstOfCount),
                                worstOfCount + " de " + total + " productos (" + ofPct + ")",
                                new Color(255, 152, 0), new Color(255, 152, 0));

                // Card 3: DroActiva discounts active in this set
                int discountCount = 0;
                double totalDiscount = 0;
                for (MasterProduct mp : products) {
                        SupplierProduct sp = mp.getSupplierPrices().get(dro);
                        if (sp != null && sp.hasDiscount()) {
                                discountCount++;
                                totalDiscount += sp.getOfferPct();
                        }
                }
                double avgDiscount = discountCount > 0 ? totalDiscount / discountCount : 0;
                setCard(3, " 💎 ", " DESCUENTOS DROACTIVA ",
                                discountCount + " ofertas",
                                "Promedio: " + String.format("%.1f%%", avgDiscount),
                                DISCOUNT_COLOR, DISCOUNT_COLOR);

                // Card 4: Average positioning
                int posSum = 0, posCount = 0, maxProv = 0;
                for (MasterProduct mp : products) {
                        int pos = mp.getPositionForSupplier(dro);
                        if (pos > 0) {
                                posSum += pos;
                                posCount++;
                                int sc = mp.getSupplierCount();
                                if (sc > maxProv)
                                        maxProv = sc;
                        }
                }
                double avgPos = posCount > 0 ? (double) posSum / posCount : 0;
                setCard(4, " 📊 ", " POSICIONAMIENTO ",
                                posCount > 0 ? String.format("%.1f de %d", avgPos, maxProv) : "N/A",
                                posCount + " productos con precio",
                                GAP_COLOR, GAP_COLOR);
        }

        // ============ Helpers ============
        private void setCard(int idx, String icon, String title, String value, String detail,
                        Color valueColor, Color accentColor) {
                JLabel ic, ti, va, de;
                JPanel card;
                switch (idx) {
                        case 1:
                                ic = icon1;
                                ti = title1;
                                va = value1;
                                de = detail1;
                                card = card1;
                                break;
                        case 2:
                                ic = icon2;
                                ti = title2;
                                va = value2;
                                de = detail2;
                                card = card2;
                                break;
                        case 3:
                                ic = icon3;
                                ti = title3;
                                va = value3;
                                de = detail3;
                                card = card3;
                                break;
                        default:
                                ic = icon4;
                                ti = title4;
                                va = value4;
                                de = detail4;
                                card = card4;
                                break;
                }
                ic.setText(icon);
                ti.setText(title);
                va.setText(value);
                va.setForeground(valueColor);
                de.setText(detail);
                card.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createEmptyBorder(0, 0, 0, 0),
                                BorderFactory.createMatteBorder(0, 0, 2, 0, accentColor)));
        }

        private void clearCards() {
                setCard(1, " 🏆 ", " MEJOR PRECIO ", "—", "", WIN_COLOR, WIN_COLOR);
                setCard(2, " ⚠️ ", " PEOR PRECIO ", "—", "", LOSS_COLOR, LOSS_COLOR);
                setCard(3, " 💎 ", " MEJOR DESCUENTO ", "—", "", DISCOUNT_COLOR, DISCOUNT_COLOR);
                setCard(4, " 🔔 ", " OPORTUNIDAD ", "—", "", GAP_COLOR, GAP_COLOR);
        }

        /**
         * Extract a Supplier from the filter name, or null if "Todos".
         */
        private Supplier extractSupplier(String filterName) {
                if (filterName == null || filterName.equals("Todos"))
                        return null;
                String name = filterName;
                if (name.startsWith("Ganador: "))
                        name = name.substring("Ganador: ".length());
                else if (name.startsWith("Perdedor: "))
                        name = name.substring("Perdedor: ".length());

                for (Supplier s : Supplier.values()) {
                        if (s.getDisplayName().equals(name))
                                return s;
                }
                return null;
        }
}
