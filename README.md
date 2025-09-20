# NSE Stock Analysis Bot

An intelligent Telegram bot for NSE stock analysis and trading recommendations using machine learning algorithms, technical analysis, and news sentiment.

## Features

- **Real-time NSE market data integration** via official APIs
- **Multi-strategy analysis engine** with ML algorithms
- **News sentiment analysis** for comprehensive recommendations
- **Technical pattern recognition** and indicators
- **Comprehensive trading recommendations** with entry/exit points
- **Confidence scoring** and strategy transparency
- **Two main commands:**
  - `/scan` - Analyze top 100 Nifty stocks and get 15-20 best picks
  - `/analyze SYMBOL` - Deep analysis of specific stocks

## Tech Stack

- **Framework:** Spring Boot 3.3.2 with Java 21
- **Database:** PostgreSQL (production) / H2 (development)
- **Cache:** Redis for performance optimization
- **Telegram Bot:** TelegramBots Java library
- **ML Framework:** TensorFlow Java
- **Technical Analysis:** Apache Commons Math
- **Build Tool:** Maven

## Prerequisites

1. **Java 21** or higher
2. **Maven 3.9+** for dependency management
3. **PostgreSQL 17+** (for production)
4. **Redis** (for caching)
5. **Telegram Bot Token** from @BotFather

## Quick Start

### 1. Clone and Setup

```bash
git clone <repository-url>
cd nse-stock-analysis-bot
```

### 2. Configure Environment

Create `application-local.yml` or set environment variables:

```yaml
telegram:
  bot:
    token: YOUR_BOT_TOKEN_HERE
    username: YourBotUsername

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/nse_bot
    username: your_db_user
    password: your_db_password
    
  data:
    redis:
      host: localhost
      port: 6379
```

Or set environment variables:
```bash
export TELEGRAM_BOT_TOKEN=your_bot_token
export TELEGRAM_BOT_USERNAME=your_bot_username
```

### 3. Build and Run

```bash
# Build the application
mvn clean compile

# Run tests
mvn test

# Run the application
mvn spring-boot:run
```

### 4. Create Telegram Bot

1. Message @BotFather on Telegram
2. Send `/newbot`
3. Choose a name and username for your bot
4. Copy the token and add it to your configuration

## Available Commands

### Bot Commands

- `/start` - Welcome message and bot introduction
- `/help` - Display help and command information
- `/scan` - Get top 15-20 stock recommendations from Nifty 100
- `/analyze SYMBOL` - Detailed analysis of specific stock (e.g., `/analyze RELIANCE`)

### Analysis Features

**Market Scan Output:**
- High confidence picks (85%+)
- Medium confidence picks (70-84%)
- Market sentiment analysis
- Real-time recommendations with entry/exit points

**Individual Stock Analysis:**
- Current market data and price trends
- Technical analysis (RSI, MACD, Bollinger Bands, etc.)
- ML-based confidence scoring
- Strategy breakdown and validation
- Entry price, target prices, and stop-loss recommendations
- Time horizon and risk assessment

## Architecture

```
src/main/java/com/nsebot/
├── config/          # Configuration classes
├── controller/      # REST controllers
├── service/         # Business logic services
├── repository/      # Data access layer
├── entity/          # JPA entities
├── dto/             # Data transfer objects
├── client/          # External API clients
├── analysis/        # Stock analysis algorithms
├── exception/       # Exception handling
└── util/            # Utility classes
```

## Database Schema

### Stock Prices
- Real-time and historical stock price data
- Indexed by symbol and timestamp for fast queries

### Stock Analysis
- Analysis results with recommendations
- Confidence scores and strategy validation
- Time-based validity for recommendations

## API Integration

### NSE Data
- Real-time stock quotes
- Market status checking
- Nifty 100 constituents

### Technical Analysis
- RSI (Relative Strength Index)
- MACD (Moving Average Convergence Divergence)
- Bollinger Bands
- Moving Averages
- Volume Analysis

## Development

### Adding New Analysis Strategies

1. Create strategy class implementing analysis interface
2. Add strategy to configuration
3. Update analysis service to include new strategy
4. Add strategy validation in confidence calculation

### Testing

```bash
# Run unit tests
mvn test

# Run with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=test
```

### Database Migration

Using H2 for development:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
  jpa:
    hibernate:
      ddl-auto: create-drop
```

For production PostgreSQL:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/nse_bot
  jpa:
    hibernate:
      ddl-auto: validate
```

## Performance Considerations

- **Caching:** Redis used for frequently accessed stock data
- **Rate Limiting:** NSE API rate limiting implemented
- **Async Processing:** Non-blocking operations for better performance
- **Database Indexing:** Optimized queries with proper indexing

## Security

- **Input Validation:** All user inputs validated and sanitized
- **API Rate Limiting:** Prevents abuse of NSE APIs
- **Error Handling:** Secure error messages without sensitive information
- **Configuration:** Sensitive data via environment variables

## Monitoring

- **Health Checks:** Spring Boot Actuator endpoints
- **Metrics:** Micrometer with Prometheus integration
- **Logging:** Structured logging with appropriate levels

## Deployment

### Docker (Recommended)

```dockerfile
# TODO: Add Dockerfile for containerized deployment
```

### Traditional Deployment

1. Build JAR: `mvn clean package`
2. Run with: `java -jar target/nse-stock-analysis-bot-1.0.0-SNAPSHOT.jar`

## Roadmap

### Phase 1: MVP (Current)
- [x] Basic Telegram bot setup
- [x] NSE API integration
- [x] Core /scan and /analyze commands
- [x] Database entities and repositories
- [x] Error handling framework

### Phase 2: Intelligence Engine
- [ ] Technical analysis algorithms
- [ ] Machine learning model integration
- [ ] News sentiment analysis
- [ ] Confidence scoring system

### Phase 3: Production & Enhancement
- [ ] Performance optimization
- [ ] Advanced user management
- [ ] Portfolio tracking
- [ ] Webhook deployment
- [ ] Monitoring and alerting

## Contributing

1. Fork the repository
2. Create feature branch: `git checkout -b feature/amazing-feature`
3. Commit changes: `git commit -m 'Add amazing feature'`
4. Push to branch: `git push origin feature/amazing-feature`
5. Open a Pull Request

## Disclaimer

⚠️ **Important:** This bot provides analysis for educational and informational purposes only. Always conduct your own research and consult with qualified financial advisors before making investment decisions. Trading in stocks involves risk and you may lose money.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For issues and questions:
1. Check the [Issues](../../issues) section
2. Create a new issue with detailed description
3. Include logs and configuration (without sensitive data)