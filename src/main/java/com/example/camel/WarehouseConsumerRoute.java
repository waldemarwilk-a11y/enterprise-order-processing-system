package com.example.camel;

import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

public class WarehouseConsumerRoute extends RouteBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(WarehouseConsumerRoute.class);

    // Stałe dla połączenia z bazą - można wyciągnąć do configu
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/shopdb";
    private static final String DB_USER = "shopuser";
    private static final String DB_PASS = "shop_pass";

    // Dozwolone statusy w systemie
    private static final Set<String> ALLOWED_STATUSES = new HashSet<>(
        Arrays.asList("oczekujace", "zrealizowane", "anulowane")
    );

    @Override
    public void configure() throws Exception {

        // ---------------------------
        // 1) Odbiór powiadomień z kolejki magazynowej
        // ---------------------------
        from("activemq:queue:magazyn_powiadomienie")
            .log("Otrzymano powiadomienie magazynowe: orderId=${body}")
            .process(exchange -> {
                Long orderId = exchange.getIn().getBody(Long.class);
                if (orderId != null) {
                    LOG.info("🔔 Zamówienie {} przekazane do realizacji w magazynie.", orderId);
                } else {
                    LOG.warn("🔔 Otrzymano pusty lub niepoprawny orderId z kolejki.");
                }
            });


        // ---------------------------
        // 2) HTTP GET – wyświetlenie listy zamówień z filtrowaniem po statusie
        // np: /warehouseQueue?status=oczekujace
        // ---------------------------
        from("jetty:http://0.0.0.0:8080/warehouseQueue?httpMethodRestrict=GET")
            .setHeader("Content-Type", constant("text/html; charset=UTF-8"))
            .process(exchange -> {
                String statusFilter = exchange.getIn().getHeader("status", String.class);
                String html = generateOrdersHtml(statusFilter);
                exchange.getIn().setBody(html);
            });


        // ---------------------------
        // 3) HTTP POST – aktualizacja statusu zamówienia na dowolny (oczekujace, zrealizowane, anulowane)
        // ---------------------------
        from("jetty:http://0.0.0.0:8080/updateStatus?httpMethodRestrict=POST")
            .process(exchange -> {
                // Pobierz orderId i newStatus z formularza POST (application/x-www-form-urlencoded)
                String orderIdParam = exchange.getIn().getHeader("orderId", String.class);
                String newStatusParam = exchange.getIn().getHeader("newStatus", String.class);

                // Jeśli Camel nie wrzucił parametrów do headerów, spróbuj z body jako Map
                if (orderIdParam == null || newStatusParam == null) {
                    Object body = exchange.getIn().getBody();
                    if (body instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> form = (Map<String, Object>) body;
                        if (orderIdParam == null) {
                            Object v = form.get("orderId");
                            if (v != null) {
                                orderIdParam = v.toString();
                            }
                        }
                        if (newStatusParam == null) {
                            Object v = form.get("newStatus");
                            if (v != null) {
                                newStatusParam = v.toString();
                            }
                        }
                    }
                }

                exchange.getIn().setHeader("Content-Type", "text/html; charset=UTF-8");

                if (orderIdParam == null || orderIdParam.isEmpty()) {
                    exchange.getIn().setBody(generateErrorPage("Brak parametru orderId."));
                    return;
                }
                if (newStatusParam == null || newStatusParam.isEmpty()) {
                    exchange.getIn().setBody(generateErrorPage("Brak parametru newStatus."));
                    return;
                }

                Long orderId;
                try {
                    orderId = Long.parseLong(orderIdParam);
                } catch (NumberFormatException e) {
                    exchange.getIn().setBody(generateErrorPage("Nieprawidłowy format orderId."));
                    return;
                }

                String newStatus = newStatusParam.toLowerCase();
                if (!ALLOWED_STATUSES.contains(newStatus)) {
                    exchange.getIn().setBody(generateErrorPage("Nieprawidłowy status: " + escapeHtml(newStatusParam)));
                    return;
                }

                try {
                    String updateResult = updateOrderStatus(orderId, newStatus);
                    exchange.getIn().setBody(generateResultPage(updateResult));
                } catch (SQLException e) {
                    LOG.error("Błąd podczas aktualizacji statusu zamówienia ID " + orderId, e);
                    exchange.getIn().setBody(generateErrorPage("Wystąpił błąd podczas aktualizacji statusu. Spróbuj ponownie później."));
                }
            });
    }


    /**
     * Generuje stronę HTML z listą zamówień,
     * opcjonalnie filtrowaną po statusie zamówienia.
     */
    private String generateOrdersHtml(String statusFilter) {
        StringBuilder html = new StringBuilder();

        // Nagłówek HTML, meta, stylizacje
        html.append("<!DOCTYPE html><html lang=\"pl\"><head><meta charset=\"UTF-8\">");
        html.append("<title>Magazyn – lista zamówień</title>");
        html.append("<style>")
            .append("body {font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #f9f9f9; margin: 0; padding: 0;} ")
            .append(".container {max-width: 1200px; margin: 0 auto; padding: 20px;} ")
            .append("h1 {text-align: center; color: #333; margin-bottom: 20px;} ")
            .append(".filter-form {display: flex; justify-content: center; align-items: center; margin-bottom: 20px;} ")
            .append(".filter-form select {padding: 8px; margin-right: 10px; border: 1px solid #ccc; border-radius: 4px;} ")
            .append(".filter-form input[type='submit'] {padding: 8px 16px; background-color: #007bff; color: #fff; border: none; border-radius: 4px; cursor: pointer;} ")
            .append(".filter-form input[type='submit']:hover {background-color: #0056b3;} ")
            .append(".order-card {background: #fff; border-radius: 6px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); margin-bottom: 20px; padding: 20px;} ")
            .append(".order-header {display: flex; justify-content: space-between; align-items: center; margin-bottom: 15px;} ")
            .append(".order-header h2 {margin: 0; color: #444;} ")
            .append(".order-meta p {margin: 4px 0; color: #555;} ")
            .append(".status-oczekujace {color: #ff8c00; font-weight: bold;} ")
            .append(".status-zrealizowane {color: #28a745; font-weight: bold;} ")
            .append(".status-anulowane {color: #dc3545; font-weight: bold;} ")
            .append(".status-select {padding: 6px; border: 1px solid #ccc; border-radius: 4px;} ")
            .append(".btn-change {padding: 6px 12px; background-color: #17a2b8; color: #fff; border: none; border-radius: 4px; cursor: pointer;} ")
            .append(".btn-change:hover {background-color: #117a8b;} ")
            .append("table {width: 100%; border-collapse: collapse; margin-top: 15px;} ")
            .append("th, td {border: 1px solid #e0e0e0; padding: 10px; text-align: left;} ")
            .append("th {background-color: #f1f1f1; color: #333;} ")
            .append("tr:nth-child(even) {background-color: #fdfdfd;} ")
            .append("tr:hover {background-color: #f5f5f5;} ")
            .append("</style>");
        html.append("</head><body>");

        html.append("<div class=\"container\">");
        html.append("<h1>Lista zamówień</h1>");

        // Form do filtrowania po statusie
        html.append("<form class=\"filter-form\" method='GET' action='/warehouseQueue'>");
        html.append("<label for='status' style='margin-right:10px;'>Filtruj po statusie:</label>");
        html.append("<select id='status' name='status'>");
        html.append("<option value='' ")
            .append(statusFilter == null || statusFilter.isEmpty() ? "selected" : "")
            .append(">Wszystkie</option>");
        html.append("<option value='oczekujace' ")
            .append("oczekujace".equalsIgnoreCase(statusFilter) ? "selected" : "")
            .append(">Oczekujące</option>");
        html.append("<option value='zrealizowane' ")
            .append("zrealizowane".equalsIgnoreCase(statusFilter) ? "selected" : "")
            .append(">Zrealizowane</option>");
        html.append("<option value='anulowane' ")
            .append("anulowane".equalsIgnoreCase(statusFilter) ? "selected" : "")
            .append(">Anulowane</option>");
        html.append("</select>");
        html.append("<input type='submit' value='Filtruj' />");
        html.append("</form>");

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            StringBuilder sql = new StringBuilder(
                "SELECT o.order_id, o.customer_first_name, o.customer_last_name, o.address, o.status, " +
                "oi.product_id, oi.quantity, oi.price, oi.stock_level, p.name, p.current_stock AS current_stock_level " +
                "FROM orders o " +
                "JOIN order_items oi ON o.order_id = oi.order_id " +
                "JOIN products p ON p.product_id = CAST(oi.product_id AS INTEGER) "
            );

            boolean useParam = false;
            String paramValue = null;

            if (statusFilter != null && !statusFilter.isEmpty()) {
                switch (statusFilter.toLowerCase()) {
                    case "oczekujace":
                        sql.append(" WHERE LOWER(o.status) = ? ");
                        useParam = true;
                        paramValue = "oczekujace";
                        break;
                    case "zrealizowane":
                        sql.append(" WHERE LOWER(o.status) = ? ");
                        useParam = true;
                        paramValue = "zrealizowane";
                        break;
                    case "anulowane":
                        sql.append(" WHERE LOWER(o.status) = ? ");
                        useParam = true;
                        paramValue = "anulowane";
                        break;
                    default:
                        // Nieznany parametr – pobieramy wszystko
                        break;
                }
            }

            sql.append(" ORDER BY o.order_id DESC");

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                if (useParam && paramValue != null) {
                    ps.setString(1, paramValue);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.isBeforeFirst()) {
                        html.append("<p style='text-align:center; color:#666;'>Brak zamówień w bazie danych.</p>");
                    } else {
                        Map<Long, List<Map<String, Object>>> grouped = new LinkedHashMap<>();

                        while (rs.next()) {
                            long orderId = rs.getLong("order_id");
                            grouped.putIfAbsent(orderId, new ArrayList<>());

                            Map<String, Object> produkt = new HashMap<>();
                            produkt.put("name", rs.getString("name"));
                            produkt.put("quantity", rs.getInt("quantity"));
                            produkt.put("price", rs.getDouble("price"));
                            produkt.put("stockLevelAtOrder", rs.getInt("stock_level"));          // stan w momencie zamówienia
                            produkt.put("stockLevelCurrent", rs.getInt("current_stock_level"));    // aktualny stan magazynu
                            produkt.put("productId", rs.getString("product_id"));
                            produkt.put("address", rs.getString("address"));
                            produkt.put("customerFirstName", rs.getString("customer_first_name"));
                            produkt.put("customerLastName", rs.getString("customer_last_name"));
                            produkt.put("status", rs.getString("status"));

                            grouped.get(orderId).add(produkt);
                        }

                        for (Map.Entry<Long, List<Map<String, Object>>> entry : grouped.entrySet()) {
                            Long orderId = entry.getKey();
                            List<Map<String, Object>> produkty = entry.getValue();
                            Map<String, Object> first = produkty.get(0);

                            String status = ((String) first.get("status")).toLowerCase();
                            String statusClass = getStatusClass(status);

                            html.append("<div class=\"order-card\">");
                            html.append("<div class=\"order-header\">");
                            html.append("<h2>Zamówienie ID: ").append(orderId).append("</h2>");
                            html.append("<span class=\"").append(statusClass).append("\">")
                                .append(escapeHtml(status.toUpperCase())).append("</span>");
                            html.append("</div>");

                            html.append("<div class=\"order-meta\">");
                            html.append("<p><strong>Klient:</strong> ")
                                .append(escapeHtml((String) first.get("customerFirstName"))).append(" ")
                                .append(escapeHtml((String) first.get("customerLastName"))).append("</p>");
                            html.append("<p><strong>Adres:</strong> ").append(escapeHtml((String) first.get("address"))).append("</p>");
                            html.append("</div>");

                            // Formularz do zmiany statusu
                            html.append("<form method='POST' action='/updateStatus' style='margin-top:10px;'>");
                            html.append("<input type='hidden' name='orderId' value='").append(orderId).append("'/>");
                            html.append("<select class=\"status-select\" name='newStatus'>");
                            html.append("<option value='oczekujace' ")
                                .append("oczekujace".equalsIgnoreCase(status) ? "selected" : "").append(">Oczekujące</option>");
                            html.append("<option value='zrealizowane' ")
                                .append("zrealizowane".equalsIgnoreCase(status) ? "selected" : "").append(">Zrealizowane</option>");
                            html.append("<option value='anulowane' ")
                                .append("anulowane".equalsIgnoreCase(status) ? "selected" : "").append(">Anulowane</option>");
                            html.append("</select> ");
                            html.append("<button type='submit' class=\"btn-change\">Zmień status</button>");
                            html.append("</form>");

                            html.append("<table>");
                            html.append("<thead><tr>")
                                .append("<th>Produkt</th>")
                                .append("<th>Ilość</th>")
                                .append("<th>Cena</th>")
                                .append("<th>Stan (aktualny / przy zamówieniu)</th>")
                                .append("</tr></thead><tbody>");

                            for (Map<String, Object> p : produkty) {
                                html.append("<tr>");
                                html.append("<td>").append(escapeHtml((String) p.get("name"))).append("</td>");
                                html.append("<td>").append(p.get("quantity")).append("</td>");
                                html.append("<td>")
                                    .append(String.format("%.2f", p.get("price"))).append(" zł</td>");

                                int stockAtOrder = (Integer) p.get("stockLevelAtOrder");
                                int stockCurrent = (Integer) p.get("stockLevelCurrent");

                                String stockColor = stockCurrent >= stockAtOrder ? "#28a745" : "#dc3545";
                                html.append("<td style='color:").append(stockColor).append(";'>")
                                    .append(stockCurrent).append(" / ").append(stockAtOrder)
                                    .append("</td>");
                                html.append("</tr>");
                            }

                            html.append("</tbody></table>");
                            html.append("</div>"); // koniec order-card
                        }
                    }
                }
            }

        } catch (SQLException e) {
            LOG.error("Błąd podczas pobierania zamówień z bazy", e);
            html.append("<p style='text-align:center; color:#dc3545;'>Błąd podczas pobierania zamówień. Spróbuj ponownie później.</p>");
        }

        html.append("</div>"); // koniec container
        html.append("</body></html>");
        return html.toString();
    }


    /**
     * Aktualizuje status zamówienia w bazie na podany newStatus
     * i zwraca komunikat o wyniku.
     */
    private String updateOrderStatus(Long orderId, String newStatus) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            // Sprawdź, czy zamówienie istnieje
            String checkSql = "SELECT status FROM orders WHERE order_id = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setLong(1, orderId);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (!rs.next()) {
                        return "Zamówienie o ID " + orderId + " nie istnieje.";
                    }
                    String currentStatus = rs.getString("status").toLowerCase();
                    if (currentStatus.equals(newStatus)) {
                        return "Zamówienie ma już status '" + newStatus + "'.";
                    }
                }
            }

            // Aktualizacja statusu
            String updateSql = "UPDATE orders SET status = ? WHERE order_id = ?";
            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                updateStmt.setString(1, newStatus);
                updateStmt.setLong(2, orderId);
                int rows = updateStmt.executeUpdate();
                if (rows > 0) {
                    return "Status zamówienia " + orderId + " został zaktualizowany na '" + newStatus + "'.";
                } else {
                    return "Nie udało się zaktualizować statusu zamówienia " + orderId + ".";
                }
            }
        }
    }

    /**
     * Zwraca nazwę klasy CSS na podstawie statusu.
     */
    private String getStatusClass(String status) {
        if (status == null) return "";
        switch (status.toLowerCase()) {
            case "oczekujace":
                return "status-oczekujace";
            case "zrealizowane":
                return "status-zrealizowane";
            case "anulowane":
                return "status-anulowane";
            default:
                return "";
        }
    }

    /**
     * Generuje prostą stronę błędu HTML z podanym komunikatem.
     */
    private String generateErrorPage(String message) {
        return "<html><head><meta charset='UTF-8'><title>Błąd</title>" +
               "<style>" +
               "body {font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #f9f9f9; margin: 0; padding: 0;} " +
               ".container {max-width: 600px; margin: 80px auto; text-align: center;} " +
               ".error-card {background: #fff; border-radius: 6px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); padding: 30px;} " +
               ".error-card h1 {color: #dc3545; margin-bottom: 20px;} " +
               ".error-card p {color: #555; margin-bottom: 20px;} " +
               ".btn-back {padding: 8px 16px; background-color: #007bff; color: #fff; border: none; border-radius: 4px; text-decoration: none;} " +
               ".btn-back:hover {background-color: #0056b3;} " +
               "</style>" +
               "</head><body>" +
               "<div class=\"container\">" +
               "<div class=\"error-card\">" +
               "<h1>Błąd</h1>" +
               "<p>" + escapeHtml(message) + "</p>" +
               "<a href=\"/warehouseQueue\" class=\"btn-back\">Powrót do listy zamówień</a>" +
               "</div>" +
               "</div>" +
               "</body></html>";
    }

    /**
     * Generuje stylizowaną stronę wynikową HTML z podanym komunikatem.
     */
    private String generateResultPage(String message) {
        return "<html><head><meta charset='UTF-8'><title>Wynik</title>" +
               "<style>" +
               "body {font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #f9f9f9; margin: 0; padding: 0;} " +
               ".container {max-width: 600px; margin: 80px auto; text-align: center;} " +
               ".result-card {background: #fff; border-radius: 6px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); padding: 30px;} " +
               ".result-card h1 {color: #28a745; margin-bottom: 20px;} " +
               ".result-card p {color: #555; margin-bottom: 20px;} " +
               ".btn-back {padding: 8px 16px; background-color: #007bff; color: #fff; border: none; border-radius: 4px; text-decoration: none;} " +
               ".btn-back:hover {background-color: #0056b3;} " +
               "</style>" +
               "</head><body>" +
               "<div class=\"container\">" +
               "<div class=\"result-card\">" +
               "<h1>Sukces</h1>" +
               "<p>" + escapeHtml(message) + "</p>" +
               "<a href=\"/warehouseQueue\" class=\"btn-back\">Powrót do listy zamówień</a>" +
               "</div>" +
               "</div>" +
               "</body></html>";
    }

    /**
     * Proste uciekanie znaków HTML aby uniknąć XSS i błędów wyświetlania.
     */
    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#x27;");
    }
}

