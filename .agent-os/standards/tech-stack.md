# Java Tech Stack for Agent OS

## Context
Global tech stack for Agent OS projects combining modern Java backend (Java 24 + Spring Boot 3.5.x) with existing infrastructure, tooling, and operational stack. Overridable in project-specific `.agent-os/product/tech-stack.md`.

## Core Backend Stack
- **App Framework**: Spring Boot 3.5.x
- **Language**: Java 24 (with Virtual Threads, Pattern Matching, Records)
- **Primary Database**: PostgreSQL 17+
- **ORM**: Spring Data JPA with Hibernate 6.4+
- **Reactive Support**: Spring WebFlux with Project Reactor
- **Virtual Threads**: Enabled via `spring.threads.virtual.enabled=true`

## Advanced Java Features & Patterns

### Java 24 Features
- **Virtual Threads**: For high-concurrency applications
- **Pattern Matching**: Enhanced switch expressions and instanceof
- **Records**: Immutable data carriers
- **Sealed Classes**: Controlled inheritance hierarchies
- **Text Blocks**: Multi-line string literals

### Functional Programming
- **Functional Interfaces**: Custom and built-in (Function, Predicate, Consumer, Supplier)
- **Lambda Expressions**: Method references and closure capture
- **Stream API**: Parallel processing with virtual threads
- **Optional**: Null-safe programming
- **CompletableFuture**: Asynchronous programming

### Design Patterns Implementation

#### Creational Patterns
- **Factory Pattern**: Bean factory methods with `@Configuration`
- **Builder Pattern**: Record builders and fluent APIs
- **Singleton Pattern**: Spring managed beans
- **Prototype Pattern**: `@Scope("prototype")` beans

#### Structural Patterns
- **Adapter Pattern**: Spring Boot auto-configuration adapters
- **Decorator Pattern**: AOP with `@Aspect` annotations
- **Facade Pattern**: Service layer abstractions
- **Proxy Pattern**: Spring AOP and JPA proxies

#### Behavioral Patterns
- **Strategy Pattern**: Functional interfaces for algorithms
- **Observer Pattern**: Spring Events and ApplicationEventPublisher
- **Command Pattern**: Lambda-based command objects
- **Template Method**: Abstract service classes

## SOLID Principles Implementation

### Single Responsibility Principle (SRP)
```java
@Service
public class UserRegistrationService {
    // Only handles user registration logic
}

@Component
public class EmailNotificationService {
    // Only handles email notifications
}
```

### Open/Closed Principle (OCP)
```java
@FunctionalInterface
public interface PaymentProcessor {
    PaymentResult process(PaymentRequest request);
}

@Component
public class PaymentService {
    private final Map<PaymentType, PaymentProcessor> processors;
    // Open for extension, closed for modification
}
```

### Liskov Substitution Principle (LSP)
```java
public abstract sealed class Animal permits Dog, Cat {
    public abstract String makeSound();
}
```

### Interface Segregation Principle (ISP)
```java
public interface Readable {
    String read();
}

public interface Writable {
    void write(String content);
}
```

### Dependency Inversion Principle (DIP)
```java
@Service
public class OrderService {
    private final PaymentGateway paymentGateway; // Depends on abstraction
    
    public OrderService(PaymentGateway paymentGateway) {
        this.paymentGateway = paymentGateway;
    }
}
```

## DRY, KISS, LoD Implementation

### DRY (Don't Repeat Yourself)
- **Utility Classes**: Common functionality extraction
- **Base Classes**: Abstract service/controller classes
- **Configuration**: Centralized application properties
- **Aspects**: Cross-cutting concerns with AOP

### KISS (Keep It Simple, Stupid)
- **Records**: Simple data structures
- **Functional Interfaces**: Clear, focused contracts
- **Method References**: Concise lambda expressions
- **Spring Boot Starters**: Convention over configuration

### LoD (Law of Demeter)
- **Service Layer**: Limited object interaction chains
- **DTOs**: Data transfer without deep object graphs
- **Facade Pattern**: Simplified interfaces

## Frontend & JavaScript Stack (Maintained from Original)
- **JavaScript Framework**: React latest stable
- **Build Tool**: Vite
- **Import Strategy**: Node.js modules
- **Package Manager**: npm
- **Node Version**: 22 LTS
- **CSS Framework**: TailwindCSS 4.0+
- **UI Components**: Instrumental Components latest
- **UI Installation**: Via development dependencies (npm equivalent of gems)
- **Font Provider**: Google Fonts
- **Font Loading**: Self-hosted for performance
- **Icons**: Lucide React components

## Development Tools & Build
- **Build Tool**: Maven 3.9+ or Gradle 8.5+ (integrates with existing CI/CD)
- **Java Build**: Compatible with Digital Ocean App Platform deployment
- **Code Quality**: SpotBugs, PMD, Checkstyle (integrated in GitHub Actions)
- **Testing**: JUnit 5, Mockito, TestContainers
- **API Documentation**: OpenAPI 3 with Swagger UI
- **Development Environment**: Compatible with existing project structure

