# Enterprise Order Processing System

An enterprise-style order processing system built with Apache Camel, ActiveMQ, PostgreSQL, and Java. The application demonstrates asynchronous message-driven architecture, enterprise integration patterns (EIP), and end-to-end order processing workflows.

## Overview

This project was created as an integration system for handling e-commerce orders. It demonstrates how modern enterprise applications can decouple services using message queues and integration frameworks.

The system allows customers to place orders through a web-based storefront. Orders are then processed asynchronously through ActiveMQ queues, stored in a PostgreSQL database, and forwarded to a warehouse management module.

Key concepts demonstrated in this project include:

* Enterprise Integration Patterns (EIP)
* Asynchronous messaging
* Apache Camel routing
* JMS communication with ActiveMQ
* REST-style HTTP endpoints
* PostgreSQL database integration
* JSON message processing
* Inventory management workflows

---

## Features

### Online Store

* Product catalog
* Shopping cart functionality
* Customer information form
* Order submission through web interface

### Order Processing

* JSON-based order payloads
* ActiveMQ message queues
* Automatic order persistence
* Inventory stock updates
* Warehouse notifications

### Warehouse Module

* Warehouse order dashboard
* Order status management
* Pending, completed, and cancelled orders
* Warehouse notification processing

### Data Persistence

* PostgreSQL database
* Orders storage
* Order items storage
* Product inventory tracking

---

## Technologies

* Java 17
* Apache Camel 3.20.2
* Apache ActiveMQ
* PostgreSQL 14
* Apache Jetty
* Jackson JSON Processor
* Apache Commons DBCP2
* Maven

---

## Enterprise Integration Patterns Used

* Message Queue
* Splitter
* Message Translator
* Transactional Client
* Content-Based Routing
* Asynchronous Messaging
* Producer / Consumer Pattern

---

## System Architecture

<img width="1122" height="1402" alt="9fa692c4-a12f-4218-b894-08ae4528491b" src="https://github.com/user-attachments/assets/ceec705b-dab0-44c9-a539-7ee7c150bc35" />

---

## Project Structure

```text
enterprise-order-processing-system/
│
├── pom.xml
│
├── src/
│   └── main/
│       ├── java/
│       │   └── com/example/camel/
│       │       ├── MainApp.java
│       │       ├── OrderFormRoute.java
│       │       ├── OrderConsumerRoute.java
│       │       └── WarehouseConsumerRoute.java
│       │
│       └── resources/
│
├── images/
│   ├── architecture.png
│   ├── storefront.png
│   ├── activemq.png
│   ├── camel-processing.png
│   ├── database.png
│   └── warehouse-dashboard.png
│
└── README.md
```

---

## Screenshots

### Online Store

<img width="739" height="851" alt="Zrzut ekranu 2026-06-05 043022" src="https://github.com/user-attachments/assets/52825ede-bb92-406b-a31a-4c975e22c3ec" />

---

### ActiveMQ Queue Monitoring

<img width="873" height="689" alt="Zrzut ekranu 2026-06-05 043406" src="https://github.com/user-attachments/assets/c954a748-d1a6-425e-a0b0-e5fe17a1faf5" />

---

### Apache Camel Order Processing

<img width="1481" height="673" alt="Zrzut ekranu 2026-06-05 044145" src="https://github.com/user-attachments/assets/f95447aa-b272-4952-97cc-584a060e76a9" />

---

### PostgreSQL Database

<img width="1879" height="595" alt="Zrzut ekranu 2026-06-05 044231" src="https://github.com/user-attachments/assets/a305dfae-d650-4b1e-b6da-e30588d8e5a4" />

---

### Warehouse Dashboard

<img width="865" height="900" alt="Zrzut ekranu 2026-06-05 044332" src="https://github.com/user-attachments/assets/4f53e931-1183-49e2-b8f2-20a62c3b05c6" />

---

## Order Processing Flow

1. Customer opens the online store.
2. Products are added to the shopping cart.
3. Customer submits an order.
4. Order is converted into a JSON payload.
5. JSON message is sent to the ActiveMQ queue (`zamowienia`).
6. Apache Camel consumes the message.
7. Order data is stored in PostgreSQL.
8. Inventory levels are updated.
9. Warehouse notification is sent to the `magazyn_powiadomienie` queue.
10. Warehouse module receives the notification.
11. Order becomes available for warehouse processing.

---

## Database Schema

The system uses three main tables:

### products

Stores product information and inventory levels.

### orders

Stores customer order information.

### order_items

Stores products associated with individual orders.

---

## How to Run

### Prerequisites

* Java 17
* Maven 3.8+
* PostgreSQL 14
* Apache ActiveMQ 5.x

### Database Setup

Create database:

```sql
CREATE DATABASE shopdb;
```

Create user:

```sql
CREATE USER shopuser WITH ENCRYPTED PASSWORD 'shop_pass';
GRANT ALL PRIVILEGES ON DATABASE shopdb TO shopuser;
```

Create required tables:

* products
* orders
* order_items

---

### Start ActiveMQ

Start ActiveMQ broker:

```bash
activemq start
```

ActiveMQ Web Console:

```text
http://localhost:8161/admin
```

Default credentials:

```text
admin
admin
```

---

### Run the Application

Build project:

```bash
mvn clean package
```

Start application:

```bash
mvn camel:run
```

Storefront:

```text
http://localhost:8080/orderForm
```

Warehouse Dashboard:

```text
http://localhost:8080/warehouseQueue
```

---

## Learning Outcomes

This project provided practical experience with:

* Enterprise Integration Patterns
* Apache Camel routing
* JMS messaging systems
* ActiveMQ broker configuration
* PostgreSQL integration
* REST and HTTP communication
* Asynchronous system architecture
* Inventory and order processing workflows

---

## Future Improvements

Potential enhancements include:

* REST API documentation (OpenAPI)
* Docker containerization
* Dead Letter Queue implementation
* Email notifications
* Authentication and authorization
* Order tracking system
* Inventory analytics dashboard
* Microservices deployment

---

## Author

Waldemar Wilk

Personal portfolio project demonstrating enterprise application integration using Apache Camel, ActiveMQ, PostgreSQL, and message-driven architecture.
