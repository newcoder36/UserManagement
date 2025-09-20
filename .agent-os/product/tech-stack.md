# Technical Stack

> Last Updated: 2025-08-25
> Version: 1.0.0

## Application Framework

- **Framework:** Spring Boot 3.5.x
- **Version:** Java 24
- **Features:** Virtual Threads enabled for high-concurrency stock processing

## Database

- **Primary Database:** PostgreSQL 17+
- **Extensions:** TimescaleDB for time series processing
- **Use Case:** Stock price data, user portfolios, trading history

## JavaScript

- **Framework:** N/A (Java backend only)

## CSS Framework

- **Framework:** N/A (Telegram bot interface)

## UI Component Library

- **Library:** N/A (Telegram bot interface)

## Fonts & Icons

- **Fonts Provider:** N/A (no web UI)
- **Icon Library:** N/A (no web UI)

## Hosting & Infrastructure

- **Application Hosting:** Digital Ocean App Platform
- **Database Hosting:** Digital Ocean Managed PostgreSQL
- **Asset Hosting:** Amazon S3 for ML models and historical data

## Deployment & CI/CD

- **Deployment Solution:** GitHub Actions CI/CD pipeline
- **Code Repository:** GitHub (nse-stock-analysis-bot)

## Dependency Management

- **Import Strategy:** Maven 3.9+ dependency management

## Financial Trading Specific Stack

### Messaging & Communication

- **Bot Framework:** TelegramBots Java library
- **API Integration:** Telegram Bot API

### Machine Learning

- **ML Framework:** TensorFlow Java for analysis algorithms
- **Models Storage:** Amazon S3 for ML models

### Data Processing

- **Message Queue:** Redis for real-time notifications
- **Caching:** Redis for frequently accessed stock data
- **Time Series:** TimescaleDB extension for PostgreSQL

### Market Data

- **Market Data APIs:** NSE official APIs + backup sources
- **Data Storage:** PostgreSQL with TimescaleDB for historical data

### Security & Authentication

- **Security Framework:** Spring Security 6+
- **Authentication:** JWT authentication

### Monitoring & Observability

- **Metrics:** Micrometer with Prometheus metrics
- **Performance:** Real-time stock processing monitoring

### Concurrency & Performance

- **Threading:** Virtual Threads for high-concurrency stock processing
- **Caching Strategy:** Redis for frequently accessed stock data
- **Queue Management:** Redis for real-time notifications