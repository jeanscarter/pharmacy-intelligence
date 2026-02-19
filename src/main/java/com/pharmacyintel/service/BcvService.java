package com.pharmacyintel.service;

import com.pharmacyintel.model.GlobalConfig;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;

/**
 * Scrapes the BCV (Banco Central de Venezuela) website for the official USD/VES
 * exchange rate. Includes SSL bypass for environments with certificate issues.
 */
public class BcvService {

    private static final String BCV_URL = "https://www.bcv.org.ve/";
    private static final int TIMEOUT_MS = 15000;

    /**
     * Fetch current USD/VES rate from BCV and store in GlobalConfig.
     * Returns the rate or 0 if failed.
     */
    public double fetchRate() {
        try {
            // Disable SSL verification for BCV (common issue with Venezuelan gov sites)
            disableSSLVerification();

            Document doc = Jsoup.connect(BCV_URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .sslSocketFactory(trustAllSocketFactory())
                    .get();

            // BCV page has a div with id "dolar" containing the rate
            Element dolarDiv = doc.getElementById("dolar");
            if (dolarDiv != null) {
                // The rate is inside a <strong> tag within the #dolar div
                Element strong = dolarDiv.selectFirst("strong");
                if (strong != null) {
                    double rate = parseVenezuelanDecimal(strong.text().trim());
                    if (rate > 0) {
                        GlobalConfig.getInstance().setBcvRate(rate);
                        System.out.println("[BcvService] Rate fetched: " + rate);
                        return rate;
                    }
                }

                // Try the div text directly
                String text = dolarDiv.text().trim();
                double rate = parseVenezuelanDecimal(text);
                if (rate > 0) {
                    GlobalConfig.getInstance().setBcvRate(rate);
                    System.out.println("[BcvService] Rate fetched: " + rate);
                    return rate;
                }
            }

            // Fallback: search all strong elements inside #dolar
            Elements strongElements = doc.select("#dolar strong");
            for (Element el : strongElements) {
                double rate = parseVenezuelanDecimal(el.text().trim());
                if (rate > 0) {
                    GlobalConfig.getInstance().setBcvRate(rate);
                    System.out.println("[BcvService] Rate fetched (fallback): " + rate);
                    return rate;
                }
            }

            // Fallback 2: look for any element with class containing "centrado"
            Elements centrado = doc.select(".centrado strong");
            for (Element el : centrado) {
                double rate = parseVenezuelanDecimal(el.text().trim());
                if (rate > 1) { // USD rate should be > 1
                    GlobalConfig.getInstance().setBcvRate(rate);
                    System.out.println("[BcvService] Rate fetched (centrado): " + rate);
                    return rate;
                }
            }

            System.err.println("[BcvService] Could not find exchange rate on BCV page");
            return 0;

        } catch (Exception e) {
            System.err.println("[BcvService] Error fetching BCV rate: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Parse Venezuelan number format: "51,3205" or "51.320,50"
     */
    private double parseVenezuelanDecimal(String text) {
        if (text == null || text.isBlank())
            return 0;
        String cleaned = text.replaceAll("[^0-9.,]", "").trim();
        if (cleaned.isEmpty())
            return 0;

        int lastComma = cleaned.lastIndexOf(',');
        int lastDot = cleaned.lastIndexOf('.');

        if (lastComma > lastDot) {
            cleaned = cleaned.replace(".", "").replace(",", ".");
        } else if (lastDot > lastComma) {
            cleaned = cleaned.replace(",", "");
        }

        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Set rate manually (fallback if web scraping fails)
     */
    public void setManualRate(double rate) {
        GlobalConfig.getInstance().setBcvRate(rate);
    }

    /** Create an SSLSocketFactory that trusts all certificates */
    private static SSLSocketFactory trustAllSocketFactory() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            return sc.getSocketFactory();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Disable SSL verification globally (brute-force fallback) */
    private static void disableSSLVerification() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            // ignore
        }
    }
}
