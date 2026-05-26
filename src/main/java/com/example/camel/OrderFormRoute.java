package com.example.camel;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;

public class OrderFormRoute extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        // Strona główna sklepu (HTML + CSS + JS)
        from("jetty:http://0.0.0.0:8080/orderForm?httpMethodRestrict=GET")
            .setHeader(Exchange.CONTENT_TYPE, constant("text/html; charset=UTF-8"))
            .setBody(constant("""
                <!DOCTYPE html>
                <html lang="pl">
                <head>
                    <meta charset="UTF-8">
                    <title>Sklep Internetowy</title>
                    <style>
                        body { font-family: Arial, sans-serif; background: #f0f0f0; padding: 20px; }
                        h1 { text-align: center; }
                        .products { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 20px; }
                        .card { background: white; padding: 15px; border-radius: 8px; box-shadow: 0 0 10px rgba(0,0,0,0.1); }
                        .card img { width: 100%; height: 150px; object-fit: cover; }
                        .card h3 { margin: 10px 0 5px; }
                        .card p { font-size: 14px; color: #555; }
                        .card input { width: 60px; margin-top: 5px; }
                        .card button { margin-top: 10px; background: #28a745; color: white; border: none; padding: 8px 12px; border-radius: 4px; cursor: pointer; }
                        .cart { background: #fff; margin-top: 30px; padding: 15px; border-radius: 8px; }
                        .cart h2 { margin-bottom: 10px; }
                        #orderBtn { background: #007bff; color: white; padding: 10px 15px; border: none; border-radius: 4px; cursor: pointer; }
                        #clearCartBtn { background: #dc3545; color: white; padding: 8px 12px; border: none; border-radius: 4px; cursor: pointer; }
                        label { display: block; margin-top: 20px; }
                        input[type=text] { padding: 8px; width: 300px; border-radius: 4px; border: 1px solid #ccc; }
                    </style>
                </head>
                <body>
                    <h1>Sklep Internetowy</h1>
                    <div class="products" id="productList">
                        <!-- Produkty zostaną dodane przez JS -->
                    </div>
                    <div class="cart" id="cart">
                        <h2>🛒 Koszyk</h2>
                        <div id="cartItems">Brak produktów</div>
                        <div id="total">Suma: 0.00 PLN</div>
                        <button id="clearCartBtn">Opróżnij koszyk</button>
                        <button id="orderBtn">Złóż zamówienie</button>
                    </div>
                    <div class="clientData">
                        <label for="firstName">Imię:</label>
                        <input type="text" id="firstName" placeholder="Wpisz imię" required />
                        <label for="lastName">Nazwisko:</label>
                        <input type="text" id="lastName" placeholder="Wpisz nazwisko" required />
                        <label for="address">Adres dostawy:</label>
                        <input type="text" id="address" placeholder="Wpisz adres dostawy" required />
                    </div>
                    <script>
                        const products = [
                            { id: '1',  name: 'Mysz',       desc: 'Bezprzewodowa mysz optyczna',          price: 49.99 },
                            { id: '2',  name: 'Klawiatura', desc: 'Mechaniczna klawiatura RGB',          price: 129.99 },
                            { id: '3',  name: 'Monitor',    desc: '24-calowy monitor Full HD',            price: 499.00 },
                            { id: '4',  name: 'Słuchawki',  desc: 'Słuchawki nauszne z mikrofonem',        price: 89.00 },
                            { id: '5',  name: 'Laptop',     desc: 'Laptop 15.6\" Intel i5, 8GB RAM, 512GB SSD', price: 2499.00 },
                            { id: '6',  name: 'Smartfon',   desc: 'Smartfon 6.5\", 128GB, dual SIM',         price: 1899.00 },
                            { id: '7',  name: 'Drukarka',   desc: 'Drukarka laserowa mono z WiFi',          price: 399.00 },
                            { id: '8',  name: 'Router Wi-Fi', desc: 'Router AC1200 z dwoma antenami',        price: 159.99 },
                            { id: '9',  name: 'Kamera internetowa', desc: 'HD kamera USB do wideokonferencji', price: 109.00 },
                            { id: '10', name: 'Tablet graficzny',      desc: 'Tablet graficzny do rysowania',     price: 249.00 }
                        ];

                        const cart = {};
                        const productList   = document.getElementById("productList");
                        const cartItems     = document.getElementById("cartItems");
                        const totalDisplay  = document.getElementById("total");
                        const firstNameInput = document.getElementById("firstName");
                        const lastNameInput  = document.getElementById("lastName");
                        const addressInput   = document.getElementById("address");

                        products.forEach(p => {
                            const div = document.createElement("div");
                            div.className = "card";
                            div.innerHTML = `
                                <img src="https://via.placeholder.com/250x150?text=${encodeURIComponent(p.name)}" alt="${p.name}">
                                <h3>${p.name}</h3>
                                <p>${p.desc}</p>
                                <p><strong>${p.price.toFixed(2)} PLN</strong></p>
                                <input type="number" id="qty-${p.id}" min="1" value="1">
                                <button onclick="addToCart('${p.id}')">Dodaj do koszyka</button>
                                <button onclick="removeFromCart('${p.id}')"
                                        style="background:#dc3545;color:#fff;border:none;padding:5px 8px;margin-left:10px;border-radius:4px;cursor:pointer;">
                                    Usuń
                                </button>
                            `;
                            productList.appendChild(div);
                        });

                        function addToCart(id) {
                            const product = products.find(p => p.id === id);
                            const qty = parseInt(document.getElementById('qty-' + id).value);
                            if (qty < 1) return;
                            cart[id] = { ...product, quantity: (cart[id]?.quantity || 0) + qty };
                            updateCart();
                        }

                        function removeFromCart(id) {
                            if (cart[id]) {
                                delete cart[id];
                                updateCart();
                            }
                        }

                        function clearCart() {
                            for (const key in cart) {
                                delete cart[key];
                            }
                            updateCart();
                        }

                        function updateCart() {
                            let html = '';
                            let total = 0;
                            for (let key in cart) {
                                const item = cart[key];
                                const subtotal = item.price * item.quantity;
                                total += subtotal;
                                html += `<div>${item.name} x ${item.quantity} = ${subtotal.toFixed(2)} PLN</div>`;
                            }
                            cartItems.innerHTML = html || 'Brak produktów';
                            totalDisplay.innerText = 'Suma: ' + total.toFixed(2) + ' PLN';
                        }

                        document.getElementById("orderBtn").onclick = () => {
                            const payload = Object.values(cart);
                            if (!payload.length)
                                return alert("Koszyk jest pusty!");
                            if (!firstNameInput.value.trim())
                                return alert("Proszę podać imię!");
                            if (!lastNameInput.value.trim())
                                return alert("Proszę podać nazwisko!");
                            if (!addressInput.value.trim())
                                return alert("Proszę podać adres dostawy!");

                            const orderData = {
                                orderId:         Date.now(),
                                clientFirstName: firstNameInput.value.trim(),
                                clientLastName:  lastNameInput.value.trim(),
                                address:         addressInput.value.trim(),
                                items:           payload
                            };

                            fetch('/orders', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify(orderData)
                            })
                            .then(res => res.text())
                            .then(msg => {
                                alert("✅ " + msg);
                                clearCart();
                                firstNameInput.value = '';
                                lastNameInput.value  = '';
                                addressInput.value   = '';
                            })
                            .catch(err => alert("❌ Błąd: " + err));
                        };

                        document.getElementById("clearCartBtn").onclick = clearCart;
                    </script>
                </body>
                </html>
            """));

        // POST /orders - odbiera dane JSON z imieniem, nazwiskiem, adresem i pozycjami zamówienia
        from("jetty:http://0.0.0.0:8080/orders?httpMethodRestrict=POST")
            .streamCaching()
            .convertBodyTo(String.class)
            .log("Odebrano zamówienie (JSON): ${body}")
            .to(ExchangePattern.InOnly, "activemq:queue:zamowienia")
            .setHeader(Exchange.CONTENT_TYPE, constant("text/plain; charset=UTF-8"))
            .setBody(constant("Zamówienie przyjęte i wysłane do kolejki!"));
    }
}

