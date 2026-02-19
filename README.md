# 💊 Pharmacy Intelligence

**Sistema de Inteligencia de Precios para Farmacias**

Una aplicación de escritorio moderna desarrollada en Java (Swing + FlatLaf) para analizar y comparar precios de múltiples droguerías farmacéuticas en Venezuela. Permite identificar las mejores ofertas, calcular márgenes de ganancia y optimizar las compras.

## 🚀 Características Principales

*   **Carga de Archivos Multi-Formato:** Soporte para archivos Excel (`.xlsx`) y CSV de proveedores como DroActiva, Dromarko, Cobeca, Nena y otros.
*   **Detección Inteligente de Columnas:** Algoritmos heurísticos para identificar automáticamente códigos de barra, precios y descripciones en formatos desconocidos.
*   **Consolidación de Datos:** Unificación de productos por código de barras (EAN/UPC) para comparar precios "manzanas con manzanas".
*   **Cálculo de Tasa BCV:** Obtención automática de la tasa del Banco Central de Venezuela o configuración manual.
*   **Análisis Competitivo:**
    *   Identificación automática del proveedor con el mejor precio ("Ganador").
    *   Cálculo de diferencia porcentual entre el mejor precio y el precio base (DroActiva).
    *   Simulación de precios de venta (PVP) y márgenes de ganancia.
*   **Dashboard Interactivo:** Gráficos de torta y barras para visualizar la distribución de mejores precios por proveedor.
*   **Exportación a Excel:** Generación de reportes detallados con formato condicional para facilitar la toma de decisiones.

## 🛠️ Tecnologías

*   **Lenguaje:** Java 17+
*   **UI:** Swing con [FlatLaf](https://www.formdev.com/flatlaf/) (Look and Feel moderno y oscuro).
*   **Layout:** MigLayout.
*   **Procesamiento Excel:** Apache POI.
*   **Gráficos:** JFreeChart.

## 📦 Instalación y Uso

1.  **Clonar el repositorio:**
    ```bash
    git clone https://github.com/jeanscarter/pharmacy-intelligence.git
    ```
2.  **Construir el proyecto:**
    ```bash
    ./mvnw clean package
    ```
3.  **Ejecutar la aplicación:**
    Busca el archivo `.jar` generado en la carpeta `target/` y ejecútalo, o usa tu IDE favorito.

## 📄 Licencia

Este proyecto es propiedad privada y está destinado para uso interno de análisis de precios.
