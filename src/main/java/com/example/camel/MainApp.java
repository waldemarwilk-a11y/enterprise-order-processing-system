package com.example.camel;

import org.apache.camel.main.Main;
import org.apache.commons.dbcp2.BasicDataSource;

public class MainApp {
    public static void main(String[] args) throws Exception {
        Main main = new Main();

        // Konfiguracja DataSource dla PostgreSQL
        BasicDataSource ds = new BasicDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl("jdbc:postgresql://localhost:5432/shopdb");
        ds.setUsername("shopuser");
        ds.setPassword("shop_pass");

        // Rejestrujemy DataSource w rejestrze Camel pod nazwą "dataSource"
        main.bind("dataSource", ds);

        // Dodajemy trasy: frontendową, konsumencką oraz magazynową
        main.configure().addRoutesBuilder(new OrderFormRoute());
        main.configure().addRoutesBuilder(new OrderConsumerRoute());
        main.configure().addRoutesBuilder(new WarehouseConsumerRoute());

        main.run(args);
    }
}

