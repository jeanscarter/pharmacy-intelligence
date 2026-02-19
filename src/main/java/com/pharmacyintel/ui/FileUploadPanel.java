package com.pharmacyintel.ui;

import com.pharmacyintel.model.GlobalConfig;
import com.pharmacyintel.model.Supplier;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.io.File;
import java.util.*;

/**
 * File upload panel: 6 slots for supplier files + BCV rate display + process
 * button.
 */
public class FileUploadPanel extends JPanel {

    public interface OnProcessListener {
        void onProcess(Map<Supplier, File> files, boolean fetchBcv);
    }

    private static final Color BG = new Color(30, 33, 40);
    private static final Color CARD_BG = new Color(40, 44, 52);
    private static final Color ACCENT = new Color(100, 160, 255);
    private static final Color SUCCESS = new Color(52, 168, 83);

    private final Map<Supplier, File> selectedFiles = new EnumMap<>(Supplier.class);
    private final Map<Supplier, JLabel> fileLabels = new EnumMap<>(Supplier.class);
    private final JTextField bcvRateField;
    private final JCheckBox fetchBcvCheck;
    private final JButton processBtn;
    private final OnProcessListener listener;

    public FileUploadPanel(OnProcessListener listener) {
        this.listener = listener;
        setLayout(new MigLayout("insets 40 60 40 60, fill, wrap", "[grow]", "[][20][]push[]"));
        setBackground(BG);

        // Title
        JLabel title = new JLabel("💊 Sistema de Inteligencia de Precios — Droguerías");
        title.setFont(new Font("Segoe UI", Font.BOLD, 28));
        title.setForeground(Color.WHITE);
        add(title, "center");

        JLabel subtitle = new JLabel("Carga los archivos de cada proveedor para generar el análisis comparativo");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        subtitle.setForeground(new Color(130, 140, 160));
        add(subtitle, "center");

        // File slots grid: 3 columns x 2 rows
        JPanel grid = new JPanel(new MigLayout("insets 0, gap 16", "[grow][grow][grow]", "[grow][grow]"));
        grid.setOpaque(false);

        for (Supplier s : Supplier.values()) {
            JPanel card = createFileCard(s);
            grid.add(card, "grow, h 140!");
        }
        add(grid, "growx");

        // BCV Rate section
        JPanel bcvPanel = new JPanel(new MigLayout("insets 16, fillx", "[]16[]16[]push[]", ""));
        bcvPanel.setOpaque(false);

        fetchBcvCheck = new JCheckBox("Obtener tasa BCV automáticamente");
        fetchBcvCheck.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        fetchBcvCheck.setForeground(Color.WHITE);
        fetchBcvCheck.setOpaque(false);
        fetchBcvCheck.setSelected(true);
        bcvPanel.add(fetchBcvCheck);

        JLabel rateLabel = new JLabel("Tasa Manual:");
        rateLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        rateLabel.setForeground(new Color(180, 185, 195));
        bcvPanel.add(rateLabel);

        bcvRateField = new JTextField("51.3205", 10);
        bcvRateField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        bcvPanel.add(bcvRateField);

        // Margin spinner
        JLabel marginLabel = new JLabel("Margen %:");
        marginLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        marginLabel.setForeground(new Color(180, 185, 195));
        bcvPanel.add(marginLabel);

        JSpinner marginSpinner = new JSpinner(new SpinnerNumberModel(30.0, 0.0, 200.0, 5.0));
        marginSpinner.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        marginSpinner.addChangeListener(
                e -> GlobalConfig.getInstance().setTargetMarginPct((Double) marginSpinner.getValue()));
        bcvPanel.add(marginSpinner);

        add(bcvPanel, "growx");

        // Process button
        processBtn = new JButton("🔄  Procesar Datos y Generar Análisis");
        processBtn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        processBtn.setBackground(ACCENT);
        processBtn.setForeground(Color.WHITE);
        processBtn.setFocusPainted(false);
        processBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        processBtn.setPreferredSize(new Dimension(420, 48));
        processBtn.addActionListener(e -> onProcess());
        add(processBtn, "center, h 48!");
    }

