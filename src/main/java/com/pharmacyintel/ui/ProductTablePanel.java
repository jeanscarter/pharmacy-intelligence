package com.pharmacyintel.ui;

import com.pharmacyintel.model.*;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.*;
import java.awt.*;
import java.util.List;

/**
 * Searchable/filterable JTable displaying the consolidated product catalog.
 * Columns grouped:
 * [Datos Base] Código, Descripción, #Prov
 * [RESUMEN PRECIO VENTA] 6 cols (basePrice per supplier)
 * [RESUMEN OFERTA] 6 cols (offerPct per supplier)
 * [RESUMEN PRECIO CON OF] 6 cols (netPrice per supplier, winner in green)
 * [POSICION] 6 cols (ranking 1..6)
 * [Análisis Final] Ganador, DIF%, Utilidad Simulada
 */
public class ProductTablePanel extends JPanel {

    private static final Color TABLE_BG = new Color(38, 42, 52);
    private static final Color WINNER_BG = new Color(39, 174, 96, 50);
    private static final Supplier[] SUPPLIERS = Supplier.values();
    private static final int SUPPLIER_COUNT = SUPPLIERS.length;

    // Column group start indices
    private static final int COL_PRECIO_VENTA_START = 3; // 6 basePrice columns
    private static final int COL_OFERTA_START = 3 + SUPPLIER_COUNT; // 6 offerPct columns
    private static final int COL_PRECIO_CON_OF_START = 3 + SUPPLIER_COUNT * 2; // 6 netPrice columns
    private static final int COL_POSICION_START = 3 + SUPPLIER_COUNT * 3; // 6 position columns
    private static final int COL_ANALISIS_START = 3 + SUPPLIER_COUNT * 4; // Ganador, DIF%, Utilidad
    private static final int TOTAL_COLS = 3 + SUPPLIER_COUNT * 4 + 3;

    private static final String FILTER_ALL = "Todos";
    private static final String FILTER_PREFIX_WINNER = "Ganador: ";
    private static final String FILTER_PREFIX_LOSER = "Perdedor: ";

    private final JTable table;
    private final DefaultTableModel model;
    private final TableRowSorter<DefaultTableModel> sorter;
    private final JTextField searchField;
    private final JCheckBox stockOnlyCheck;
    private final JComboBox<String> strategyFilter;
    private final JLabel countLabel;
    private final List<MasterProduct> products;

