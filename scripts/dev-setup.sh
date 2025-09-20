#!/bin/bash

# NSE Stock Analysis Bot - Development Environment Setup Script
set -e

# Configuration
PROJECT_NAME="nse-stock-analysis-bot"
COMPOSE_FILE="docker-compose.dev.yml"
ENV_FILE=".env"
DEV_ENV_FILE=".env.development"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
    exit 1
}

warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    log "Checking prerequisites..."
    
    # Check if Docker is installed and running
    if ! command -v docker &> /dev/null; then
        error "Docker is not installed. Please install Docker first."
    fi
    
    if ! docker info &> /dev/null; then
        error "Docker daemon is not running. Please start Docker."
    fi
    
    # Check if Docker Compose is installed
    if ! command -v docker-compose &> /dev/null; then
        error "Docker Compose is not installed. Please install Docker Compose."
    fi
    
    # Check if Java 21 is installed (for local development)
    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
        if [ "$JAVA_VERSION" -lt 21 ]; then
            warning "Java 21 is recommended for local development. Current version: $JAVA_VERSION"
        fi
    else
        log "Java not found locally (will use Docker for compilation)"
    fi
    
    # Check if Maven is installed (for local development)
    if ! command -v mvn &> /dev/null; then
        log "Maven not found locally (will use Docker for builds)"
    fi
    
    success "Prerequisites check completed"
}

# Setup environment file
setup_environment() {
    log "Setting up environment file..."
    
    if [ ! -f "$ENV_FILE" ]; then
        if [ -f "$DEV_ENV_FILE" ]; then
            log "Copying development environment template..."
            cp "$DEV_ENV_FILE" "$ENV_FILE"
        else
            error "Neither $ENV_FILE nor $DEV_ENV_FILE found"
        fi
    fi
    
    # Check if bot token is configured
    if grep -q "your_dev_bot_token_here" "$ENV_FILE"; then
        warning "Please configure your Telegram bot token in $ENV_FILE"
        echo ""
        echo "To get a bot token:"
        echo "1. Message @BotFather on Telegram"
        echo "2. Create a new bot with /newbot"
        echo "3. Copy the token to TELEGRAM_BOT_TOKEN in $ENV_FILE"
        echo ""
        read -p "Press Enter to continue after configuring the bot token..."
    fi
    
    success "Environment file is ready"
}

# Build development environment
build_dev_environment() {
    log "Building development environment..."
    
    # Stop any existing containers
    docker-compose -f "$COMPOSE_FILE" down || true
    
    # Build application with development profile
    docker-compose -f "$COMPOSE_FILE" build --no-cache
    
    success "Development environment built"
}

# Start development services
start_services() {
    log "Starting development services..."
    
    # Start database and cache first
    docker-compose -f "$COMPOSE_FILE" up -d postgres redis
    
    # Wait for database to be ready
    log "Waiting for database to be ready..."
    for i in {1..30}; do
        if docker-compose -f "$COMPOSE_FILE" exec postgres pg_isready -U nse_bot; then
            break
        fi
        if [ $i -eq 30 ]; then
            error "Database failed to start within timeout"
        fi
        sleep 2
    done
    
    # Wait for Redis to be ready
    log "Waiting for Redis to be ready..."
    for i in {1..20}; do
        if docker-compose -f "$COMPOSE_FILE" exec redis redis-cli ping; then
            break
        fi
        if [ $i -eq 20 ]; then
            error "Redis failed to start within timeout"
        fi
        sleep 1
    done
    
    # Start the application
    docker-compose -f "$COMPOSE_FILE" up -d nse-bot
    
    # Wait for application to be ready
    log "Waiting for application to be ready..."
    for i in {1..60}; do
        if curl -f http://localhost:8080/actuator/health &> /dev/null; then
            break
        fi
        if [ $i -eq 60 ]; then
            error "Application failed to start within timeout"
        fi
        sleep 5
    done
    
    success "Development services started successfully"
}

# Run health checks
run_health_checks() {
    log "Running health checks..."
    
    # Check application health
    if curl -s http://localhost:8080/actuator/health | grep -q "UP"; then
        success "Application health check passed"
    else
        error "Application health check failed"
    fi
    
    # Check database connectivity
    if docker-compose -f "$COMPOSE_FILE" exec postgres pg_isready -U nse_bot &> /dev/null; then
        success "Database health check passed"
    else
        error "Database health check failed"
    fi
    
    # Check Redis connectivity
    if docker-compose -f "$COMPOSE_FILE" exec redis redis-cli ping &> /dev/null; then
        success "Redis health check passed"
    else
        error "Redis health check failed"
    fi
}