    private JPanel createFileCard(Supplier supplier) {
        RoundedPanel card = new RoundedPanel(16);
        card.setLayout(new MigLayout("insets 16, fill, wrap", "[grow]", "[]8[]push[]"));
        card.setBackground(CARD_BG);

        // Supplier name with colored indicator
        JPanel headerPanel = new JPanel(new MigLayout("insets 0", "[]8[]", ""));
        headerPanel.setOpaque(false);

        JPanel colorDot = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(supplier.getColor());
                ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.fillOval(0, 0, 12, 12);
            }
        };
        colorDot.setPreferredSize(new Dimension(12, 12));
        colorDot.setOpaque(false);
        headerPanel.add(colorDot);

        JLabel nameLabel = new JLabel(supplier.getDisplayName());
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        nameLabel.setForeground(Color.WHITE);
        headerPanel.add(nameLabel);
        card.add(headerPanel);

        // File extension hint
        String ext = (supplier == Supplier.DROACTIVA || supplier == Supplier.DROMARKO) ? "CSV" : "XLSX";
        JLabel extLabel = new JLabel("Archivo " + ext);
        extLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        extLabel.setForeground(new Color(120, 130, 145));
        card.add(extLabel);

        // File name label
        JLabel fileLabel = new JLabel("Arrastra o haz clic para seleccionar");
        fileLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        fileLabel.setForeground(new Color(100, 110, 130));
        fileLabels.put(supplier, fileLabel);
        card.add(fileLabel);

        // Click to browse
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                browseFile(supplier);
            }
        });

        // Drag and drop
        new DropTarget(card, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    @SuppressWarnings("unchecked")
                    java.util.List<File> files = (java.util.List<File>) dtde.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) {
                        setFile(supplier, files.get(0));
                    }
                } catch (Exception ex) {
                    Toast.show("Error al cargar archivo", Toast.Type.ERROR);
                }
            }
        });

        return card;
    }

    private void browseFile(Supplier supplier) {
        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
        if (supplier == Supplier.DROACTIVA || supplier == Supplier.DROMARKO) {
            chooser.setFileFilter(new FileNameExtensionFilter("CSV files", "csv"));
        } else {
            chooser.setFileFilter(new FileNameExtensionFilter("Excel files", "xlsx", "xls"));
        }

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            setFile(supplier, chooser.getSelectedFile());
        }
    }

    private void setFile(Supplier supplier, File file) {
        selectedFiles.put(supplier, file);
        JLabel label = fileLabels.get(supplier);
        label.setText("✓ " + file.getName());
        label.setForeground(SUCCESS);
        label.setFont(new Font("Segoe UI", Font.BOLD, 11));
        Toast.show(supplier.getDisplayName() + ": " + file.getName(), Toast.Type.SUCCESS);
    }

    private void onProcess() {
        if (selectedFiles.isEmpty()) {
            Toast.show("Debe seleccionar al menos un archivo", Toast.Type.WARNING);
            return;
        }

        // DroActiva is required as the base
        if (!selectedFiles.containsKey(Supplier.DROACTIVA)) {
            Toast.show("DroActiva es obligatorio como base de productos", Toast.Type.WARNING);
            return;
        }

        // ALWAYS set the manual BCV rate first (serves as fallback if auto-fetch fails)
        try {
            double rate = Double.parseDouble(bcvRateField.getText().replace(",", "."));
            if (rate > 0) {
                GlobalConfig.getInstance().setBcvRate(rate);
            }
        } catch (NumberFormatException e) {
            if (!fetchBcvCheck.isSelected()) {
                // Only error if manual is the only option
                Toast.show("Tasa BCV inválida", Toast.Type.ERROR);
                return;
            }
        }

        listener.onProcess(new EnumMap<>(selectedFiles), fetchBcvCheck.isSelected());
    }

    public Map<Supplier, File> getSelectedFiles() {
        return selectedFiles;
    }
}
