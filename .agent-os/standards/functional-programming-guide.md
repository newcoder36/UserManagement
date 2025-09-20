# Functional Programming Guide - Java 24

## Context
Comprehensive functional programming standards for TradeMaster using Java 24 features, virtual threads, structured concurrency, and modern patterns. Focus on architectural fitness - replace imperative constructs only when functionally beneficial.

## Architectural Philosophy

### When to Apply Functional Patterns
- **Replace if-else**: When pattern matching or strategy patterns improve clarity and maintainability
- **Replace loops**: When stream operations or recursive patterns provide better abstraction
- **Introduce immutability**: When data consistency and thread safety are critical
- **Apply concurrency**: When virtual threads and structured concurrency enhance performance
- **Use monadic patterns**: When error handling and null safety improve code reliability

### When NOT to Force Functional Patterns
- Simple conditional logic that is clear and performant
- Single-pass iterations over small collections
- Performance-critical code where imperative style is measurably faster
- Legacy integration points where functional approach adds complexity

## Virtual Threads Integration

### Virtual Thread-Aware Functional Patterns
```java
// Virtual thread-enabled functional pipeline
public class VirtualThreadFunctionalProcessor {
    
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    
    // Functional pipeline with virtual thread parallelization
    public <T, R> CompletableFuture<List<R>> processInParallel(
            List<T> items, 
            Function<T, R> processor) {
        
        return CompletableFuture.supplyAsync(() -> 
            items.parallelStream()
                .map(item -> CompletableFuture.supplyAsync(() -> processor.apply(item), virtualExecutor))
                .map(CompletableFuture::join)
                .toList()
        );
    }
    
    // Structured concurrency with functional composition
    public <A, B, C, R> CompletableFuture<R> combineAsync(
            Supplier<A> taskA,
            Supplier<B> taskB, 
            Supplier<C> taskC,
            Function3<A, B, C, R> combiner) {
        
        return CompletableFuture.supplyAsync(() -> {
            try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                var futureA = scope.fork(taskA::get);
                var futureB = scope.fork(taskB::get);
                var futureC = scope.fork(taskC::get);
                
                scope.join();
                scope.throwIfFailed();
                
                return combiner.apply(
                    futureA.resultNow(),
                    futureB.resultNow(),
                    futureC.resultNow()
                );
            }
        }, virtualExecutor);
    }
}

@FunctionalInterface
public interface Function3<A, B, C, R> {
    R apply(A a, B b, C c);
}
```

### Lock-Free Functional Data Structures
```java
// Immutable market data cache with functional updates
public class FunctionalMarketDataCache {
    
    private final AtomicReference<Map<String, MarketData>> cache = 
        new AtomicReference<>(Map.of());
    
    // Functional cache update
    public CompletableFuture<MarketData> updateAsync(
            String symbol, 
            Function<Optional<MarketData>, MarketData> updater) {
        
        return CompletableFuture.supplyAsync(() -> {
            while (true) {
                Map<String, MarketData> current = cache.get();
                Optional<MarketData> existing = Optional.ofNullable(current.get(symbol));
                MarketData newData = updater.apply(existing);
                
                Map<String, MarketData> updated = new HashMap<>(current);
                updated.put(symbol, newData);
                
                if (cache.compareAndSet(current, Map.copyOf(updated))) {
                    return newData;
                }
                // Retry on CAS failure
            }
        });
    }
    
    // Functional query with transformation
    public <R> Optional<R> query(String symbol, Function<MarketData, R> transformer) {
        return Optional.ofNullable(cache.get().get(symbol))
            .map(transformer);
    }
}
```

## Core Functional Principles

### 1. Immutability First
All data structures should be immutable by default using Records and sealed classes. Combine with virtual threads for thread-safe concurrent operations.

```java
// GOOD: Immutable data with Records
public record MarketData(
    String symbol,
    String exchange,
    double price,
    long volume,
    LocalDateTime timestamp,
    Optional<Double> change,
    Optional<Double> changePercent
) {
    // Validation in compact constructor
    public MarketData {
        Objects.requireNonNull(symbol, "Symbol cannot be null");
        Objects.requireNonNull(exchange, "Exchange cannot be null");
        if (price < 0) throw new IllegalArgumentException("Price cannot be negative");
    }
}

// GOOD: Sealed hierarchies for type safety
public sealed interface OrderResult permits OrderSuccess, OrderFailure {
    record OrderSuccess(String orderId, LocalDateTime executedAt, double executedPrice) implements OrderResult {}
    record OrderFailure(String errorCode, String message, LocalDateTime failedAt) implements OrderResult {}
}
```

