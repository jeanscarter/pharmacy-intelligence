package com.pharmacyintel;

import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.*;

public class App {
    public static void main(String[] args) {
        FlatDarkLaf.setup();
        UIManager.put("Component.arc", 12);
        UIManager.put("Button.arc", 14);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new java.awt.Insets(2, 2, 2, 2));
        UIManager.put("TabbedPane.selectedBackground", new java.awt.Color(40, 44, 52));
        UIManager.put("Table.showHorizontalLines", false);
        UIManager.put("Table.showVerticalLines", false);
        UIManager.put("TableHeader.separatorColor", new java.awt.Color(50, 55, 65));
        UIManager.put("ScrollPane.smoothScrolling", true);

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
