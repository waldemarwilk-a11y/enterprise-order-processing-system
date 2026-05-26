package com.example.camel;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

public class OrderConsumerRoute extends RouteBuilder {
    // Logger SLF4J do logowania wartości debug
    private static final Logger LOG = LoggerFactory.getLogger(OrderConsumerRoute.class);

    @Override
    public void configure() throws Exception {
        // Nasłuchiwanie na kolejce "zamowienia"
        from("activemq:queue:zamowienia")
            .log("Odebrano zamówienie z kolejki (surowy JSON): ${body}")
            // 1) Deserializujemy JSON do Map<String, Object>
            .unmarshal().json(JsonLibrary.Jackson, Map.class)
            .log("Zdeserializowane zamówienie (Map): ${body}")
            // 2) Pierwszy processor: wstawienie rekordu INTO orders
            .process(exchange -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> orderMap = exchange.getIn().getBody(Map.class);

                // Pobieramy orderId z mapy (JSON: "orderId": ...)
                long orderId = ((Number) orderMap.get("orderId")).longValue();
                // Pobieramy dane klienta (JSON: "clientFirstName", "clientLastName", "address")
                String clientFirstName = orderMap.get("clientFirstName").toString();
                String clientLastName  = orderMap.get("clientLastName").toString();
                String address         = orderMap.get("address").toString();

                // Zapisujemy całą mapę zamówienia w property, żeby była dostępna później
                exchange.setProperty("orderBody", orderMap);

                // Pobranie DataSource z rejestru Camel
                DataSource ds = exchange.getContext()
                                       .getRegistry()
                                       .lookupByNameAndType("dataSource", DataSource.class);

                // Wstawienie rekordu do tabeli orders (order_id, customer_first_name, customer_last_name, address)
                String sqlInsertOrder =
                    "INSERT INTO orders(order_id, customer_first_name, customer_last_name, address) VALUES(?,?,?,?)";
                try (Connection conn = ds.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sqlInsertOrder)) {
                    ps.setLong(1, orderId);
                    ps.setString(2, clientFirstName);
                    ps.setString(3, clientLastName);
                    ps.setString(4, address);
                    ps.executeUpdate();
                }
            })
            // 3) Splitujemy po kluczu "items" (lista pozycji w zamówieniu)
            .split(simple("${body[items]}"))
            // Każda pozycja to Map<String,Object> z kluczami: "id","name","desc","price","quantity"
            .process(exchange -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> item = exchange.getIn().getBody(Map.class);

                // Wartości z pozycji zamówienia
                String productId = item.get("id").toString();
                int quantity     = ((Number) item.get("quantity")).intValue();
                double price     = ((Number) item.get("price")).doubleValue();

                // Pobranie orderId z wcześniej zapisanej property "orderBody"
                @SuppressWarnings("unchecked")
                Map<String, Object> orderMap = exchange.getProperty("orderBody", Map.class);
                long orderId = ((Number) orderMap.get("orderId")).longValue();

                // Pobranie DataSource z rejestru Camel
                DataSource ds = exchange.getContext()
                                       .getRegistry()
                                       .lookupByNameAndType("dataSource", DataSource.class);

                try (Connection conn = ds.getConnection()) {
                    // 3.1) SELECT current_stock z tabeli products
                    String sqlSelect = "SELECT current_stock FROM products WHERE product_id = ?";
                    int currentStock = 0;
                    try (PreparedStatement psSelect = conn.prepareStatement(sqlSelect)) {
                        psSelect.setInt(1, Integer.parseInt(productId));
                        try (ResultSet rs = psSelect.executeQuery()) {
                            if (rs.next()) {
                                currentStock = rs.getInt("current_stock");
                            }
                        }
                    }

                    // → LOG: co faktycznie odczytaliśmy
                    LOG.info("DEBUG: productId={} → currentStock={}", productId, currentStock);

                    // 3.2) Obliczenie nowego stanu magazynowego
                    int newStock = currentStock - quantity;
                    if (newStock < 0) {
                        newStock = 0;
                    }

                    // → LOG: ile powinno zostać po odjęciu quantity
                    LOG.info("DEBUG: quantity={} → newStock={}", quantity, newStock);

                    // 3.3) UPDATE products SET current_stock = newStock
                    String sqlUpdate = "UPDATE products SET current_stock = ? WHERE product_id = ?";
                    try (PreparedStatement psUpdate = conn.prepareStatement(sqlUpdate)) {
                        psUpdate.setInt(1, newStock);
                        psUpdate.setInt(2, Integer.parseInt(productId));
                        psUpdate.executeUpdate();
                    }

                    // → LOG przed wstawieniem do order_items
                    LOG.info("DEBUG: Wstawiam do order_items → orderId={}, productId={}, quantity={}, price={}, stock_level={}",
                             orderId, productId, quantity, price, newStock);

                    // 3.4) INSERT do tabeli order_items (order_id, product_id, quantity, price, stock_level)
                    String sqlInsertItem =
                        "INSERT INTO order_items(order_id, product_id, quantity, price, stock_level) VALUES(?,?,?,?,?)";
                    try (PreparedStatement psInsertItem = conn.prepareStatement(sqlInsertItem)) {
                        psInsertItem.setLong(1, orderId);
                        psInsertItem.setInt(2, Integer.parseInt(productId));
                        psInsertItem.setInt(3, quantity);
                        psInsertItem.setBigDecimal(4, java.math.BigDecimal.valueOf(price));
                        psInsertItem.setInt(5, newStock);
                        psInsertItem.executeUpdate();
                    }

                    // Przechowujemy stock_level w obiekcie pozycji, żeby można to ewentualnie logować
                    item.put("stock_level", newStock);

                    // Po wstawieniu do order_items: wysłanie powiadomienia do kolejki „magazyn_powiadomienia”
                    exchange.getContext()
                            .createProducerTemplate()
                            .sendBody("activemq:queue:magazyn_powiadomienia", orderId);
                }

                // Każda pozycja (item) w body Exchange ma teraz pole "stock_level"
                exchange.getIn().setBody(item);
            })
            .log("Pozycja zapisana: ${body}")
        .end();
    }
}

