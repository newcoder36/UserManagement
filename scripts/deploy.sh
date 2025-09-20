#!/bin/bash

# NSE Stock Analysis Bot - Production Deployment Script
set -e

# Configuration
PROJECT_NAME="nse-stock-analysis-bot"
COMPOSE_FILE="docker-compose.prod.yml"
ENV_FILE=".env"
BACKUP_DIR="/backups/nse-bot"
LOG_FILE="/var/log/nse-bot-deploy.log"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging function
log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1" | tee -a "$LOG_FILE"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1" | tee -a "$LOG_FILE"
    exit 1
}

warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1" | tee -a "$LOG_FILE"
}

success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1" | tee -a "$LOG_FILE"
}

# Pre-deployment checks
check_prerequisites() {
    log "Checking prerequisites..."
    
    # Check if Docker is installed and running
    if ! command -v docker &> /dev/null; then
        error "Docker is not installed"
    fi
    
    if ! docker info &> /dev/null; then
        error "Docker daemon is not running"
    fi
    
    # Check if Docker Compose is installed
    if ! command -v docker-compose &> /dev/null; then
        error "Docker Compose is not installed"
    fi
    
    # Check if environment file exists
    if [ ! -f "$ENV_FILE" ]; then
        error "Environment file $ENV_FILE not found"
    fi
    
    # Check if required environment variables are set
    if ! grep -q "TELEGRAM_BOT_TOKEN=" "$ENV_FILE" || grep -q "your_bot_token_here" "$ENV_FILE"; then
        error "TELEGRAM_BOT_TOKEN is not properly configured in $ENV_FILE"
    fi
    
    success "Prerequisites check passed"
}

# Backup current data
backup_data() {
    log "Creating backup..."
    
    # Create backup directory if it doesn't exist
    mkdir -p "$BACKUP_DIR"
    
    BACKUP_FILE="$BACKUP_DIR/nse-bot-backup-$(date +'%Y%m%d-%H%M%S').tar.gz"
    
    # Stop containers to ensure consistent backup
    docker-compose -f "$COMPOSE_FILE" stop postgres redis || true
    
    # Create backup of volumes
    docker run --rm \
        -v nse-bot-postgres-data:/postgres-data:ro \
        -v nse-bot-redis-data:/redis-data:ro \
        -v "$BACKUP_DIR":/backup \
        alpine:latest \
        tar czf "/backup/$(basename $BACKUP_FILE)" /postgres-data /redis-data || warning "Backup creation failed"
    
    success "Backup created: $BACKUP_FILE"
}

# Build application
build_application() {
    log "Building application..."
    
    # Build Docker images
    docker-compose -f "$COMPOSE_FILE" build --no-cache
    
    success "Application built successfully"
}

# Deploy application
deploy_application() {
    log "Deploying application..."
    
    # Pull latest images for external services
    docker-compose -f "$COMPOSE_FILE" pull postgres redis prometheus grafana
    
    # Start services in the correct order
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
    
    # Start application
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
    
    # Start monitoring services
    docker-compose -f "$COMPOSE_FILE" up -d prometheus grafana
    
    # Start reverse proxy (if configured)
    docker-compose -f "$COMPOSE_FILE" up -d nginx || warning "Nginx not configured or failed to start"
    
    success "Application deployed successfully"
}

# Health checks
run_health_checks() {
    log "Running health checks..."
    
    # Check application health
    if ! curl -f http://localhost:8080/actuator/health; then
        error "Application health check failed"
    fi
    
    # Check database connectivity
    if ! docker-compose -f "$COMPOSE_FILE" exec postgres pg_isready -U nse_bot; then
        error "Database health check failed"
    fi
    
    # Check Redis connectivity
    if ! docker-compose -f "$COMPOSE_FILE" exec redis redis-cli ping; then
        error "Redis health check failed"
    fi
    
    success "All health checks passed"
}

# Setup webhook (if webhook URL is configured)
setup_webhook() {
    if grep -q "TELEGRAM_WEBHOOK_URL=" "$ENV_FILE" && ! grep -q "https://yourdomain.com" "$ENV_FILE"; then
        log "Setting up Telegram webhook..."
        
        # Extract bot token and webhook URL
        BOT_TOKEN=$(grep "TELEGRAM_BOT_TOKEN=" "$ENV_FILE" | cut -d '=' -f2)
        WEBHOOK_URL=$(grep "TELEGRAM_WEBHOOK_URL=" "$ENV_FILE" | cut -d '=' -f2)
        WEBHOOK_PATH=$(grep "TELEGRAM_WEBHOOK_PATH=" "$ENV_FILE" | cut -d '=' -f2 | sed 's/^//')
        
        # Set webhook
        curl -X POST "https://api.telegram.org/bot$BOT_TOKEN/setWebhook" \
            -H "Content-Type: application/json" \
            -d "{\"url\":\"$WEBHOOK_URL$WEBHOOK_PATH\"}" || warning "Webhook setup failed"
        
        success "Webhook configured"
    else
        log "Webhook not configured (using polling mode)"
    fi
}

# Cleanup old images and containers
cleanup() {
    log "Cleaning up unused resources..."
    
    # Remove unused images
    docker image prune -f || true
    
    # Remove unused volumes (be careful with this)
    # docker volume prune -f || true
    
    success "Cleanup completed"
}

# Show deployment status
show_status() {
    log "Deployment Status:"
    echo "===================="
    docker-compose -f "$COMPOSE_FILE" ps
    echo ""
    
    log "Service URLs:"
    echo "Application: http://localhost:8080"
    echo "Health Check: http://localhost:8080/actuator/health"
    echo "Metrics: http://localhost:8080/actuator/prometheus"
    echo "Grafana: http://localhost:3000 (admin/admin)"
    echo "Prometheus: http://localhost:9090"
}

# Main deployment function
main() {
    log "Starting NSE Stock Analysis Bot deployment..."
    
    check_prerequisites
    backup_data
    build_application
    deploy_application
    run_health_checks
    setup_webhook
    cleanup
    show_status
    
    success "Deployment completed successfully!"
    log "Logs are available at: $LOG_FILE"
}

# Handle command line arguments
case "${1:-deploy}" in
    "deploy")
        main
        ;;
    "backup")
        backup_data
        ;;
    "health")
        run_health_checks
        ;;
    "status")
        show_status
        ;;
    "logs")
        docker-compose -f "$COMPOSE_FILE" logs -f "${2:-nse-bot}"
        ;;
    "restart")
        docker-compose -f "$COMPOSE_FILE" restart "${2:-nse-bot}"
        ;;
    "stop")
        docker-compose -f "$COMPOSE_FILE" stop
        ;;
    "cleanup")
        cleanup
        ;;
    *)
        echo "Usage: $0 {deploy|backup|health|status|logs|restart|stop|cleanup}"
        echo ""
        echo "Commands:"
        echo "  deploy  - Full deployment (default)"
        echo "  backup  - Create backup only"
        echo "  health  - Run health checks only"
        echo "  status  - Show service status"
        echo "  logs    - Show logs (optionally specify service)"
        echo "  restart - Restart services (optionally specify service)"
        echo "  stop    - Stop all services"
        echo "  cleanup - Clean up unused resources"
        exit 1
        ;;
esac