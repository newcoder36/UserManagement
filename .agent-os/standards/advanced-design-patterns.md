# Advanced Design Patterns & Functional Programming Standards

## Context
Advanced design patterns and functional programming guidelines for TradeMaster project following Java 24 features and modern architectural principles.

## Core Principles Hierarchy

### 1. SOLID Principles (Mandatory)
- **Single Responsibility**: Each class has one reason to change
- **Open/Closed**: Open for extension, closed for modification
- **Liskov Substitution**: Derived classes must be substitutable
- **Interface Segregation**: Clients depend only on methods they use
- **Dependency Inversion**: Depend on abstractions, not concretions

### 2. DRY, KISS, LoD (Law of Demeter)
- **DRY (Don't Repeat Yourself)**: Abstract common functionality
- **KISS (Keep It Simple Stupid)**: Simplicity over complexity
- **LoD (Law of Demeter)**: Objects should only talk to immediate neighbors

### 3. Functional Programming First
- **Architectural fitness over blind rules**: Replace imperative constructs (if-else, loops) only when architecturally beneficial
- **Immutable data structures**: Records, sealed classes, persistent collections
- **Higher-order functions**: Function composition, method references
- **Monadic patterns**: Optional, CompletableFuture, Result types
- **Virtual threads integration**: Combine functional patterns with virtual threads for scalable concurrency

### 4. Modern Concurrency Patterns
- **Virtual threads by default**: Use virtual threads for I/O-bound operations
- **Structured concurrency**: Organize concurrent operations with clear lifecycle
- **Lock-free algorithms**: Prefer atomic operations and concurrent collections
- **Actor-like patterns**: Message passing with virtual thread channels
- **Reactive streams integration**: Non-blocking data processing pipelines

## Advanced Design Patterns Implementation

### Virtual Threads & Modern Concurrency (Java 24)

#### Virtual Thread Configuration
```java
@Configuration
@EnableAsync
public class VirtualThreadConfiguration {
    
    @Bean("applicationTaskExecutor")
    @Primary
    public AsyncTaskExecutor applicationTaskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
        executor.setVirtualThreads(true);  // Enable virtual threads
        executor.setThreadNamePrefix("vt-async-");
        return executor;
    }
    
    @Bean
    public TaskScheduler taskScheduler() {
        SimpleAsyncTaskExecutor scheduler = new SimpleAsyncTaskExecutor();
        scheduler.setVirtualThreads(true);
        scheduler.setThreadNamePrefix("vt-scheduler-");
        return scheduler;
    }
}
```

#### Virtual Thread Patterns
```java
// Virtual Thread Factory Pattern
public enum VirtualThreadFactory {
    INSTANCE;
    
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    
    public CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task, executor);
    }
    
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }
}

// Structured Concurrency with Virtual Threads
public class StructuredConcurrencyService {
    
    public CompletableFuture<CombinedResult> fetchMultipleDataSources(String symbol) {
        return VirtualThreadFactory.INSTANCE.supplyAsync(() -> {
            try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                
                var priceTask = scope.fork(() -> fetchPrice(symbol));
                var volumeTask = scope.fork(() -> fetchVolume(symbol));
                var newsTask = scope.fork(() -> fetchNews(symbol));
                
                scope.join();           // Wait for all tasks
                scope.throwIfFailed();  // Propagate any failures
                
                return CombinedResult.builder()
                    .price(priceTask.resultNow())
                    .volume(volumeTask.resultNow())
                    .news(newsTask.resultNow())
                    .build();
            }
        });
    }
}
```

#### Lock-Free Patterns with Virtual Threads
```java
// Lock-free market data aggregation
public class LockFreeMarketDataAggregator {
    private final ConcurrentHashMap<String, AtomicReference<MarketData>> latestData = new ConcurrentHashMap<>();
    private final EventPublisher<MarketData> publisher = new EventPublisher<>();
    
    public CompletableFuture<Void> updateMarketData(MarketData newData) {
        return VirtualThreadFactory.INSTANCE.runAsync(() -> {
            String key = newData.symbol();
            
            // Lock-free compare-and-swap update
            latestData.compute(key, (k, existing) -> {
                if (existing == null || newData.timestamp().isAfter(existing.get().timestamp())) {
                    AtomicReference<MarketData> ref = new AtomicReference<>(newData);
                    publisher.publish(newData);  // Notify subscribers
                    return ref;
                }
                return existing;
            });
        });
    }
}
```

#### Reactive Streams with Virtual Threads
```java
// Non-blocking reactive pipeline using virtual threads
public class VirtualThreadReactiveService {
    
    public Stream<MarketData> processMarketDataStream(Stream<String> symbols) {
        return symbols
            .parallel()  // Use parallel streams with virtual threads
            .map(symbol -> VirtualThreadFactory.INSTANCE.supplyAsync(() -> fetchMarketData(symbol)))
            .map(CompletableFuture::join)
            .filter(Optional::isPresent)
            .map(Optional::get);
    }
    
    // Channel-like pattern for async communication
    public static class VirtualThreadChannel<T> {
        private final BlockingQueue<T> queue = new LinkedBlockingQueue<>();
        private volatile boolean closed = false;
        
        public CompletableFuture<Void> send(T item) {
            return VirtualThreadFactory.INSTANCE.runAsync(() -> {
                if (!closed) {
                    try {
                        queue.put(item);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }
        
        public Stream<T> receive() {
            return Stream.generate(() -> {
                try {
                    return queue.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            })
            .takeWhile(Objects::nonNull);
        }
    }
}
```

### 1. Creational Patterns

#### Factory Method (Functional)
```java
@FunctionalInterface
public interface ProviderFactory<T extends MarketDataProvider> {
    T create(ProviderConfig config);
}

public enum ProviderType {
    ALPHA_VANTAGE(AlphaVantageProvider::new),
    YAHOO_FINANCE(YahooFinanceProvider::new),
    FREE_API(FreeApiProvider::new);
    
    private final ProviderFactory<? extends MarketDataProvider> factory;
    
    ProviderType(ProviderFactory<? extends MarketDataProvider> factory) {
        this.factory = factory;
    }
    
    public MarketDataProvider create(ProviderConfig config) {
        return factory.create(config);
    }
}
```

#### Builder Pattern (Records + Functional)
```java
public record MarketDataRequest(
    String symbol,
    String exchange,
    LocalDateTime from,
    LocalDateTime to,
    Set<DataType> dataTypes
) {
    public static MarketDataRequestBuilder builder() {
        return new MarketDataRequestBuilder();
    }
    
    public static class MarketDataRequestBuilder {
        private String symbol;
        private String exchange = "NSE";
        private LocalDateTime from = LocalDateTime.now().minusDays(1);
        private LocalDateTime to = LocalDateTime.now();
        private Set<DataType> dataTypes = EnumSet.of(DataType.PRICE);
        
        public MarketDataRequestBuilder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }
        
        public MarketDataRequestBuilder exchange(String exchange) {
            this.exchange = exchange;
            return this;
        }
        
        public MarketDataRequest build() {
            return new MarketDataRequest(symbol, exchange, from, to, dataTypes);
        }
    }
}
```

### 2. Structural Patterns

#### Adapter Pattern (Functional Interface)
```java
@FunctionalInterface
public interface DataAdapter<FROM, TO> {
    TO adapt(FROM source);
    
    default <NEXT> DataAdapter<FROM, NEXT> andThen(DataAdapter<TO, NEXT> next) {
        return source -> next.adapt(this.adapt(source));
    }
}

// Usage
DataAdapter<AlphaVantageResponse, MarketData> alphaAdapter = AlphaVantageResponse::toMarketData;
DataAdapter<YahooResponse, MarketData> yahooAdapter = YahooResponse::toMarketData;
```

#### Decorator Pattern (Functional Composition)
```java
@FunctionalInterface
public interface PriceEnhancer {
    MarketData enhance(MarketData data);
    
    default PriceEnhancer andThen(PriceEnhancer after) {
        return data -> after.enhance(this.enhance(data));
    }
}

// Usage
PriceEnhancer cacheEnhancer = data -> cacheService.cache(data);
PriceEnhancer validationEnhancer = data -> validator.validate(data);
PriceEnhancer auditEnhancer = data -> auditService.audit(data);

PriceEnhancer fullEnhancer = cacheEnhancer
    .andThen(validationEnhancer)
    .andThen(auditEnhancer);
```

### 3. Behavioral Patterns

#### Strategy Pattern (No if-else)
```java
public enum PricingStrategy {
    REAL_TIME(data -> calculateRealTimePrice(data)),
    DELAYED(data -> calculateDelayedPrice(data)),
    CACHED(data -> getCachedPrice(data)),
    FALLBACK(data -> getFallbackPrice(data));
    
    private final Function<MarketDataRequest, CompletableFuture<Price>> calculator;
    
    PricingStrategy(Function<MarketDataRequest, CompletableFuture<Price>> calculator) {
        this.calculator = calculator;
    }
    
    public CompletableFuture<Price> calculate(MarketDataRequest request) {
        return calculator.apply(request);
    }
    
    private static CompletableFuture<Price> calculateRealTimePrice(MarketDataRequest data) {
        // Implementation
    }
}
```

#### Command Pattern (Functional)
```java
@FunctionalInterface
public interface Command<T> {
    CompletableFuture<T> execute();
    
    default <U> Command<U> map(Function<T, U> mapper) {
        return () -> this.execute().thenApply(mapper);
    }
    
    default Command<T> retry(int attempts) {
        return () -> executeWithRetry(this, attempts);
    }
}

// Usage
Command<MarketData> getPrice = () -> priceService.getCurrentPrice(symbol);
Command<String> priceString = getPrice
    .map(MarketData::toString)
    .retry(3);
```

#### Chain of Responsibility (Functional)
```java
@FunctionalInterface
public interface ValidationChain<T> {
    ValidationResult validate(T input);
    
    default ValidationChain<T> andThen(ValidationChain<T> next) {
        return input -> {
            ValidationResult result = this.validate(input);
            return result.isValid() ? next.validate(input) : result;
        };
    }
}

// Usage
ValidationChain<MarketDataRequest> symbolValidator = request -> 
    Optional.of(request.symbol())
        .filter(s -> !s.isBlank())
        .map(s -> ValidationResult.valid())
        .orElse(ValidationResult.invalid("Symbol cannot be blank"));

ValidationChain<MarketDataRequest> exchangeValidator = request ->
    Optional.of(request.exchange())
        .filter(SUPPORTED_EXCHANGES::contains)
        .map(e -> ValidationResult.valid())
        .orElse(ValidationResult.invalid("Unsupported exchange"));

ValidationChain<MarketDataRequest> fullValidation = symbolValidator
    .andThen(exchangeValidator);
```

#### Observer Pattern (Functional)
```java
public class EventPublisher<T> {
    private final Set<Consumer<T>> observers = ConcurrentHashMap.newKeySet();
    
    public void subscribe(Consumer<T> observer) {
        observers.add(observer);
    }
    
    public void publish(T event) {
        observers.parallelStream()
            .forEach(observer -> observer.accept(event));
    }
}

// Usage
EventPublisher<MarketData> pricePublisher = new EventPublisher<>();
pricePublisher.subscribe(data -> portfolioService.updatePosition(data));
pricePublisher.subscribe(data -> alertService.checkAlerts(data));
pricePublisher.subscribe(data -> analyticsService.recordPrice(data));
```

## Functional Programming Patterns

### 1. Eliminate if-else with Functional Alternatives

#### Replace if-else with Optional
```java
// BAD: if-else
public String getProviderName(ProviderType type) {
    if (type == ProviderType.ALPHA_VANTAGE) {
        return "Alpha Vantage";
    } else if (type == ProviderType.YAHOO) {
        return "Yahoo Finance";
    } else {
        return "Unknown";
    }
}

// GOOD: Functional with Map
private static final Map<ProviderType, String> PROVIDER_NAMES = Map.of(
    ProviderType.ALPHA_VANTAGE, "Alpha Vantage",
    ProviderType.YAHOO, "Yahoo Finance",
    ProviderType.FREE_API, "Free API"
);

public String getProviderName(ProviderType type) {
    return Optional.ofNullable(PROVIDER_NAMES.get(type))
        .orElse("Unknown");
}
```

#### Replace if-else with Stream API
```java
// BAD: if-else for filtering
public List<MarketData> filterData(List<MarketData> data, FilterCriteria criteria) {
    List<MarketData> result = new ArrayList<>();
    for (MarketData item : data) {
        if (criteria.getMinPrice() != null && item.price() < criteria.getMinPrice()) {
            continue;
        }
        if (criteria.getMaxPrice() != null && item.price() > criteria.getMaxPrice()) {
            continue;
        }
        if (criteria.getSymbols() != null && !criteria.getSymbols().contains(item.symbol())) {
            continue;
        }
        result.add(item);
    }
    return result;
}

// GOOD: Functional with Streams
public List<MarketData> filterData(List<MarketData> data, FilterCriteria criteria) {
    return data.stream()
        .filter(item -> Optional.ofNullable(criteria.getMinPrice())
            .map(min -> item.price() >= min)
            .orElse(true))
        .filter(item -> Optional.ofNullable(criteria.getMaxPrice())
            .map(max -> item.price() <= max)
            .orElse(true))
        .filter(item -> Optional.ofNullable(criteria.getSymbols())
            .map(symbols -> symbols.contains(item.symbol()))
            .orElse(true))
        .toList();
}
```

#### Replace if-else with Pattern Matching (Java 24)
```java
// GOOD: Pattern matching with sealed classes
public sealed interface MarketDataEvent permits PriceUpdate, VolumeUpdate, TradingHalt {}

public record PriceUpdate(String symbol, double price, LocalDateTime timestamp) implements MarketDataEvent {}
public record VolumeUpdate(String symbol, long volume, LocalDateTime timestamp) implements MarketDataEvent {}
public record TradingHalt(String symbol, String reason, LocalDateTime timestamp) implements MarketDataEvent {}

public String processEvent(MarketDataEvent event) {
    return switch (event) {
        case PriceUpdate(var symbol, var price, var timestamp) -> 
            "Price update for %s: %.2f at %s".formatted(symbol, price, timestamp);
        case VolumeUpdate(var symbol, var volume, var timestamp) -> 
            "Volume update for %s: %d at %s".formatted(symbol, volume, timestamp);
        case TradingHalt(var symbol, var reason, var timestamp) -> 
            "Trading halt for %s: %s at %s".formatted(symbol, reason, timestamp);
    };
}
```

### 2. Monadic Patterns

#### Result Type (Railway Oriented Programming)
```java
public sealed interface Result<T, E> {
    record Success<T, E>(T value) implements Result<T, E> {}
    record Failure<T, E>(E error) implements Result<T, E> {}
    
    static <T, E> Result<T, E> success(T value) {
        return new Success<>(value);
    }
    
    static <T, E> Result<T, E> failure(E error) {
        return new Failure<>(error);
    }
    
    default <U> Result<U, E> map(Function<T, U> mapper) {
        return switch (this) {
            case Success(var value) -> success(mapper.apply(value));
            case Failure(var error) -> failure(error);
        };
    }
    
    default <U> Result<U, E> flatMap(Function<T, Result<U, E>> mapper) {
        return switch (this) {
            case Success(var value) -> mapper.apply(value);
            case Failure(var error) -> failure(error);
        };
    }
}

// Usage
public Result<MarketData, String> getMarketData(String symbol) {
    return validateSymbol(symbol)
        .flatMap(this::fetchFromProvider)
        .flatMap(this::validateData)
        .map(this::enrichData);
}
```

#### Try-Catch Functional Wrapper
```java
@FunctionalInterface
public interface ThrowingFunction<T, R, E extends Exception> {
    R apply(T t) throws E;
    
    static <T, R, E extends Exception> Function<T, Optional<R>> lift(ThrowingFunction<T, R, E> fn) {
        return t -> {
            try {
                return Optional.of(fn.apply(t));
            } catch (Exception e) {
                return Optional.empty();
            }
        };
    }
    
    static <T, R, E extends Exception> Function<T, Result<R, String>> liftToResult(ThrowingFunction<T, R, E> fn) {
        return t -> {
            try {
                return Result.success(fn.apply(t));
            } catch (Exception e) {
                return Result.failure(e.getMessage());
            }
        };
    }
}

// Usage
Function<String, Optional<MarketData>> safeGetPrice = 
    ThrowingFunction.lift(this::getPriceFromApi);
```

## Error Handling Patterns

### 1. Functional Error Handling (No try-catch blocks)
```java
public class SafeOperations {
    public static <T> Optional<T> safely(Supplier<T> operation) {
        try {
            return Optional.ofNullable(operation.get());
        } catch (Exception e) {
            return Optional.empty();
        }
    }
    
    public static <T> CompletableFuture<Optional<T>> safelyAsync(Supplier<T> operation) {
        return CompletableFuture.supplyAsync(() -> safely(operation));
    }
}

// Usage
Optional<MarketData> data = SafeOperations.safely(() -> apiClient.getPrice(symbol));
```

## Performance Patterns

### 1. Lazy Evaluation
```java
public class LazyMarketDataProvider {
    private final Supplier<MarketData> lazyData = Suppliers.memoize(() -> {
        // Expensive computation
        return fetchMarketDataExpensively();
    });
    
    public MarketData getData() {
        return lazyData.get();
    }
}
```

### 2. Parallel Processing with Virtual Threads
```java
public CompletableFuture<List<MarketData>> getMultipleSymbols(List<String> symbols) {
    return CompletableFuture.supplyAsync(() -> 
        symbols.parallelStream()
            .map(this::getMarketData)
            .map(CompletableFuture::join)
            .toList()
    );
}
```

## Testing Patterns

### 1. Functional Test Builders
```java
public class MarketDataTestBuilder {
    public static MarketData.Builder validMarketData() {
        return MarketData.builder()
            .symbol("AAPL")
            .exchange("NASDAQ")
            .price(150.0)
            .timestamp(LocalDateTime.now());
    }
    
    public static MarketData.Builder withSymbol(String symbol) {
        return validMarketData().symbol(symbol);
    }
}
```

## Naming Conventions

### 1. Functional Style
- **Predicates**: `isValid`, `hasValue`, `canProcess`
- **Functions**: `transform`, `convert`, `map`, `filter`
- **Suppliers**: `provide`, `create`, `generate`
- **Consumers**: `process`, `handle`, `accept`

### 2. Domain-Specific
- **Market Data**: `PriceProvider`, `DataEnricher`, `MarketValidator`
- **Trading**: `OrderProcessor`, `PositionManager`, `RiskCalculator`
- **Portfolio**: `ValueCalculator`, `PerformanceTracker`, `AssetAllocator`

## Code Organization Patterns

### 1. Feature-Based Packages
```
com.trademaster.marketdata/
├── provider/
│   ├── ProviderFactory.java
│   ├── ProviderStrategy.java
│   └── impl/
├── enrichment/
│   ├── DataEnricher.java
│   └── EnrichmentPipeline.java
├── validation/
│   ├── ValidationChain.java
│   └── Validators.java
└── streaming/
    ├── EventPublisher.java
    └── StreamProcessor.java
```

### 2. Clean Architecture Layers
```
com.trademaster/
├── domain/          # Entities, Value Objects, Domain Services
├── application/     # Use Cases, Application Services
├── infrastructure/  # External APIs, Databases, Messaging
└── presentation/    # Controllers, DTOs, Mappers
```

## Implementation Checklist

### Before Writing Code
- [ ] No if-else statements (use functional alternatives)
- [ ] Apply at least 2 design patterns per class
- [ ] Follow SOLID + DRY + LoD principles
- [ ] Use immutable data structures (Records)
- [ ] Implement proper error handling (Result types)
- [ ] Use function composition over inheritance
- [ ] Apply lazy evaluation where appropriate
- [ ] Ensure thread safety with functional approaches

### Code Review Checklist
- [ ] All conditionals replaced with functional patterns
- [ ] Proper separation of concerns (SRP)
- [ ] Dependencies inverted (DIP)
- [ ] No repeated code (DRY)
- [ ] Limited object interaction chains (LoD)
- [ ] Proper use of Optional, CompletableFuture
- [ ] Pattern matching used for complex conditionals
- [ ] Function composition over complex methods