### 2. Function Composition Over Method Chaining
Build complex operations by composing simple functions.

```java
// Function composition utilities
public final class Functions {
    
    public static <T, R> Function<T, R> compose(Function<T, R>... functions) {
        return Arrays.stream(functions)
            .reduce(Function.identity(), Function::compose);
    }
    
    public static <T> Predicate<T> and(Predicate<T>... predicates) {
        return Arrays.stream(predicates)
            .reduce(Predicate::and)
            .orElse(t -> true);
    }
    
    public static <T> Predicate<T> or(Predicate<T>... predicates) {
        return Arrays.stream(predicates)
            .reduce(Predicate::or)
            .orElse(t -> false);
    }
}

// Usage example
public class MarketDataProcessor {
    
    private final Function<RawData, MarketData> processData = compose(
        this::validateData,
        this::normalizeData,
        this::enrichData,
        this::cacheData
    );
    
    public MarketData process(RawData rawData) {
        return processData.apply(rawData);
    }
}
```

### 3. Higher-Order Functions
Functions that take other functions as parameters or return functions.

```java
public class HigherOrderFunctions {
    
    // Retry function - takes a function and returns a new function with retry logic
    public static <T, R> Function<T, R> withRetry(
            Function<T, R> function, 
            int maxAttempts,
            Duration delay) {
        return input -> {
            Exception lastException = null;
            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                try {
                    return function.apply(input);
                } catch (Exception e) {
                    lastException = e;
                    if (attempt < maxAttempts - 1) {
                        try {
                            Thread.sleep(delay.toMillis());
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(ie);
                        }
                    }
                }
            }
            throw new RuntimeException("Failed after " + maxAttempts + " attempts", lastException);
        };
    }
    
    // Memoization function
    public static <T, R> Function<T, R> memoize(Function<T, R> function) {
        return new Function<>() {
            private final Map<T, R> cache = new ConcurrentHashMap<>();
            
            @Override
            public R apply(T input) {
                return cache.computeIfAbsent(input, function);
            }
        };
    }
    
    // Circuit breaker function
    public static <T, R> Function<T, Optional<R>> withCircuitBreaker(
            Function<T, R> function,
            int failureThreshold,
            Duration timeout) {
        return new CircuitBreakerFunction<>(function, failureThreshold, timeout);
    }
}
```

### 4. Monadic Patterns Implementation

#### Maybe/Optional Pattern
```java
public final class Maybe<T> {
    private final Optional<T> value;
    
    private Maybe(T value) {
        this.value = Optional.ofNullable(value);
    }
    
    public static <T> Maybe<T> of(T value) {
        return new Maybe<>(value);
    }
    
    public static <T> Maybe<T> empty() {
        return new Maybe<>(null);
    }
    
    public <U> Maybe<U> map(Function<T, U> mapper) {
        return value.map(mapper).map(Maybe::of).orElse(empty());
    }
    
    public <U> Maybe<U> flatMap(Function<T, Maybe<U>> mapper) {
        return value.map(mapper).orElse(empty());
    }
    
    public Maybe<T> filter(Predicate<T> predicate) {
        return value.filter(predicate).map(Maybe::of).orElse(empty());
    }
    
    public T orElse(T defaultValue) {
        return value.orElse(defaultValue);
    }
    
    public T orElseGet(Supplier<T> supplier) {
        return value.orElseGet(supplier);
    }
    
    public boolean isPresent() {
        return value.isPresent();
    }
}
```

#### Either Pattern (Left/Right)
```java
public sealed interface Either<L, R> {
    record Left<L, R>(L value) implements Either<L, R> {}
    record Right<L, R>(R value) implements Either<L, R> {}
    
    static <L, R> Either<L, R> left(L value) {
        return new Left<>(value);
    }
    
    static <L, R> Either<L, R> right(R value) {
        return new Right<>(value);
    }
    
    default <T> T fold(Function<L, T> leftMapper, Function<R, T> rightMapper) {
        return switch (this) {
            case Left(var l) -> leftMapper.apply(l);
            case Right(var r) -> rightMapper.apply(r);
        };
    }
    
    default <T> Either<L, T> map(Function<R, T> mapper) {
        return switch (this) {
            case Left(var l) -> left(l);
            case Right(var r) -> right(mapper.apply(r));
        };
    }
    
    default <T> Either<L, T> flatMap(Function<R, Either<L, T>> mapper) {
        return switch (this) {
            case Left(var l) -> left(l);
            case Right(var r) -> mapper.apply(r);
        };
    }
    
    default boolean isLeft() {
        return this instanceof Left<L, R>;
    }
    
    default boolean isRight() {
        return this instanceof Right<L, R>;
    }
}
```

