package com.pharmacyintel.ui;

import com.pharmacyintel.model.Supplier;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import java.awt.*;
import java.util.Map;

/**
 * Factory for creating dark-themed JFreeChart panels.
 */
public class PharmacyChartFactory {

    private static final Color BG = new Color(30, 33, 40);
    private static final Color PLOT_BG = new Color(38, 42, 52);
    private static final Color GRID = new Color(55, 60, 72);
    private static final Color TEXT = new Color(200, 205, 215);
    private static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 14);
    private static final Font LABEL_FONT = new Font("Segoe UI", Font.PLAIN, 11);

    /** Bar chart: one value per supplier */
    public static ChartPanel createBarChart(String title, Map<Supplier, ? extends Number> data, String valueLabel) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        // Use each supplier as a separate series for per-bar coloring
        for (var entry : data.entrySet()) {
            dataset.addValue(entry.getValue(), entry.getKey().getDisplayName(), entry.getKey().getDisplayName());
        }

        JFreeChart chart = ChartFactory.createBarChart(title, "", valueLabel, dataset,
                PlotOrientation.VERTICAL, false, true, false);

        styleChart(chart);
        CategoryPlot plot = chart.getCategoryPlot();
        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setMaximumBarWidth(0.08);
        renderer.setBarPainter(new org.jfree.chart.renderer.category.StandardBarPainter());

        // Color each series by its supplier
        int seriesIdx = 0;
        for (Supplier s : data.keySet()) {
            renderer.setSeriesPaint(seriesIdx++, s.getColor());
        }

        return wrap(chart);
    }

    /** Horizontal bar chart for ranking */
    public static ChartPanel createHorizontalBarChart(String title, Map<Supplier, Integer> data) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        // Sort by value descending, each supplier as its own series
        data.entrySet().stream()
                .sorted(Map.Entry.<Supplier, Integer>comparingByValue().reversed())
                .forEach(e -> dataset.addValue(e.getValue(), e.getKey().getDisplayName(), e.getKey().getDisplayName()));

        JFreeChart chart = ChartFactory.createBarChart(title, "", "Victorias", dataset,
                PlotOrientation.HORIZONTAL, false, true, false);

        styleChart(chart);
        CategoryPlot plot = chart.getCategoryPlot();
        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setBarPainter(new org.jfree.chart.renderer.category.StandardBarPainter());
        renderer.setMaximumBarWidth(0.12);

        // Color each series by its supplier
        int seriesIdx = 0;
        for (var entry : data.entrySet().stream()
                .sorted(Map.Entry.<Supplier, Integer>comparingByValue().reversed()).toList()) {
            renderer.setSeriesPaint(seriesIdx++, entry.getKey().getColor());
        }

        return wrap(chart);
    }

    /** Pie chart */
    @SuppressWarnings("unchecked")
    public static ChartPanel createPieChart(String title, Map<Supplier, Integer> data) {
        DefaultPieDataset<String> dataset = new DefaultPieDataset<>();
        for (var entry : data.entrySet()) {
            if (entry.getValue() > 0) {
                dataset.setValue(entry.getKey().getDisplayName(), entry.getValue());
            }
        }

        JFreeChart chart = ChartFactory.createPieChart(title, dataset, true, true, false);
        chart.setBackgroundPaint(BG);
        chart.getTitle().setFont(TITLE_FONT);
        chart.getTitle().setPaint(TEXT);

        PiePlot<String> plot = (PiePlot<String>) chart.getPlot();
        plot.setBackgroundPaint(PLOT_BG);
        plot.setOutlinePaint(null);
        plot.setLabelFont(LABEL_FONT);
        plot.setLabelPaint(TEXT);
        plot.setShadowPaint(null);

        for (var entry : data.entrySet()) {
            plot.setSectionPaint(entry.getKey().getDisplayName(), entry.getKey().getColor());
        }

        if (chart.getLegend() != null) {
            chart.getLegend().setBackgroundPaint(BG);
            chart.getLegend().setItemPaint(TEXT);
            chart.getLegend().setItemFont(LABEL_FONT);
        }

        return wrap(chart);
    }

    /** Grouped bar chart: base price vs offer price per supplier */
    public static ChartPanel createGroupedBarChart(String title, Map<Supplier, double[]> data) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (var entry : data.entrySet()) {
            dataset.addValue(entry.getValue()[0], "Precio Base", entry.getKey().getDisplayName());
            dataset.addValue(entry.getValue()[1], "Precio Neto", entry.getKey().getDisplayName());
        }

        JFreeChart chart = ChartFactory.createBarChart(title, "", "USD", dataset,
                PlotOrientation.VERTICAL, true, true, false);

        styleChart(chart);
        CategoryPlot plot = chart.getCategoryPlot();
        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setBarPainter(new org.jfree.chart.renderer.category.StandardBarPainter());

        renderer.setSeriesPaint(0, new Color(100, 140, 200, 180));
        renderer.setSeriesPaint(1, new Color(52, 168, 83));

        return wrap(chart);
    }

    // --- Helpers ---

    private static void styleChart(JFreeChart chart) {
        chart.setBackgroundPaint(BG);
        chart.getTitle().setFont(TITLE_FONT);
        chart.getTitle().setPaint(TEXT);

        if (chart.getLegend() != null) {
            chart.getLegend().setBackgroundPaint(BG);
            chart.getLegend().setItemPaint(TEXT);
            chart.getLegend().setItemFont(LABEL_FONT);
        }

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(PLOT_BG);
        plot.setOutlinePaint(null);
        plot.setRangeGridlinePaint(GRID);
        plot.setDomainGridlinePaint(GRID);
        plot.getDomainAxis().setTickLabelPaint(TEXT);
        plot.getDomainAxis().setTickLabelFont(LABEL_FONT);
        plot.getDomainAxis().setLabelPaint(TEXT);
        plot.getRangeAxis().setTickLabelPaint(TEXT);
        plot.getRangeAxis().setTickLabelFont(LABEL_FONT);
        plot.getRangeAxis().setLabelPaint(TEXT);
    }

    private static ChartPanel wrap(JFreeChart chart) {
        ChartPanel panel = new ChartPanel(chart);
        panel.setBackground(BG);
        panel.setOpaque(false);
        panel.setMinimumDrawWidth(200);
        panel.setMinimumDrawHeight(150);
        panel.setMaximumDrawWidth(2000);
        panel.setMaximumDrawHeight(1500);
        return panel;
    }

}
