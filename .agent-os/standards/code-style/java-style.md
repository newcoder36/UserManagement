# Java Spring Boot - Best in Class Coding Style Guide

## Table of Contents
- [Project Structure & Organization](#project-structure--organization)
- [Naming Conventions](#naming-conventions)
- [Spring Boot Specific Best Practices](#spring-boot-specific-best-practices)
- [Code Quality Standards](#code-quality-standards)
- [Configuration Management](#configuration-management)
- [Testing Best Practices](#testing-best-practices)
- [Performance & Security](#performance--security)
- [Documentation Standards](#documentation-standards)

## Project Structure & Organization

### Standard Directory Layout
```
src/main/java/com/company/project/
├── Application.java              # Main Spring Boot application class
├── config/                       # Configuration classes
│   ├── SecurityConfig.java
│   ├── DatabaseConfig.java
│   └── SwaggerConfig.java
├── controller/                   # REST controllers
│   ├── UserController.java
│   └── OrderController.java
├── service/                      # Business logic layer
│   ├── UserService.java
│   ├── impl/
│   │   └── UserServiceImpl.java
│   └── OrderService.java
├── repository/                   # Data access layer
│   ├── UserRepository.java
│   └── OrderRepository.java
├── model/
│   ├── entity/                   # JPA entities
│   │   ├── User.java
│   │   └── Order.java
│   └── dto/                      # Data transfer objects
│       ├── UserDto.java
│       ├── CreateUserRequest.java
│       └── UpdateUserRequest.java
├── exception/                    # Custom exceptions
│   ├── UserNotFoundException.java
│   └── GlobalExceptionHandler.java
├── mapper/                       # Object mapping utilities
│   └── UserMapper.java
└── util/                        # Utility classes
    └── DateUtils.java

src/main/resources/
├── application.yml              # Main configuration
├── application-dev.yml          # Development profile
├── application-prod.yml         # Production profile
└── db/migration/               # Database migration scripts
    └── V1__Create_user_table.sql
```

## Naming Conventions

### Classes & Interfaces
- **Controllers**: `UserController`, `OrderController`
- **Services**: `UserService`, `OrderService`
- **Service Implementations**: `UserServiceImpl`
- **Repositories**: `UserRepository`, `OrderRepository`
- **DTOs**: `UserDto`, `CreateOrderRequest`, `OrderResponse`
- **Entities**: `User`, `Order`, `Product`
- **Exceptions**: `UserNotFoundException`, `InvalidOrderException`
- **Configuration**: `DatabaseConfig`, `SecurityConfig`

### Methods & Variables
```java
// Use camelCase for methods and variables
private UserService userService;
private List<User> activeUsers;

public List<User> findActiveUsers() { }
public void updateUserStatus(Long userId, Status status) { }

// Boolean methods - use is/has/can/should prefix
public boolean isActive() { }
public boolean hasPermission() { }
public boolean canAccess() { }
public boolean shouldProcess() { }

// Constants - use UPPER_SNAKE_CASE
public static final String DEFAULT_PAGE_SIZE = "20";
public static final int MAX_RETRY_ATTEMPTS = 3;
```

### Package Names
```java
// Use lowercase with dots as separators
com.company.project.controller
com.company.project.service.impl
com.company.project.model.dto.request
```

## Spring Boot Specific Best Practices

### 1. Dependency Injection - Constructor Injection (Preferred)
```java
@Service
public class UserService {
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    
    // Constructor injection - preferred approach
    public UserService(UserRepository userRepository, 
                      EmailService emailService,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }
}
```

### 2. Controller Layer Design
```java
@RestController
@RequestMapping("/api/v1/users")
@Validated
@Slf4j
public class UserController {
    
    private final UserService userService;
    
    public UserController(UserService userService) {
        this.userService = userService;
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUser(@PathVariable @Positive Long id) {
        log.info("Fetching user with id: {}", id);
        UserDto user = userService.findById(id);
        return ResponseEntity.ok(user);
    }
    
    @PostMapping
    public ResponseEntity<UserDto> createUser(@RequestBody @Valid CreateUserRequest request) {
        log.info("Creating new user with email: {}", request.getEmail());
        UserDto user = userService.create(request);
        URI location = ServletUriComponentsBuilder
            .fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(user.getId())
            .toUri();
        return ResponseEntity.created(location).body(user);
    }
    
    @GetMapping
    public ResponseEntity<Page<UserDto>> getUsers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "id") String sortBy) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy));
        Page<UserDto> users = userService.findAll(pageable);
        return ResponseEntity.ok(users);
    }
}
```

### 3. Service Layer Design
```java
@Service
@Transactional(readOnly = true)
@Slf4j
public class UserService {
    
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final UserMapper userMapper;
    
    public UserService(UserRepository userRepository, 
                      EmailService emailService,
                      UserMapper userMapper) {
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.userMapper = userMapper;
    }
    
    @Transactional
    public UserDto create(CreateUserRequest request) {
        log.debug("Creating user with email: {}", request.getEmail());
        
        // Validate business rules
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("User with email already exists: " + request.getEmail());
        }
        
        User user = userMapper.toEntity(request);
        User savedUser = userRepository.save(user);
        
        // Send welcome email asynchronously
        emailService.sendWelcomeEmailAsync(savedUser.getEmail());
        
        log.info("Successfully created user with id: {}", savedUser.getId());
        return userMapper.toDto(savedUser);
    }
    
    public UserDto findById(Long id) {
        log.debug("Fetching user with id: {}", id);
        User user = userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
        return userMapper.toDto(user);
    }
    
    public Page<UserDto> findAll(Pageable pageable) {
        log.debug("Fetching users with pagination: {}", pageable);
        Page<User> users = userRepository.findAll(pageable);
        return users.map(userMapper::toDto);
    }
}
```

### 4. Repository Layer
```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByEmail(String email);
    
    boolean existsByEmail(String email);
    
    @Query("SELECT u FROM User u WHERE u.status = :status AND u.createdAt >= :since")
    List<User> findActiveUsersSince(@Param("status") UserStatus status, 
                                   @Param("since") LocalDateTime since);
    
    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :loginTime WHERE u.id = :userId")
    void updateLastLogin(@Param("userId") Long userId, 
                        @Param("loginTime") LocalDateTime loginTime);
}
```

## Code Quality Standards

### 1. Entity Design
```java
@Entity
@Table(name = "users", 
       indexes = @Index(name = "idx_user_email", columnList = "email"))
@Slf4j
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 100)
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;
    
    @Column(nullable = false, unique = true, length = 255)
    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String email;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.ACTIVE;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    // Constructors
    protected User() {} // JPA requirement
    
    public User(String name, String email) {
        this.name = name;
        this.email = email;
    }
    
    // Getters and setters
    // equals() and hashCode() based on business key (email)
    // toString() excluding sensitive data
}
```

### 2. DTO Design
```java
public class UserDto {
    private Long id;
    
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;
    
    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String email;
    
    private UserStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Constructors, getters, setters
}

public class CreateUserRequest {
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;
    
    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String email;
    
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$", 
             message = "Password must contain uppercase, lowercase and digit")
    private String password;
    
    // Constructors, getters, setters
}
```

### 3. Exception Handling
```java
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        log.warn("User not found: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
            .code("USER_NOT_FOUND")
            .message(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors()
            .stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.toList());
            
        ErrorResponse error = ErrorResponse.builder()
            .code("VALIDATION_ERROR")
            .message("Validation failed")
            .details(errors)
            .timestamp(LocalDateTime.now())
            .build();
            
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Unexpected error occurred", ex);
        ErrorResponse error = ErrorResponse.builder()
            .code("INTERNAL_ERROR")
            .message("An unexpected error occurred")
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
```

### 4. Mapper Pattern
```java
@Component
public class UserMapper {
    
    public UserDto toDto(User user) {
        if (user == null) return null;
        
        return UserDto.builder()
            .id(user.getId())
            .name(user.getName())
            .email(user.getEmail())
            .status(user.getStatus())
            .createdAt(user.getCreatedAt())
            .updatedAt(user.getUpdatedAt())
            .build();
    }
    
    public User toEntity(CreateUserRequest request) {
        if (request == null) return null;
        
        return User.builder()
            .name(request.getName())
            .email(request.getEmail())
            .build();
    }
    
    public List<UserDto> toDtoList(List<User> users) {
        return users.stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }
}
```

## Configuration Management

### 1. Application Configuration
```yaml
# application.yml
spring:
  application:
    name: user-service
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true

  jackson:
    serialization:
      write-dates-as-timestamps: false
    deserialization:
      fail-on-unknown-properties: false

logging:
  level:
    com.company.project: INFO
    org.hibernate.SQL: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always
```

### 2. Configuration Classes
```java
@Configuration
@EnableJpaAuditing
public class DatabaseConfig {
    
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSource dataSource() {
        return DataSourceBuilder.create().build();
    }
    
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> Optional.of("system"); // or get from security context
    }
}

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()))
            .build();
    }
}
```

## Testing Best Practices

### 1. Unit Tests
```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private EmailService emailService;
    
    @Mock
    private UserMapper userMapper;
    
    @InjectMocks
    private UserService userService;
    
    @Test
    @DisplayName("Should create user successfully when valid request provided")
    void shouldCreateUser_WhenValidRequest() {
        // Given
        CreateUserRequest request = new CreateUserRequest("John Doe", "john@example.com");
        User user = new User("John Doe", "john@example.com");
        UserDto expectedDto = new UserDto(1L, "John Doe", "john@example.com");
        
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(userMapper.toEntity(request)).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toDto(user)).thenReturn(expectedDto);
        
        // When
        UserDto result = userService.create(request);
        
        // Then
        assertThat(result).isEqualTo(expectedDto);
        verify(emailService).sendWelcomeEmailAsync(request.getEmail());
    }
    
    @Test
    @DisplayName("Should throw exception when user already exists")
    void shouldThrowException_WhenUserAlreadyExists() {
        // Given
        CreateUserRequest request = new CreateUserRequest("John Doe", "john@example.com");
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);
        
        // When & Then
        assertThatThrownBy(() -> userService.create(request))
            .isInstanceOf(UserAlreadyExistsException.class)
            .hasMessage("User with email already exists: john@example.com");
    }
}
```

### 2. Integration Tests
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserControllerIntegrationTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private UserRepository userRepository;
    
    @Test
    @DisplayName("Should create user and return 201 status")
    void shouldCreateUser() {
        // Given
        CreateUserRequest request = new CreateUserRequest("John Doe", "john@example.com");
        
        // When
        ResponseEntity<UserDto> response = restTemplate.postForEntity(
            "/api/v1/users", request, UserDto.class);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getName()).isEqualTo("John Doe");
        assertThat(userRepository.existsByEmail("john@example.com")).isTrue();
    }
}
```

## Performance & Security

### 1. Caching
```java
@Service
@Transactional(readOnly = true)
public class UserService {
    
    @Cacheable(value = "users", key = "#id")
    public UserDto findById(Long id) {
        // Implementation
    }
    
    @CacheEvict(value = "users", key = "#result.id")
    @Transactional
    public UserDto update(Long id, UpdateUserRequest request) {
        // Implementation
    }
}
```

### 2. Async Processing
```java
@Service
public class EmailService {
    
    @Async
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public CompletableFuture<Void> sendWelcomeEmailAsync(String email) {
        // Send email implementation
        return CompletableFuture.completedFuture(null);
    }
}
```

### 3. Security Annotations
```java
@RestController
@PreAuthorize("hasRole('USER')")
public class UserController {
    
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @userService.isOwner(#id, authentication.name)")
    public ResponseEntity<UserDto> getUser(@PathVariable Long id) {
        // Implementation
    }
}
```

## Documentation Standards

### 1. JavaDoc
```java
/**
 * Service for managing user operations.
 * 
 * @author John Doe
 * @since 1.0.0
 */
@Service
public class UserService {
    
    /**
     * Creates a new user with the provided information.
     * 
     * @param request the user creation request containing name and email
     * @return the created user data
     * @throws UserAlreadyExistsException if a user with the same email already exists
     * @throws ValidationException if the request data is invalid
     */
    @Transactional
    public UserDto create(CreateUserRequest request) {
        // Implementation
    }
}
```

### 2. OpenAPI Documentation
```java
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Management", description = "APIs for managing users")
public class UserController {
    
    @Operation(
        summary = "Create a new user",
        description = "Creates a new user with the provided information",
        responses = {
            @ApiResponse(responseCode = "201", description = "User created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "409", description = "User already exists")
        }
    )
    @PostMapping
    public ResponseEntity<UserDto> createUser(
            @Parameter(description = "User creation request") 
            @RequestBody @Valid CreateUserRequest request) {
        // Implementation
    }
}
```

## Code Formatting & Style Rules

### 1. Line Length & Indentation
- Maximum line length: 120 characters
- Use 4 spaces for indentation (no tabs)
- Continuation lines should be indented 8 spaces

### 2. Method Organization
```java
public class UserService {
    // 1. Static fields
    private static final String DEFAULT_ROLE = "USER";
    
    // 2. Instance fields
    private final UserRepository userRepository;
    
    // 3. Constructor
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    // 4. Public methods
    public UserDto create(CreateUserRequest request) { }
    
    // 5. Private methods
    private void validateUser(User user) { }
}
```

### 3. Import Organization
```java
// 1. Java standard library imports
import java.time.LocalDateTime;
import java.util.List;

// 2. Third-party library imports
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

// 3. Application imports
import com.company.project.model.User;
import com.company.project.repository.UserRepository;
```

## Additional Best Practices

1. **Use Builder Pattern** for complex objects with many optional fields
2. **Implement Proper Logging** with structured logging and appropriate log levels
3. **Handle Transactions Properly** with appropriate isolation levels and rollback rules
4. **Use Profiles** for environment-specific configurations
5. **Implement Health Checks** for monitoring application status
6. **Follow RESTful Conventions** for API design
7. **Use DTOs** to separate internal models from API contracts
8. **Implement Proper Error Handling** with meaningful error messages
9. **Add Metrics and Monitoring** using Micrometer and actuator endpoints
10. **Use Database Migrations** (Flyway/Liquibase) for schema versioning