# Show development status and URLs
show_status() {
    log "Development Environment Status:"
    echo "================================"
    docker-compose -f "$COMPOSE_FILE" ps
    echo ""
    
    log "Service URLs:"
    echo "Application: http://localhost:8080"
    echo "Health Check: http://localhost:8080/actuator/health"
    echo "Metrics: http://localhost:8080/actuator/prometheus"
    echo "Debug Port: localhost:5005 (for remote debugging)"
    echo ""
    echo "Database: localhost:5432 (nse_bot_dev/nse_bot/dev_password)"
    echo "Redis: localhost:6379 (password: dev_redis_password)"
    echo ""
    
    log "Development Commands:"
    echo "View logs: docker-compose -f $COMPOSE_FILE logs -f nse-bot"
    echo "Stop services: docker-compose -f $COMPOSE_FILE down"
    echo "Restart app: docker-compose -f $COMPOSE_FILE restart nse-bot"
    echo "Enter app container: docker-compose -f $COMPOSE_FILE exec nse-bot bash"
    echo "Database shell: docker-compose -f $COMPOSE_FILE exec postgres psql -U nse_bot -d nse_bot_dev"
    echo "Redis shell: docker-compose -f $COMPOSE_FILE exec redis redis-cli"
}

# Setup IDE debugging
setup_debugging() {
    log "IDE Debugging Setup:"
    echo "===================="
    echo "Remote Debug Configuration:"
    echo "Host: localhost"
    echo "Port: 5005"
    echo "Transport: Socket"
    echo "Debugger mode: Attach"
    echo ""
    echo "For IntelliJ IDEA:"
    echo "1. Run -> Edit Configurations"
    echo "2. Add -> Remote JVM Debug"
    echo "3. Set Host: localhost, Port: 5005"
    echo "4. Set module classpath to your project"
    echo ""
    echo "For VS Code:"
    echo "1. Install Extension Pack for Java"
    echo "2. Add debug configuration in launch.json:"
    echo '   {'
    echo '     "type": "java",'
    echo '     "name": "Attach to NSE Bot",'
    echo '     "request": "attach",'
    echo '     "hostName": "localhost",'
    echo '     "port": 5005'
    echo '   }'
}

# Main setup function
main() {
    log "Setting up NSE Stock Analysis Bot development environment..."
    
    check_prerequisites
    setup_environment
    build_dev_environment
    start_services
    run_health_checks
    show_status
    setup_debugging
    
    success "Development environment setup completed!"
    echo ""
    echo "Your bot is now running and ready for development."
    echo "Check the logs with: docker-compose -f $COMPOSE_FILE logs -f nse-bot"
}

# Handle command line arguments
case "${1:-setup}" in
    "setup")
        main
        ;;
    "start")
        start_services
        run_health_checks
        show_status
        ;;
    "stop")
        docker-compose -f "$COMPOSE_FILE" down
        success "Development environment stopped"
        ;;
    "restart")
        docker-compose -f "$COMPOSE_FILE" restart "${2:-nse-bot}"
        success "Service restarted"
        ;;
    "logs")
        docker-compose -f "$COMPOSE_FILE" logs -f "${2:-nse-bot}"
        ;;
    "health")
        run_health_checks
        ;;
    "status")
        show_status
        ;;
    "build")
        build_dev_environment
        ;;
    "clean")
        docker-compose -f "$COMPOSE_FILE" down -v
        docker system prune -f
        success "Development environment cleaned"
        ;;
    *)
        echo "Usage: $0 {setup|start|stop|restart|logs|health|status|build|clean}"
        echo ""
        echo "Commands:"
        echo "  setup   - Full development environment setup (default)"
        echo "  start   - Start services and show status"
        echo "  stop    - Stop all services"
        echo "  restart - Restart services (optionally specify service)"
        echo "  logs    - Show logs (optionally specify service)"
        echo "  health  - Run health checks"
        echo "  status  - Show service status and URLs"
        echo "  build   - Rebuild application"
        echo "  clean   - Stop and remove all containers and volumes"
        exit 1
        ;;
esac