### 5. Stream API Mastery

#### Advanced Stream Operations
```java
public class StreamOperations {
    
    // Group by multiple criteria
    public Map<String, Map<String, List<MarketData>>> groupByExchangeAndSector(
            Stream<MarketData> stream) {
        return stream.collect(
            groupingBy(MarketData::exchange,
                groupingBy(data -> getSector(data.symbol())))
        );
    }
    
    // Custom collectors
    public static <T> Collector<T, ?, Optional<T>> toOptional() {
        return Collector.of(
            () -> new Optional[]{Optional.empty()},
            (acc, item) -> acc[0] = Optional.of(item),
            (acc1, acc2) -> acc2,
            acc -> acc[0]
        );
    }
    
    // Parallel processing with custom thread pool
    public List<MarketData> processInParallel(List<String> symbols) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            return symbols.parallelStream()
                .map(symbol -> CompletableFuture.supplyAsync(() -> 
                    fetchMarketData(symbol), executor))
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();
        }
    }
}
```

### 6. Pattern Matching (Java 24)

#### Advanced Pattern Matching
```java
public class PatternMatchingExamples {
    
    public String processMarketEvent(MarketEvent event) {
        return switch (event) {
            case PriceUpdate(var symbol, var price, var timestamp) 
                when price > 1000.0 -> "High value update for " + symbol;
            case PriceUpdate(var symbol, var price, var timestamp) 
                when price < 1.0 -> "Penny stock update for " + symbol;
            case PriceUpdate(var symbol, var price, var timestamp) -> 
                "Regular price update for " + symbol + ": $" + price;
            case VolumeUpdate(var symbol, var volume, var timestamp) 
                when volume > 1_000_000 -> "High volume alert for " + symbol;
            case TradingHalt(var symbol, var reason, var timestamp) -> 
                "Trading halted for " + symbol + " due to: " + reason;
            case MarketClose(var exchange, var timestamp) -> 
                "Market " + exchange + " closed at " + timestamp;
        };
    }
    
    public double calculateFee(TradeRequest trade) {
        return switch (trade) {
            case MarketOrder(var symbol, var quantity, var side) -> 
                quantity * 0.001; // 0.1% fee for market orders
            case LimitOrder(var symbol, var quantity, var price, var side) 
                when quantity > 1000 -> quantity * 0.0005; // Reduced fee for large orders
            case StopOrder(var symbol, var quantity, var stopPrice, var side) -> 
                quantity * 0.0015; // Higher fee for stop orders
            default -> 0.0;
        };
    }
}
```

### 7. Functional Error Handling

#### Railway Oriented Programming
```java
public final class Railway {
    
    public static <T, E> Function<T, Result<T, E>> lift(Predicate<T> predicate, E error) {
        return input -> predicate.test(input) ? 
            Result.success(input) : 
            Result.failure(error);
    }
    
    public static <T, U, E> Function<T, Result<U, E>> liftFunction(
            Function<T, U> function, Function<Exception, E> errorMapper) {
        return input -> {
            try {
                return Result.success(function.apply(input));
            } catch (Exception e) {
                return Result.failure(errorMapper.apply(e));
            }
        };
    }
    
    // Bind operation for chaining
    public static <T, U, E> Function<Result<T, E>, Result<U, E>> bind(
            Function<T, Result<U, E>> function) {
        return result -> result.flatMap(function);
    }
    
    // Map operation
    public static <T, U, E> Function<Result<T, E>, Result<U, E>> map(
            Function<T, U> function) {
        return result -> result.map(function);
    }
}

// Usage example
public Result<MarketData, String> processMarketData(String symbol) {
    return Result.success(symbol)
        .flatMap(Railway.bind(this::validateSymbol))
        .flatMap(Railway.bind(this::fetchData))
        .flatMap(Railway.bind(this::enrichData))
        .map(this::cache);
}
```

### 8. Lazy Evaluation Patterns

