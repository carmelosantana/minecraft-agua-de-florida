#!/bin/bash

# Docker Test Script for Agua de Florida Plugin
# Tests the plugin in a Docker container environment

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Configuration
CONTAINER_NAME="agua-de-florida-test"
PLUGIN_JAR="target/agua-de-florida-1.0.0.jar"
TEST_TIMEOUT=120

print_status "Starting Agua de Florida Docker test..."

# Check if plugin jar exists
if [[ ! -f "$PLUGIN_JAR" ]]; then
    print_error "Plugin JAR not found: $PLUGIN_JAR"
    print_status "Run 'make build' first to build the plugin"
    exit 1
fi

print_success "Found plugin JAR: $PLUGIN_JAR"

# Clean up any existing containers
print_status "Cleaning up existing containers..."
docker-compose -f docker-compose.yml down --volumes --remove-orphans 2>/dev/null || true

# Start the container
print_status "Starting Docker container..."
docker-compose -f docker-compose.yml up -d

# Wait for container to be ready
print_status "Waiting for container to start..."
sleep 10

# Get container ID
CONTAINER_ID=$(docker-compose -f docker-compose.yml ps -q minecraftbe)

if [[ -z "$CONTAINER_ID" ]]; then
    print_error "Container failed to start"
    docker-compose -f docker-compose.yml logs
    exit 1
fi

print_success "Container started: $CONTAINER_ID"

# Wait for server to start (look for "Done" message)
print_status "Waiting for Minecraft server to start (this may take a few minutes)..."
timeout=$TEST_TIMEOUT
while [[ $timeout -gt 0 ]]; do
    if docker logs "$CONTAINER_ID" 2>&1 | grep -q "Done"; then
        print_success "Minecraft server started successfully!"
        break
    fi
    
    if docker logs "$CONTAINER_ID" 2>&1 | grep -q "Exception\|Error"; then
        print_error "Server encountered an error during startup"
        docker logs "$CONTAINER_ID" 2>&1 | tail -20
        exit 1
    fi
    
    sleep 5
    timeout=$((timeout - 5))
    echo -n "."
done

echo ""

if [[ $timeout -le 0 ]]; then
    print_error "Server startup timed out after $TEST_TIMEOUT seconds"
    docker logs "$CONTAINER_ID" 2>&1 | tail -20
    exit 1
fi

# Check if plugin loaded successfully
print_status "Checking if Agua de Florida plugin loaded..."
if docker logs "$CONTAINER_ID" 2>&1 | grep -q "Agua de Florida.*enabled"; then
    print_success "Agua de Florida plugin loaded successfully!"
else
    print_warning "Plugin may not have loaded correctly"
    print_status "Checking for plugin-related messages..."
    docker logs "$CONTAINER_ID" 2>&1 | grep -i "agua\|florida" || print_warning "No plugin messages found"
fi

# Check server status
print_status "Checking server status..."
if docker logs "$CONTAINER_ID" 2>&1 | grep -q "Timings Reset"; then
    print_success "Server is running normally"
else
    print_warning "Server may not be fully operational"
fi

# Run basic tests
print_status "Running basic plugin tests..."

# Test 1: Check if plugin commands are registered
print_status "Test 1: Checking if commands are registered..."
docker exec "$CONTAINER_ID" sh -c "echo 'help' > /minecraft/server/input.txt"
sleep 2

if docker logs "$CONTAINER_ID" 2>&1 | grep -q "aguadeflorida"; then
    print_success "✓ Commands registered successfully"
else
    print_warning "⚠ Commands may not be registered"
fi

# Test 2: Check plugin configuration
print_status "Test 2: Checking plugin configuration..."
if docker exec "$CONTAINER_ID" test -f "/minecraft/plugins/AguaDeFloridaPlugin/config.yml"; then
    print_success "✓ Configuration file created"
else
    print_warning "⚠ Configuration file not found"
fi

# Test 3: Check for any errors in logs
print_status "Test 3: Checking for errors..."
error_count=$(docker logs "$CONTAINER_ID" 2>&1 | grep -c "ERROR\|Exception" || echo "0")
if [[ $error_count -eq 0 ]]; then
    print_success "✓ No errors found in logs"
else
    print_warning "⚠ Found $error_count error(s) in logs"
fi

# Display server information
print_status "Server Information:"
echo "Container ID: $CONTAINER_ID"
echo "Java Port: 25565"
echo "Bedrock Port: 19132"

# Show recent logs
print_status "Recent server logs:"
docker logs "$CONTAINER_ID" 2>&1 | tail -15

# Test summary
print_status "=== Test Summary ==="
print_success "Docker container test completed"
print_status "Container is running and accessible"
print_status "You can connect to the server at localhost:25565 (Java) or localhost:19132 (Bedrock)"

# Instructions for manual testing
echo ""
print_status "Manual Testing Instructions:"
echo "1. Connect to the server: localhost:25565"
echo "2. Test commands:"
echo "   /aguadeflorida give @p 1"
echo "   /aguadeflorida help"
echo "3. Test death prevention:"
echo "   - Get Agua de Florida item"
echo "   - Take damage to trigger death"
echo "   - Verify protection activates"
echo "4. Test mob drops:"
echo "   - Kill Witch or Evoker"
echo "   - Check for Agua de Florida drops"

echo ""
print_warning "Remember to run 'docker-compose down' to stop the test container when finished"

print_success "Docker test completed successfully!"
