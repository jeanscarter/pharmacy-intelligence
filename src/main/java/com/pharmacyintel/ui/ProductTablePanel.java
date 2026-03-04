package com.pharmacyintel.ui;

import com.pharmacyintel.model.*;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ProductTablePanel extends JPanel {

    private static final Color TABLE_BG = new Color(38, 42, 52);
    private static final Color WINNER_BG = new Color(39, 174, 96, 100);
    private static final Color LOSER_BG = new Color(198, 40, 40, 100);
    private static final Supplier[] SUPPLIERS = Supplier.values();
    private static final int SUPPLIER_COUNT = SUPPLIERS.length;

    // Column group start indices
    private static final int COL_PRECIO_VENTA_START = 3;
    private static final int COL_OFERTA_START = 3 + SUPPLIER_COUNT;
    private static final int COL_PRECIO_CON_OF_START = 3 + SUPPLIER_COUNT * 2;
    private static final int COL_POSICION_START = 3 + SUPPLIER_COUNT * 3;
    private static final int COL_ANALISIS_START = 3 + SUPPLIER_COUNT * 4;
    private static final int COL_INVENTARIO_START = COL_ANALISIS_START + 3;
    private static final int COL_LOSER = COL_INVENTARIO_START + SUPPLIER_COUNT; // Hidden column for loser supplier
    private static final int TOTAL_COLS = 3 + SUPPLIER_COUNT * 4 + 3 + SUPPLIER_COUNT + 1; // +1 for hidden loser

    private static final String FILTER_ALL = "Todos";
    private static final String FILTER_MEJOR_PRECIO = "Mejor Precio DroActiva";
    private static final String FILTER_MEJOR_OFERTA = "Mejor Oferta DroActiva";
    private static final String FILTER_PEOR_NETO = "Peor Neto DroActiva";
    private static final String FILTER_PEOR_OFERTA = "Peor Oferta DroActiva";

    // DroActiva column indices for filtering
    private static final int DROACTIVA_IDX = 0; // Supplier.DROACTIVA ordinal

    private final JTable table;
    private final DefaultTableModel model;
    private final TableRowSorter<DefaultTableModel> sorter;
    private final JTextField searchField;
    private final JCheckBox stockOnlyCheck;
    private final JComboBox<String> strategyFilter;
    private final JLabel countLabel;
    private final List<MasterProduct> products;

    // Filter change listener
    private FilterChangeListener filterChangeListener;

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
        strategyFilter.setPreferredSize(new Dimension(250, 28));
        strategyFilter.addItem(FILTER_ALL);
        strategyFilter.addItem(FILTER_MEJOR_PRECIO);
        strategyFilter.addItem(FILTER_MEJOR_OFERTA);
        strategyFilter.addItem(FILTER_PEOR_NETO);
        strategyFilter.addItem(FILTER_PEOR_OFERTA);
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
        columnNames[col++] = "Código";
        columnNames[col++] = "Descripción";
        columnNames[col++] = "#Prov";
        for (Supplier s : SUPPLIERS) {
            columnNames[col++] = "PV " + s.getDisplayName();
        }
        for (Supplier s : SUPPLIERS) {
            columnNames[col++] = "OF " + s.getDisplayName();
        }
        for (Supplier s : SUPPLIERS) {
            columnNames[col++] = "NET " + s.getDisplayName();
        }
        for (Supplier s : SUPPLIERS) {
            columnNames[col++] = "P# " + s.getDisplayName();
        }
        columnNames[col++] = "Ganador";
        columnNames[col++] = "DIF%";
        columnNames[col++] = "Utilidad Sim.";
        for (Supplier s : SUPPLIERS) {
            columnNames[col++] = "INV " + s.getDisplayName();
        }
        columnNames[col++] = "_loser"; // Hidden

        // Build table data
        Object[][] data = new Object[products.size()][TOTAL_COLS];
        for (int i = 0; i < products.size(); i++) {
            MasterProduct mp = products.get(i);
            col = 0;

            data[i][col++] = mp.getBarcode();
            data[i][col++] = mp.getDescription() != null ? mp.getDescription() : "";
            data[i][col++] = mp.getSupplierCount();

            for (Supplier s : SUPPLIERS) {
                double bp = mp.getBasePriceForSupplier(s);
                data[i][col++] = bp > 0 ? bp : null;
            }
            for (Supplier s : SUPPLIERS) {
                double op = mp.getOfferPctForSupplier(s);
                data[i][col++] = op > 0 ? op : null;
            }
            for (Supplier s : SUPPLIERS) {
                double np = mp.getNetPriceForSupplier(s);
                data[i][col++] = np > 0 ? np : null;
            }
            for (Supplier s : SUPPLIERS) {
                int pos = mp.getPositionForSupplier(s);
                data[i][col++] = pos > 0 ? pos : null;
            }

            data[i][col++] = mp.getWinnerSupplier() != null ? mp.getWinnerSupplier().getDisplayName() : "";
            data[i][col++] = mp.getDiffPct() > 0 ? mp.getDiffPct() : null;
            data[i][col++] = mp.getSimulatedMargin() > 0 ? mp.getSimulatedMargin() : null;
            for (Supplier s : SUPPLIERS) {
                int inv = mp.getStockForSupplier(s);
                data[i][col++] = inv > 0 ? inv : null;
            }
            data[i][col++] = mp.getLoserSupplier() != null ? mp.getLoserSupplier().getDisplayName() : "";
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
                if (c >= COL_POSICION_START && c < COL_ANALISIS_START)
                    return Integer.class;
                if (c >= COL_INVENTARIO_START && c < COL_LOSER)
                    return Integer.class;
                if (c == COL_ANALISIS_START || c == COL_LOSER)
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

        // Hide loser column
        table.getColumnModel().getColumn(COL_LOSER).setMinWidth(0);
        table.getColumnModel().getColumn(COL_LOSER).setMaxWidth(0);
        table.getColumnModel().getColumn(COL_LOSER).setPreferredWidth(0);

        // Custom header renderer with group coloring
        JTableHeader header = table.getTableHeader();
        header.setDefaultRenderer(new GroupedHeaderRenderer());
        header.setPreferredSize(new Dimension(0, 36));

        // Set column widths
        TableColumnModel cm = table.getColumnModel();
        cm.getColumn(0).setPreferredWidth(120);
        cm.getColumn(1).setPreferredWidth(260);
        cm.getColumn(2).setPreferredWidth(45);
        for (int i = COL_PRECIO_VENTA_START; i < COL_PRECIO_VENTA_START + SUPPLIER_COUNT; i++)
            cm.getColumn(i).setPreferredWidth(82);
        for (int i = COL_OFERTA_START; i < COL_OFERTA_START + SUPPLIER_COUNT; i++)
            cm.getColumn(i).setPreferredWidth(68);
        for (int i = COL_PRECIO_CON_OF_START; i < COL_PRECIO_CON_OF_START + SUPPLIER_COUNT; i++)
            cm.getColumn(i).setPreferredWidth(85);
        for (int i = COL_POSICION_START; i < COL_POSICION_START + SUPPLIER_COUNT; i++)
            cm.getColumn(i).setPreferredWidth(52);
        cm.getColumn(COL_ANALISIS_START).setPreferredWidth(85);
        cm.getColumn(COL_ANALISIS_START + 1).setPreferredWidth(65);
        cm.getColumn(COL_ANALISIS_START + 2).setPreferredWidth(80);
        for (int i = COL_INVENTARIO_START; i < COL_INVENTARIO_START + SUPPLIER_COUNT; i++)
            cm.getColumn(i).setPreferredWidth(65);

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

    /**
     * Register a listener to be notified when the filter changes.
     */
    public void setFilterChangeListener(FilterChangeListener listener) {
        this.filterChangeListener = listener;
    }

    /**
     * Trigger the initial filter notification (call after listener is set).
     */
    public void fireInitialFilter() {
        applyFilter();
    }

    /**
     * Returns the currently selected filter name.
     */
    public String getActiveFilter() {
        String f = (String) strategyFilter.getSelectedItem();
        return f != null ? f : FILTER_ALL;
    }

    private void applyFilter() {
        String text = searchField.getText().trim().toLowerCase();
        boolean stockOnly = stockOnlyCheck.isSelected();
        String strategy = (String) strategyFilter.getSelectedItem();

        // Column indices for DroActiva
        final int colNetDroactiva = COL_PRECIO_CON_OF_START + DROACTIVA_IDX;
        final String droactivaName = Supplier.DROACTIVA.getDisplayName();

        sorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                // Text search
                if (!text.isEmpty()) {
                    String barcode = entry.getStringValue(0).toLowerCase();
                    String desc = entry.getStringValue(1).toLowerCase();
                    if (!barcode.contains(text) && !desc.contains(text))
                        return false;
                }
                // Stock filter
                if (stockOnly) {
                    String winner = entry.getStringValue(COL_ANALISIS_START);
                    if (winner == null || winner.isEmpty())
                        return false;
                }
                // Strategy filters
                if (strategy != null && !FILTER_ALL.equals(strategy)) {
                    String winner = entry.getStringValue(COL_ANALISIS_START);
                    if (FILTER_MEJOR_PRECIO.equals(strategy)) {
                        // DroActiva must be the winner
                        if (!droactivaName.equals(winner))
                            return false;
                    } else if (FILTER_MEJOR_OFERTA.equals(strategy)) {
                        // DroActiva must have the best OF% among all suppliers
                        Object ofDroObj = entry.getValue(COL_OFERTA_START + DROACTIVA_IDX);
                        if (ofDroObj == null)
                            return false;
                        double ofDro = ((Number) ofDroObj).doubleValue();
                        if (ofDro <= 0)
                            return false;
                        for (int si = 0; si < SUPPLIER_COUNT; si++) {
                            if (si == DROACTIVA_IDX)
                                continue;
                            Object ofOther = entry.getValue(COL_OFERTA_START + si);
                            if (ofOther != null) {
                                double otherOf = ((Number) ofOther).doubleValue();
                                if (otherOf > ofDro)
                                    return false;
                            }
                        }
                    } else if (FILTER_PEOR_NETO.equals(strategy)) {
                        // DroActiva must have the HIGHEST net price (worst/loser)
                        Object netDroObj = entry.getValue(colNetDroactiva);
                        if (netDroObj == null)
                            return false;
                        double netDro = ((Number) netDroObj).doubleValue();
                        if (netDro <= 0)
                            return false;
                        boolean hasLower = false;
                        for (int si = 0; si < SUPPLIER_COUNT; si++) {
                            if (si == DROACTIVA_IDX)
                                continue;
                            Object netOther = entry.getValue(COL_PRECIO_CON_OF_START + si);
                            if (netOther != null) {
                                double otherNet = ((Number) netOther).doubleValue();
                                if (otherNet > 0 && otherNet >= netDro)
                                    return false; // someone else has same or higher net
                                if (otherNet > 0)
                                    hasLower = true;
                            }
                        }
                        if (!hasLower)
                            return false; // no one to compare
                    } else if (FILTER_PEOR_OFERTA.equals(strategy)) {
                        // DroActiva must have the LOWEST OF% among suppliers with offers
                        Object ofDroObj = entry.getValue(COL_OFERTA_START + DROACTIVA_IDX);
                        double ofDro = 0;
                        if (ofDroObj != null)
                            ofDro = ((Number) ofDroObj).doubleValue();
                        boolean hasHigher = false;
                        for (int si = 0; si < SUPPLIER_COUNT; si++) {
                            if (si == DROACTIVA_IDX)
                                continue;
                            Object ofOther = entry.getValue(COL_OFERTA_START + si);
                            if (ofOther != null) {
                                double otherOf = ((Number) ofOther).doubleValue();
                                if (otherOf > 0 && otherOf > ofDro)
                                    hasHigher = true;
                                if (otherOf > 0 && otherOf <= ofDro)
                                    return false; // someone else has same or lower
                            }
                        }
                        if (!hasHigher)
                            return false;
                    }
                }
                return true;
            }
        });

        countLabel.setText(table.getRowCount() + " de " + products.size() + " productos");

        // Notify listener with visible products
        if (filterChangeListener != null) {
            List<MasterProduct> visibleProducts = getVisibleProducts();
            filterChangeListener.onFilterChanged(visibleProducts, strategy != null ? strategy : FILTER_ALL);
        }
    }

    /**
     * Build the list of MasterProduct instances that are currently visible in the
     * table.
     */
    private List<MasterProduct> getVisibleProducts() {
        List<MasterProduct> visible = new ArrayList<>();
        for (int viewRow = 0; viewRow < table.getRowCount(); viewRow++) {
            int modelRow = table.convertRowIndexToModel(viewRow);
            if (modelRow >= 0 && modelRow < products.size()) {
                visible.add(products.get(modelRow));
            }
        }
        return visible;
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
            setFont(t.getFont());

            if (value instanceof Double d) {
                int modelRow = t.convertRowIndexToModel(row);

                // OFERTA section: show as percentage
                if (column >= COL_OFERTA_START && column < COL_PRECIO_CON_OF_START) {
                    setText(String.format("%.1f%%", d));
                    setForeground(new Color(255, 200, 100));
                }
                // PRECIO CON OF section: highlight winner (green) and loser (red)
                else if (column >= COL_PRECIO_CON_OF_START && column < COL_POSICION_START) {
                    setText(String.format("%.2f", d));
                    int supplierIdx = column - COL_PRECIO_CON_OF_START;
                    Supplier s = SUPPLIERS[supplierIdx];

                    Object winnerObj = model.getValueAt(modelRow, COL_ANALISIS_START);
                    Object loserObj = model.getValueAt(modelRow, COL_LOSER);

                    if (winnerObj != null && s.getDisplayName().equals(winnerObj.toString())) {
                        setBackground(sel ? new Color(39, 174, 96, 140) : WINNER_BG);
                        setForeground(new Color(100, 255, 140));
                        setFont(t.getFont().deriveFont(Font.BOLD));
                    } else if (loserObj != null && s.getDisplayName().equals(loserObj.toString())) {
                        setBackground(sel ? new Color(198, 40, 40, 140) : LOSER_BG);
                        setForeground(new Color(255, 120, 120));
                        setFont(t.getFont().deriveFont(Font.BOLD));
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
                } else if (column >= COL_INVENTARIO_START && column < COL_LOSER) {
                    setForeground(new Color(220, 220, 230));
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
            } else if (column < COL_INVENTARIO_START) {
                setBackground(GRP_ANAL);
            } else {
                setBackground(new Color(65, 80, 85));
            }

            return this;
        }
    }
}