#### Lazy Sequences
```java
public class LazySequence<T> {
    private final Supplier<Stream<T>> streamSupplier;
    
    private LazySequence(Supplier<Stream<T>> streamSupplier) {
        this.streamSupplier = streamSupplier;
    }
    
    public static <T> LazySequence<T> of(Supplier<Stream<T>> supplier) {
        return new LazySequence<>(supplier);
    }
    
    public <U> LazySequence<U> map(Function<T, U> mapper) {
        return new LazySequence<>(() -> streamSupplier.get().map(mapper));
    }
    
    public LazySequence<T> filter(Predicate<T> predicate) {
        return new LazySequence<>(() -> streamSupplier.get().filter(predicate));
    }
    
    public <U> LazySequence<U> flatMap(Function<T, Stream<U>> mapper) {
        return new LazySequence<>(() -> streamSupplier.get().flatMap(mapper));
    }
    
    public List<T> take(int n) {
        return streamSupplier.get().limit(n).toList();
    }
    
    public Optional<T> findFirst() {
        return streamSupplier.get().findFirst();
    }
    
    public Stream<T> stream() {
        return streamSupplier.get();
    }
}

// Usage
LazySequence<MarketData> priceStream = LazySequence.of(() -> 
    generatePriceUpdates())
    .filter(data -> data.price() > 100.0)
    .map(this::enrichWithAnalytics);
```

### 9. Function Memoization

#### Advanced Memoization
```java
public class Memoization {
    
    public static <T, R> Function<T, R> memoize(Function<T, R> function) {
        return new Function<>() {
            private final ConcurrentHashMap<T, R> cache = new ConcurrentHashMap<>();
            
            @Override
            public R apply(T input) {
                return cache.computeIfAbsent(input, function);
            }
        };
    }
    
    public static <T, U, R> BiFunction<T, U, R> memoize(BiFunction<T, U, R> function) {
        return new BiFunction<>() {
            private final Map<Pair<T, U>, R> cache = new ConcurrentHashMap<>();
            
            @Override
            public R apply(T t, U u) {
                return cache.computeIfAbsent(Pair.of(t, u), 
                    pair -> function.apply(pair.first(), pair.second()));
            }
        };
    }
    
    // Time-based expiration
    public static <T, R> Function<T, R> memoizeWithExpiration(
            Function<T, R> function, Duration expiration) {
        return new Function<>() {
            private final Map<T, TimestampedValue<R>> cache = new ConcurrentHashMap<>();
            
            @Override
            public R apply(T input) {
                TimestampedValue<R> cached = cache.get(input);
                if (cached != null && cached.isValid(expiration)) {
                    return cached.value();
                }
                
                R result = function.apply(input);
                cache.put(input, new TimestampedValue<>(result, Instant.now()));
                return result;
            }
        };
    }
    
    private record TimestampedValue<T>(T value, Instant timestamp) {
        boolean isValid(Duration expiration) {
            return timestamp.plus(expiration).isAfter(Instant.now());
        }
    }
    
    private record Pair<T, U>(T first, U second) {
        static <T, U> Pair<T, U> of(T first, U second) {
            return new Pair<>(first, second);
        }
    }
}
```

### 10. Functional Validation

