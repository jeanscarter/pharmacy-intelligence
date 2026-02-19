package com.pharmacyintel;

import com.pharmacyintel.engine.ConsolidationEngine;
import com.pharmacyintel.model.Supplier;
import com.pharmacyintel.service.SyncOrchestrator;
import com.pharmacyintel.ui.DashboardPanel;
import com.pharmacyintel.ui.FileUploadPanel;
import com.pharmacyintel.ui.Toast;
import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Map;

public class MainFrame extends JFrame {

    private static final Color ROOT_BG = new Color(30, 33, 40);
    private JPanel rootPanel;
    private CardLayout cardLayout;
    private JProgressBar progressBar;
    private JLabel statusLabel;

    public MainFrame() {
        setTitle("Pharmacy Intelligence — Análisis Comparativo de Precios");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1540, 920);
        setMinimumSize(new Dimension(1200, 750));
        setLocationRelativeTo(null);

        Toast.setParentFrame(this);

        cardLayout = new CardLayout();
        rootPanel = new JPanel(cardLayout);
        rootPanel.setBackground(ROOT_BG);

        // Phase 1: Upload
        FileUploadPanel uploadPanel = new FileUploadPanel(this::onProcess);
        rootPanel.add(uploadPanel, "UPLOAD");

        // Loading screen
        JPanel loadingPanel = createLoadingPanel();
        rootPanel.add(loadingPanel, "LOADING");

        cardLayout.show(rootPanel, "UPLOAD");
        setContentPane(rootPanel);

        getRootPane().putClientProperty(FlatClientProperties.TITLE_BAR_BACKGROUND, ROOT_BG);
        getRootPane().putClientProperty(FlatClientProperties.TITLE_BAR_FOREGROUND, Color.WHITE);
    }

    private void onProcess(Map<Supplier, File> files, boolean fetchBcv) {
        cardLayout.show(rootPanel, "LOADING");

        SyncOrchestrator orchestrator = new SyncOrchestrator();
        orchestrator.setProgressListener(new SyncOrchestrator.ProgressListener() {
            @Override
            public void onProgress(String stage, int percent) {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(percent);
                    statusLabel.setText(stage);
                });
            }

            @Override
            public void onError(String stage, String message) {
                SwingUtilities.invokeLater(() -> {
                    Toast.show(stage + ": " + message, Toast.Type.WARNING);
                });
            }

            @Override
            public void onComplete(File excelFile, ConsolidationEngine engine) {
                SwingUtilities.invokeLater(() -> showDashboard(engine, excelFile));
            }
        });

        // Run on background thread
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                File outputDir = new File(System.getProperty("user.dir"));
                orchestrator.execute(files, outputDir, fetchBcv);
                return null;
            }
        }.execute();
    }

    private void showDashboard(ConsolidationEngine engine, File excelFile) {
        // Build dashboard view
        JPanel dashView = new JPanel(new MigLayout("insets 0, fill, wrap", "[grow]", "[]0[grow]"));
        dashView.setBackground(ROOT_BG);

        // Title bar
        JPanel titleBar = new JPanel(new MigLayout("insets 12 24 12 24, fillx", "[]16[]push[]16[]", ""));
        titleBar.setBackground(new Color(35, 38, 46));

        JLabel appTitle = new JLabel("💊 Pharmacy Intelligence");
        appTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        appTitle.setForeground(Color.WHITE);
        titleBar.add(appTitle);

        JLabel subtitle = new JLabel("Dashboard de Análisis Comparativo");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitle.setForeground(new Color(130, 140, 160));
        titleBar.add(subtitle);

        // Back button
        JButton backBtn = new JButton("← Cargar nuevos archivos");
        backBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        backBtn.setForeground(new Color(100, 160, 255));
        backBtn.setContentAreaFilled(false);
        backBtn.setBorderPainted(false);
        backBtn.setFocusPainted(false);
        backBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        backBtn.addActionListener(e -> {
            rootPanel.remove(dashView);
            cardLayout.show(rootPanel, "UPLOAD");
        });
        titleBar.add(backBtn);

        // Open Excel button (if file was generated)
        if (excelFile != null && excelFile.exists()) {
            JButton openExcel = new JButton("📥 Abrir Excel");
            openExcel.setFont(new Font("Segoe UI", Font.BOLD, 12));
            openExcel.setBackground(new Color(52, 168, 83));
            openExcel.setForeground(Color.WHITE);
            openExcel.setFocusPainted(false);
            openExcel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            openExcel.addActionListener(e -> {
                try {
                    if (Desktop.isDesktopSupported())
                        Desktop.getDesktop().open(excelFile);
                } catch (Exception ex) {
                    Toast.show("Error al abrir Excel", Toast.Type.ERROR);
                }
            });
            titleBar.add(openExcel);
        }

        dashView.add(titleBar, "growx, h 56!");

        // Dashboard content
        DashboardPanel dashboard = new DashboardPanel(engine);
        dashView.add(dashboard, "grow");

        rootPanel.add(dashView, "DASHBOARD");
        cardLayout.show(rootPanel, "DASHBOARD");

        Toast.show("Análisis completado: " + engine.getTotalProducts() + " productos", Toast.Type.SUCCESS);
    }

    private JPanel createLoadingPanel() {
        JPanel panel = new JPanel(new MigLayout("insets 0, fill, wrap", "[center]", "push[]16[]16[]push"));
        panel.setBackground(ROOT_BG);

        JLabel loadingIcon = new JLabel("⏳");
        loadingIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 48));
        panel.add(loadingIcon, "center");

        statusLabel = new JLabel("Iniciando procesamiento...");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        statusLabel.setForeground(Color.WHITE);
        panel.add(statusLabel, "center");

        progressBar = new JProgressBar(0, 100);
        progressBar.setPreferredSize(new Dimension(400, 8));
        progressBar.setStringPainted(false);
        progressBar.setForeground(new Color(100, 160, 255));
        progressBar.setBackground(new Color(50, 55, 65));
        panel.add(progressBar, "center, w 400!, h 8!");

        return panel;
    }
}
