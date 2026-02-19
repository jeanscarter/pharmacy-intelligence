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
 * Searchable/filterable JTable displaying the consolidated product catalog
 * with per-supplier prices, best price, winner, and margin simulation.
 */
public class ProductTablePanel extends JPanel {

    private static final Color TABLE_BG = new Color(38, 42, 52);
    private static final Color HEADER_BG = new Color(44, 48, 58);
    private static final Color WINNER_BG = new Color(39, 174, 96, 50);
    private static final Supplier[] SUPPLIERS = Supplier.values();

    private final JTable table;
    private final DefaultTableModel model;
    private final TableRowSorter<DefaultTableModel> sorter;
    private final JTextField searchField;
    private final JCheckBox stockOnlyCheck;

    public ProductTablePanel(List<MasterProduct> products) {
        setLayout(new MigLayout("insets 0, fill, wrap", "[grow]", "[]4[grow]"));
        setOpaque(false);

        // Toolbar: search + filters
        JPanel toolbar = new JPanel(new MigLayout("insets 8, fillx", "[]8[grow]16[]", ""));
        toolbar.setOpaque(false);

        JLabel searchIcon = new JLabel("🔍");
        searchIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
        toolbar.add(searchIcon);

        searchField = new JTextField();
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        searchField.putClientProperty("JTextField.placeholderText", "Buscar por código o descripción...");
        toolbar.add(searchField, "growx");

        stockOnlyCheck = new JCheckBox("Solo con stock");
        stockOnlyCheck.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        stockOnlyCheck.setForeground(Color.WHITE);
        stockOnlyCheck.setOpaque(false);
        toolbar.add(stockOnlyCheck);

        JLabel countLabel = new JLabel(products.size() + " productos");
        countLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        countLabel.setForeground(new Color(100, 160, 255));
        toolbar.add(countLabel);

        add(toolbar, "growx");

        // Build column names
        int baseCols = 3; // Barcode, Description, #Suppliers
        int supplierCols = SUPPLIERS.length; // One price column per supplier
        int analyticsCols = 5; // BestPrice, Winner, DIF%, SalePrice, Margin
        int totalCols = baseCols + supplierCols + analyticsCols;

        String[] columnNames = new String[totalCols];
        columnNames[0] = "Código";
        columnNames[1] = "Descripción";
        columnNames[2] = "#Prov";
        int col = 3;
        for (Supplier s : SUPPLIERS) {
            columnNames[col++] = s.getDisplayName();
        }
        columnNames[col++] = "Mejor $";
        columnNames[col++] = "Ganador";
        columnNames[col++] = "DIF%";
        columnNames[col++] = "P.Venta";
        columnNames[col++] = "Margen";

        // Build table data
        Object[][] data = new Object[products.size()][totalCols];
        for (int i = 0; i < products.size(); i++) {
            MasterProduct mp = products.get(i);
            col = 0;
            data[i][col++] = mp.getBarcode();
            data[i][col++] = mp.getDescription() != null ? mp.getDescription() : "";
            data[i][col++] = mp.getSupplierCount();

            for (Supplier s : SUPPLIERS) {
                double price = mp.getPriceForSupplier(s);
                data[i][col++] = price > 0 ? price : null;
            }

            data[i][col++] = mp.getBestPrice() > 0 ? mp.getBestPrice() : null;
            data[i][col++] = mp.getWinnerSupplier() != null ? mp.getWinnerSupplier().getDisplayName() : "";
            data[i][col++] = mp.getDiffPct() > 0 ? mp.getDiffPct() : null;
            data[i][col++] = mp.getSimulatedSalePrice() > 0 ? mp.getSimulatedSalePrice() : null;
            data[i][col++] = mp.getSimulatedMargin() > 0 ? mp.getSimulatedMargin() : null;
        }

        model = new DefaultTableModel(data, columnNames) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int col) {
                if (col <= 1)
                    return String.class;
                if (col == 2)
                    return Integer.class;
                if (col >= 3 + SUPPLIERS.length + 1 && col == 3 + SUPPLIERS.length + 1)
                    return String.class; // Winner
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

        // Custom header renderer
        JTableHeader header = table.getTableHeader();
        header.setBackground(HEADER_BG);
        header.setForeground(Color.WHITE);
        header.setFont(new Font("Segoe UI", Font.BOLD, 11));
        header.setPreferredSize(new Dimension(0, 32));

        // Set column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(120); // Barcode
        table.getColumnModel().getColumn(1).setPreferredWidth(280); // Description

        // Custom cell renderer for price coloring
        table.setDefaultRenderer(Double.class, new PriceCellRenderer(products));
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

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });
        stockOnlyCheck.addItemListener(e -> applyFilter());

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(TABLE_BG);
        add(scrollPane, "grow");
    }

    private void applyFilter() {
        String text = searchField.getText().trim().toLowerCase();
        boolean stockOnly = stockOnlyCheck.isSelected();

        sorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                if (!text.isEmpty()) {
                    String barcode = entry.getStringValue(0).toLowerCase();
                    String desc = entry.getStringValue(1).toLowerCase();
                    if (!barcode.contains(text) && !desc.contains(text))
                        return false;
                }
                // Stock filter: at least one supplier must have stock (suppliers start at col
                // 3+ but we'd need
                // to check product data). For simplicity, check if there's a winner (implies
                // stock exists).
                if (stockOnly) {
                    String winner = entry.getStringValue(3 + SUPPLIERS.length + 1);
                    if (winner == null || winner.isEmpty())
                        return false;
                }
                return true;
            }
        });
    }

    /** Custom renderer: highlight winner cells in green */
    private class PriceCellRenderer extends DefaultTableCellRenderer {

        PriceCellRenderer(List<MasterProduct> products) {
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean hasFocus, int row,
                int column) {
            super.getTableCellRendererComponent(t, value, sel, hasFocus, row, column);

            setHorizontalAlignment(SwingConstants.RIGHT);
            setBackground(sel ? new Color(60, 70, 90) : TABLE_BG);
            setForeground(Color.WHITE);

            if (value instanceof Double d) {
                setText(String.format("%.2f", d));

                // Check if this is a supplier column and if this supplier is the winner
                int modelRow = t.convertRowIndexToModel(row);
                if (column >= 3 && column < 3 + SUPPLIERS.length) {
                    int supplierIdx = column - 3;
                    Supplier s = SUPPLIERS[supplierIdx];

                    // Get winner from the winner column
                    Object winnerObj = model.getValueAt(modelRow, 3 + SUPPLIERS.length + 1);
                    if (winnerObj != null && s.getDisplayName().equals(winnerObj.toString())) {
                        setBackground(sel ? new Color(39, 174, 96, 100) : WINNER_BG);
                        setForeground(new Color(100, 255, 140));
                        setFont(getFont().deriveFont(Font.BOLD));
                    }
                }

                // DIF% column styling
                if (column == 3 + SUPPLIERS.length + 2) {
                    setText(String.format("%.1f%%", d));
                    if (d > 10)
                        setForeground(new Color(255, 100, 100));
                    else if (d > 5)
                        setForeground(new Color(255, 200, 100));
                    else
                        setForeground(new Color(100, 255, 140));
                }
            } else {
                setText("");
            }

            return this;
        }
    }
}