## Database & Persistence (Enhanced from Original)
- **Primary Database**: PostgreSQL 17+ (maintained)
- **Database Hosting**: Digital Ocean Managed PostgreSQL (maintained)
- **Database Backups**: Daily automated (maintained)
- **Connection Pool**: HikariCP (default in Spring Boot)
- **Migrations**: Flyway or Liquibase
- **Query Builder**: Spring Data JPA Criteria API
- **ORM**: Spring Data JPA with Hibernate 6.4+ (replaces Active Record)

## Infrastructure & Hosting (Maintained from Original)
- **Application Hosting**: Digital Ocean App Platform/Droplets
- **Hosting Region**: Primary region based on user base
- **Database Hosting**: Digital Ocean Managed PostgreSQL
- **Database Backups**: Daily automated
- **Asset Storage**: Amazon S3
- **CDN**: CloudFront
- **Asset Access**: Private with signed URLs

## CI/CD & Deployment (Maintained from Original)
- **CI/CD Platform**: GitHub Actions
- **CI/CD Trigger**: Push to main/staging branches
- **Tests**: Run before deployment
- **Production Environment**: main branch
- **Staging Environment**: staging branch
- **Java Build Integration**: Maven/Gradle builds in existing GitHub Actions workflow

## Asset Management Integration (Maintained Strategy)

### S3 + CloudFront Configuration
```java
@Configuration
@ConfigurationProperties(prefix = "aws.s3")
public class S3Config {
    
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
            .region(Region.US_EAST_1)
            .build();
    }
    
    @Service
    public class AssetService {
        
        // Private access with signed URLs (maintained from original)
        public String generateSignedUrl(String objectKey) {
            return s3Presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(15))
                    .getObjectRequest(GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .build())
                    .build())
                .url().toString();
        }
    }
}
```

## Project Structure (Compatible with Agent OS)

```
agent-os/
├── .agent-os/
│   └── product/
│       └── tech-stack.md           # This configuration file
├── .github/
│   └── workflows/
│       └── deploy.yml              # Enhanced for Java + existing flow
├── backend/                        # Java Spring Boot application
│   ├── src/main/java/
│   ├── src/main/resources/
│   ├── pom.xml                     # or build.gradle
│   └── Dockerfile
├── frontend/                       # Existing React setup maintained
│   ├── src/
│   ├── package.json
│   ├── vite.config.js
│   └── tailwind.config.js
├── docs/                          # Existing documentation
└── scripts/                       # Existing deployment scripts
```

## Testing Strategy
- **Unit Tests**: JUnit 5 + Mockito
- **Integration Tests**: @SpringBootTest with TestContainers
- **Architecture Tests**: ArchUnit for design pattern validation
- **Performance Tests**: JMH (Java Microbenchmark Harness)

## GitHub Actions Workflow Integration

### Enhanced CI/CD Pipeline (Building on Existing)
```yaml
# .github/workflows/deploy.yml
name: Deploy Agent OS Java App

on:
  push:
    branches: [main, staging]  # Maintained branching strategy
  pull_request:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up Java 24
        uses: actions/setup-java@v4
        with:
          java-version: '24'
          distribution: 'oracle'
      
      - name: Set up Node.js 22 LTS
        uses: actions/setup-node@v4
        with:
          node-version: '22'
          
      - name: Run Java Tests
        run: ./mvnw test
        
      - name: Run Frontend Tests  
        run: |
          npm ci
          npm run test
          
  deploy-staging:
    if: github.ref == 'refs/heads/staging'
    needs: test
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to Digital Ocean Staging
        # Existing deployment process maintained
        
  deploy-production:
    if: github.ref == 'refs/heads/main'  
    needs: test
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to Digital Ocean Production
        # Existing deployment process maintained
```

## Digital Ocean Deployment Configuration

### App Platform Spec (app.yaml)
```yaml
name: agent-os-java
services:
- name: api
  source_dir: /
  github:
    repo: your-org/agent-os
    branch: main  # or staging for staging environment
  run_command: java -jar target/agent-os.jar
  environment_slug: java
  instance_count: 1
  instance_size_slug: basic-xxs
  envs:
  - key: DATABASE_URL
    scope: RUN_TIME
    type: SECRET
  - key: S3_BUCKET
    scope: RUN_TIME  
    type: SECRET
    
- name: frontend
  source_dir: /frontend
  github:
    repo: your-org/agent-os
    branch: main
  run_command: npm start
  environment_slug: node-js
  routes:
  - path: /
```

## Monitoring & Observability
- **Metrics**: Micrometer with Prometheus
- **Tracing**: Spring Cloud Sleuth with Zipkin
- **Logging**: Logback with structured JSON logging
- **Health Checks**: Spring Boot Actuator

## Security
- **Authentication**: Spring Security 6+ with JWT
- **Authorization**: Method-level security with SpEL
- **CORS**: Configured for React frontend
- **CSRF**: Stateless token-based protection