#### Validation Combinators
```java
public class Validation<T> {
    private final T value;
    private final List<String> errors;
    
    private Validation(T value, List<String> errors) {
        this.value = value;
        this.errors = List.copyOf(errors);
    }
    
    public static <T> Validation<T> valid(T value) {
        return new Validation<>(value, List.of());
    }
    
    public static <T> Validation<T> invalid(String error) {
        return new Validation<>(null, List.of(error));
    }
    
    public static <T> Validation<T> invalid(List<String> errors) {
        return new Validation<>(null, errors);
    }
    
    public boolean isValid() {
        return errors.isEmpty();
    }
    
    public T getValue() {
        if (!isValid()) {
            throw new IllegalStateException("Cannot get value from invalid validation");
        }
        return value;
    }
    
    public List<String> getErrors() {
        return errors;
    }
    
    public <U> Validation<U> map(Function<T, U> mapper) {
        return isValid() ? 
            valid(mapper.apply(value)) : 
            invalid(errors);
    }
    
    public <U> Validation<U> flatMap(Function<T, Validation<U>> mapper) {
        if (!isValid()) {
            return invalid(errors);
        }
        
        Validation<U> result = mapper.apply(value);
        return result.isValid() ? 
            result : 
            invalid(Stream.concat(errors.stream(), result.errors.stream()).toList());
    }
    
    public static <T> Validation<T> combine(List<Validation<T>> validations) {
        List<String> allErrors = validations.stream()
            .flatMap(v -> v.errors.stream())
            .toList();
            
        if (allErrors.isEmpty()) {
            return valid(validations.get(0).value);
        } else {
            return invalid(allErrors);
        }
    }
}

// Validation rules as functions
public class MarketDataValidators {
    
    public static final Function<String, Validation<String>> validateSymbol = symbol ->
        Optional.ofNullable(symbol)
            .filter(s -> !s.isBlank())
            .filter(s -> s.length() <= 10)
            .filter(s -> s.matches("[A-Z]+"))
            .map(Validation::valid)
            .orElse(Validation.invalid("Invalid symbol"));
    
    public static final Function<Double, Validation<Double>> validatePrice = price ->
        Optional.of(price)
            .filter(p -> p >= 0)
            .filter(p -> p < 1_000_000)
            .map(Validation::valid)
            .orElse(Validation.invalid("Invalid price"));
    
    public static final Function<Long, Validation<Long>> validateVolume = volume ->
        Optional.of(volume)
            .filter(v -> v >= 0)
            .map(Validation::valid)
            .orElse(Validation.invalid("Invalid volume"));
    
    // Combine all validations
    public static Validation<MarketData> validateMarketData(
            String symbol, String exchange, double price, long volume, LocalDateTime timestamp) {
        
        return Validation.combine(List.of(
            validateSymbol.apply(symbol).map(s -> MarketData.builder().symbol(s)),
            validatePrice.apply(price).map(p -> MarketData.builder().price(p)),
            validateVolume.apply(volume).map(v -> MarketData.builder().volume(v))
        )).map(builder -> builder.exchange(exchange).timestamp(timestamp).build());
    }
}
```

## Implementation Guidelines

### 1. Method Signature Patterns
```java
// Pure functions - no side effects
public static double calculateReturn(double initialPrice, double currentPrice) {
    return (currentPrice - initialPrice) / initialPrice;
}

// Functions with controlled side effects
public Function<MarketData, CompletableFuture<MarketData>> withLogging(String operation) {
    return data -> CompletableFuture.supplyAsync(() -> {
        log.info("Performing {} on {}", operation, data.symbol());
        return data;
    });
}

// Higher-order functions
public <T, R> Function<List<T>, List<R>> parallelMap(Function<T, R> mapper) {
    return list -> list.parallelStream().map(mapper).toList();
}
```

### 2. Error Handling Patterns
```java
// Never use try-catch in business logic, wrap in functional constructs
public static <T> Optional<T> tryExecute(Supplier<T> operation) {
    try {
        return Optional.ofNullable(operation.get());
    } catch (Exception e) {
        return Optional.empty();
    }
}

public static <T> CompletableFuture<Optional<T>> tryExecuteAsync(Supplier<T> operation) {
    return CompletableFuture.supplyAsync(() -> tryExecute(operation));
}
```

### 3. Resource Management
```java
public static <T, R> Function<T, R> withResource(
        Function<T, AutoCloseable> resourceFactory,
        BiFunction<T, AutoCloseable, R> operation) {
    return input -> {
        try (var resource = resourceFactory.apply(input)) {
            return operation.apply(input, resource);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    };
}
```

## Anti-Patterns to Avoid

### 1. Never Use These Constructs
- `if-else` statements (use pattern matching, Optional, or function composition)
- `for/while` loops (use Stream API or recursive functions)
- Mutable fields in data classes (use Records)
- `try-catch` in business logic (use functional error handling)
- `null` returns (use Optional or Maybe)
- Side effects in pure functions
- Deep inheritance hierarchies (use composition and sealed classes)

### 2. Replace Common Patterns
```java
// BAD: Imperative style
public List<String> getHighPriceSymbols(List<MarketData> data, double threshold) {
    List<String> result = new ArrayList<>();
    for (MarketData item : data) {
        if (item.price() > threshold) {
            result.add(item.symbol());
        }
    }
    return result;
}

// GOOD: Functional style
public List<String> getHighPriceSymbols(List<MarketData> data, double threshold) {
    return data.stream()
        .filter(item -> item.price() > threshold)
        .map(MarketData::symbol)
        .toList();
}
```

## Performance Considerations

### 1. Lazy Evaluation
Use lazy evaluation for expensive computations and infinite sequences.

### 2. Parallel Processing
Leverage parallel streams and virtual threads for independent computations.

### 3. Memoization
Cache expensive function results using memoization patterns.

### 4. Stream Optimization
Use specialized streams (IntStream, LongStream) for primitive types to avoid boxing.