    public ProductTablePanel(List<MasterProduct> products) {
        this.products = products;
        setLayout(new MigLayout("insets 0, fill, wrap", "[grow]", "[]4[grow]"));
        setOpaque(false);

        // Toolbar
        JPanel toolbar = new JPanel(new MigLayout("insets 8, fillx", "[]8[grow]12[]12[]12[]", ""));
        toolbar.setOpaque(false);

        JLabel searchIcon = new JLabel("🔍");
        searchIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
        toolbar.add(searchIcon);

        searchField = new JTextField();
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        searchField.putClientProperty("JTextField.placeholderText", "Buscar por código o descripción...");
        toolbar.add(searchField, "growx");

        strategyFilter = new JComboBox<>();
        strategyFilter.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        strategyFilter.setPreferredSize(new Dimension(200, 28));
        strategyFilter.addItem(FILTER_ALL);
        for (Supplier s : SUPPLIERS) {
            strategyFilter.addItem(FILTER_PREFIX_WINNER + s.getDisplayName());
        }
        for (Supplier s : SUPPLIERS) {
            strategyFilter.addItem(FILTER_PREFIX_LOSER + s.getDisplayName());
        }
        strategyFilter.putClientProperty("JComponent.roundRect", true);
        toolbar.add(strategyFilter);

        stockOnlyCheck = new JCheckBox("Solo con stock");
        stockOnlyCheck.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        stockOnlyCheck.setForeground(Color.WHITE);
        stockOnlyCheck.setOpaque(false);
        toolbar.add(stockOnlyCheck);

        countLabel = new JLabel(products.size() + " productos");
        countLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        countLabel.setForeground(new Color(100, 160, 255));
        toolbar.add(countLabel);

        add(toolbar, "growx");

        // Build column names
        String[] columnNames = new String[TOTAL_COLS];
        int col = 0;
        // [Datos Base]
        columnNames[col++] = "Código";
        columnNames[col++] = "Descripción";
        columnNames[col++] = "#Prov";
        // [RESUMEN PRECIO VENTA]
        for (Supplier s : SUPPLIERS) {
            columnNames[col++] = "PV " + s.getDisplayName();
        }
        // [RESUMEN OFERTA]
        for (Supplier s : SUPPLIERS) {
            columnNames[col++] = "OF " + s.getDisplayName();
        }
        // [RESUMEN PRECIO CON OF]
        for (Supplier s : SUPPLIERS) {
            columnNames[col++] = "NET " + s.getDisplayName();
        }
        // [POSICION]
        for (Supplier s : SUPPLIERS) {
            columnNames[col++] = "P# " + s.getDisplayName();
        }
        // [Análisis Final]
        columnNames[col++] = "Ganador";
        columnNames[col++] = "DIF%";
        columnNames[col++] = "Utilidad Sim.";

        // Build table data
        Object[][] data = new Object[products.size()][TOTAL_COLS];
        for (int i = 0; i < products.size(); i++) {
            MasterProduct mp = products.get(i);
            col = 0;

            // [Datos Base]
            data[i][col++] = mp.getBarcode();
            data[i][col++] = mp.getDescription() != null ? mp.getDescription() : "";
            data[i][col++] = mp.getSupplierCount();

            // [RESUMEN PRECIO VENTA]
            for (Supplier s : SUPPLIERS) {
                double bp = mp.getBasePriceForSupplier(s);
                data[i][col++] = bp > 0 ? bp : null;
            }

            // [RESUMEN OFERTA]
            for (Supplier s : SUPPLIERS) {
                double op = mp.getOfferPctForSupplier(s);
                data[i][col++] = op > 0 ? op : null;
            }

            // [RESUMEN PRECIO CON OF]
            for (Supplier s : SUPPLIERS) {
                double np = mp.getNetPriceForSupplier(s);
                data[i][col++] = np > 0 ? np : null;
            }

            // [POSICION]
            for (Supplier s : SUPPLIERS) {
                int pos = mp.getPositionForSupplier(s);
                data[i][col++] = pos > 0 ? pos : null;
            }

            // [Análisis Final]
            data[i][col++] = mp.getWinnerSupplier() != null ? mp.getWinnerSupplier().getDisplayName() : "";
            data[i][col++] = mp.getDiffPct() > 0 ? mp.getDiffPct() : null;
            data[i][col++] = mp.getSimulatedMargin() > 0 ? mp.getSimulatedMargin() : null;
        }

        model = new DefaultTableModel(data, columnNames) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int c) {
                if (c <= 1)
                    return String.class;
                if (c == 2)
                    return Integer.class;
                // Posicion columns are Integer
                if (c >= COL_POSICION_START && c < COL_ANALISIS_START)
                    return Integer.class;
                // Winner column is String
                if (c == COL_ANALISIS_START)
                    return String.class;
                return Double.class;
            }
        };

        table = new JTable(model);
        table.setBackground(TABLE_BG);
        table.setForeground(Color.WHITE);
        table.setSelectionBackground(new Color(60, 70, 90));
        table.setSelectionForeground(Color.WHITE);
        table.setRowHeight(28);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setGridColor(new Color(50, 55, 65));
        table.setShowGrid(true);
        table.setAutoCreateRowSorter(false);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        // Custom header renderer with group coloring
        JTableHeader header = table.getTableHeader();
        header.setDefaultRenderer(new GroupedHeaderRenderer());
        header.setPreferredSize(new Dimension(0, 36));

        // Set column widths
        TableColumnModel cm = table.getColumnModel();
        cm.getColumn(0).setPreferredWidth(120); // Código
        cm.getColumn(1).setPreferredWidth(260); // Descripción
        cm.getColumn(2).setPreferredWidth(45); // #Prov
        for (int i = COL_PRECIO_VENTA_START; i < COL_PRECIO_VENTA_START + SUPPLIER_COUNT; i++)
            cm.getColumn(i).setPreferredWidth(82);
        for (int i = COL_OFERTA_START; i < COL_OFERTA_START + SUPPLIER_COUNT; i++)
            cm.getColumn(i).setPreferredWidth(68);
        for (int i = COL_PRECIO_CON_OF_START; i < COL_PRECIO_CON_OF_START + SUPPLIER_COUNT; i++)
            cm.getColumn(i).setPreferredWidth(85);
        for (int i = COL_POSICION_START; i < COL_POSICION_START + SUPPLIER_COUNT; i++)
            cm.getColumn(i).setPreferredWidth(52);
        cm.getColumn(COL_ANALISIS_START).setPreferredWidth(85); // Ganador
        cm.getColumn(COL_ANALISIS_START + 1).setPreferredWidth(65); // DIF%
        cm.getColumn(COL_ANALISIS_START + 2).setPreferredWidth(80); // Utilidad

        // Custom cell renderers
        table.setDefaultRenderer(Double.class, new PriceCellRenderer());
        table.setDefaultRenderer(Integer.class, new PositionCellRenderer());
        table.setDefaultRenderer(String.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean hasFocus,
                    int row, int column) {
                super.getTableCellRendererComponent(t, value, sel, hasFocus, row, column);
                setBackground(sel ? new Color(60, 70, 90) : TABLE_BG);
                setForeground(Color.WHITE);
                return this;
            }
        });

        // Sorter and filter
        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);

        DocumentListener docListener = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        };
        searchField.getDocument().addDocumentListener(docListener);
        stockOnlyCheck.addItemListener(e -> applyFilter());
        strategyFilter.addActionListener(e -> applyFilter());

        JScrollPane scrollPane = new JScrollPane(table,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(TABLE_BG);
        add(scrollPane, "grow");
    }

    private void applyFilter() {
        String text = searchField.getText().trim().toLowerCase();
        boolean stockOnly = stockOnlyCheck.isSelected();
        String strategy = (String) strategyFilter.getSelectedItem();

        sorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                if (!text.isEmpty()) {
                    String barcode = entry.getStringValue(0).toLowerCase();
                    String desc = entry.getStringValue(1).toLowerCase();
                    if (!barcode.contains(text) && !desc.contains(text))
                        return false;
                }
                if (stockOnly) {
                    String winner = entry.getStringValue(COL_ANALISIS_START);
                    if (winner == null || winner.isEmpty())
                        return false;
                }
                if (strategy != null && !FILTER_ALL.equals(strategy)) {
                    int modelRow = entry.getIdentifier();
                    if (strategy.startsWith(FILTER_PREFIX_WINNER)) {
                        String targetSupplier = strategy.substring(FILTER_PREFIX_WINNER.length());
                        String winner = entry.getStringValue(COL_ANALISIS_START);
                        if (!targetSupplier.equals(winner))
                            return false;
                    } else if (strategy.startsWith(FILTER_PREFIX_LOSER)) {
                        String targetSupplier = strategy.substring(FILTER_PREFIX_LOSER.length());
                        if (modelRow >= 0 && modelRow < products.size()) {
                            MasterProduct mp = products.get(modelRow);
                            Supplier worst = mp.getWorstPriceSupplier();
                            if (worst == null || !worst.getDisplayName().equals(targetSupplier))
                                return false;
                        }
                    }
                }
                return true;
            }
        });

        countLabel.setText(table.getRowCount() + " de " + products.size() + " productos");
    }

    /** Renderer for Double cells: prices and percentages */
    private class PriceCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean hasFocus, int row,
                int column) {
            super.getTableCellRendererComponent(t, value, sel, hasFocus, row, column);

            setHorizontalAlignment(SwingConstants.RIGHT);
            setBackground(sel ? new Color(60, 70, 90) : TABLE_BG);
            setForeground(Color.WHITE);

            if (value instanceof Double d) {
                int modelRow = t.convertRowIndexToModel(row);

                // OFERTA section: show as percentage
                if (column >= COL_OFERTA_START && column < COL_PRECIO_CON_OF_START) {
                    setText(String.format("%.1f%%", d));
                    setForeground(new Color(255, 200, 100));
                }
                // PRECIO CON OF section: highlight winner in green
                else if (column >= COL_PRECIO_CON_OF_START && column < COL_POSICION_START) {
                    setText(String.format("%.2f", d));
                    int supplierIdx = column - COL_PRECIO_CON_OF_START;
                    Supplier s = SUPPLIERS[supplierIdx];
                    Object winnerObj = model.getValueAt(modelRow, COL_ANALISIS_START);
                    if (winnerObj != null && s.getDisplayName().equals(winnerObj.toString())) {
                        setBackground(sel ? new Color(39, 174, 96, 100) : WINNER_BG);
                        setForeground(new Color(100, 255, 140));
                        setFont(getFont().deriveFont(Font.BOLD));
                    }
                }
                // DIF%
                else if (column == COL_ANALISIS_START + 1) {
                    setText(String.format("%.1f%%", d));
                    if (d > 10)
                        setForeground(new Color(255, 100, 100));
                    else if (d > 5)
                        setForeground(new Color(255, 200, 100));
                    else
                        setForeground(new Color(100, 255, 140));
                }
                // Default number
                else {
                    setText(String.format("%.2f", d));
                }
            } else {
                setText("");
            }

            return this;
        }
    }

    /** Renderer for Integer cells: position ranking */
    private class PositionCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean hasFocus, int row,
                int column) {
            super.getTableCellRendererComponent(t, value, sel, hasFocus, row, column);

            setHorizontalAlignment(SwingConstants.CENTER);
            setBackground(sel ? new Color(60, 70, 90) : TABLE_BG);
            setForeground(Color.WHITE);

            if (value instanceof Integer pos) {
                setText(String.valueOf(pos));
                if (column >= COL_POSICION_START && column < COL_ANALISIS_START) {
                    if (pos == 1) {
                        setForeground(new Color(100, 255, 140));
                        setFont(getFont().deriveFont(Font.BOLD));
                    } else if (pos == 2) {
                        setForeground(new Color(255, 220, 100));
                    } else {
                        setForeground(new Color(180, 180, 190));
                    }
                }
            } else {
                setText("");
            }

            return this;
        }
    }

    /** Header renderer with group background colors */
    private class GroupedHeaderRenderer extends DefaultTableCellRenderer {
        private static final Color GRP_BASE = new Color(55, 60, 75);
        private static final Color GRP_PV = new Color(50, 70, 100);
        private static final Color GRP_OF = new Color(100, 70, 50);
        private static final Color GRP_NET = new Color(40, 90, 60);
        private static final Color GRP_POS = new Color(80, 60, 100);
        private static final Color GRP_ANAL = new Color(70, 60, 55);

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean hasFocus, int row,
                int column) {
            super.getTableCellRendererComponent(t, value, sel, hasFocus, row, column);

            setFont(new Font("Segoe UI", Font.BOLD, 10));
            setForeground(Color.WHITE);
            setHorizontalAlignment(SwingConstants.CENTER);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 2, 1, new Color(30, 33, 40)),
                    BorderFactory.createEmptyBorder(2, 4, 2, 4)));

            if (column < COL_PRECIO_VENTA_START) {
                setBackground(GRP_BASE);
            } else if (column < COL_OFERTA_START) {
                setBackground(GRP_PV);
            } else if (column < COL_PRECIO_CON_OF_START) {
                setBackground(GRP_OF);
            } else if (column < COL_POSICION_START) {
                setBackground(GRP_NET);
            } else if (column < COL_ANALISIS_START) {
                setBackground(GRP_POS);
            } else {
                setBackground(GRP_ANAL);
            }

            return this;
        }
    }
}
