#!/bin/bash

# Debug Script for Agua de Florida Plugin
# Interactive debugging and testing commands

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
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

print_command() {
    echo -e "${CYAN}[CMD]${NC} $1"
}

# Check if server is running
check_server() {
    if ! screen -list | grep -q "minecraft"; then
        print_error "Minecraft server is not running"
        print_status "Start the server with: make start"
        exit 1
    fi
    print_success "Server is running"
}

# Send command to server
send_command() {
    local cmd="$1"
    print_command "Executing: $cmd"
    screen -S minecraft -p 0 -X stuff "$cmd$(printf \\r)"
    sleep 1
}

# Show server logs
show_logs() {
    local lines="${1:-20}"
    print_status "Recent server logs ($lines lines):"
    if [[ -f "server/logs/latest.log" ]]; then
        tail -n "$lines" server/logs/latest.log
    else
        print_warning "No log file found"
    fi
}

# Test plugin commands
test_commands() {
    print_status "Testing Agua de Florida commands..."
    echo ""
    
    print_status "1. Testing help command"
    send_command "aguadeflorida help"
    
    print_status "2. Testing give command"
    send_command "aguadeflorida give @p 1"
    
    print_status "3. Testing reload command"
    send_command "aguadeflorida reload"
    
    print_status "Command test completed. Check logs for results."
}

# Create test player scenarios
test_scenarios() {
    print_status "Creating test scenarios..."
    echo ""
    
    print_status "1. Setting up test environment"
    send_command "gamemode survival @p"
    send_command "time set night"
    
    print_status "2. Giving Agua de Florida item"
    send_command "aguadeflorida give @p 1"
    
    print_status "3. Setting up death test (low health)"
    send_command "effect give @p minecraft:instant_damage 1 10"
    
    print_warning "Player should now have low health and Agua de Florida item"
    print_status "Trigger death to test protection mechanism"
}

# Test mob drops
test_mob_drops() {
    print_status "Testing mob drop functionality..."
    echo ""
    
    print_status "1. Spawning test mobs"
    send_command "summon minecraft:witch ~ ~ ~ {CustomName:'\"Test Witch\"'}"
    send_command "summon minecraft:evoker ~ ~ ~ {CustomName:'\"Test Evoker\"'}"
    
    print_status "2. Giving player weapons"
    send_command "give @p minecraft:diamond_sword{Enchantments:[{id:looting,lvl:3}]} 1"
    
    print_status "Kill the spawned mobs to test drop rates"
    print_warning "Check drops for Agua de Florida items"
}

# Check plugin status
check_plugin() {
    print_status "Checking plugin status..."
    echo ""
    
    # Check if plugin file exists
    if [[ -f "server/plugins/agua-de-florida-1.0.0.jar" ]]; then
        print_success "✓ Plugin JAR file present"
    else
        print_error "✗ Plugin JAR file missing"
    fi
    
    # Check if config exists
    if [[ -f "server/plugins/agua-de-florida/config.yml" ]]; then
        print_success "✓ Configuration file present"
    else
        print_warning "⚠ Configuration file not found"
    fi
    
    # Check logs for plugin loading
    if grep -q "Agua de Florida.*enabled" server/logs/latest.log 2>/dev/null; then
        print_success "✓ Plugin loaded successfully"
    else
        print_error "✗ Plugin may not have loaded"
    fi
    
    # Check for errors
    local error_count
    error_count=$(grep -c "ERROR.*AguaDeFloridaPlugin\|Exception.*aguadeflorida" server/logs/latest.log 2>/dev/null || echo "0")
    
    if [[ $error_count -eq 0 ]]; then
        print_success "✓ No plugin errors found"
    else
        print_warning "⚠ Found $error_count plugin error(s)"
    fi
}

# Interactive debugging menu
interactive_menu() {
    while true; do
        echo ""
        echo -e "${CYAN}=== Agua de Florida Debug Menu ===${NC}"
        echo "1. Check plugin status"
        echo "2. Test commands"
        echo "3. Test scenarios (death protection)"
        echo "4. Test mob drops"
        echo "5. Show recent logs"
        echo "6. Show configuration"
        echo "7. Manual command entry"
        echo "8. Exit"
        echo ""
        read -p "Select an option (1-8): " choice
        
        case $choice in
            1)
                check_plugin
                ;;
            2)
                test_commands
                ;;
            3)
                test_scenarios
                ;;
            4)
                test_mob_drops
                ;;
            5)
                read -p "Number of lines to show (default 20): " lines
                show_logs "${lines:-20}"
                ;;
            6)
                if [[ -f "server/plugins/agua-de-florida/config.yml" ]]; then
                    print_status "Plugin configuration:"
                    cat "server/plugins/agua-de-florida/config.yml"
                else
                    print_warning "Configuration file not found"
                fi
                ;;
            7)
                echo "Enter commands (type 'back' to return to menu):"
                while true; do
                    read -p "> " manual_cmd
                    if [[ "$manual_cmd" == "back" ]]; then
                        break
                    fi
                    if [[ -n "$manual_cmd" ]]; then
                        send_command "$manual_cmd"
                    fi
                done
                ;;
            8)
                print_status "Exiting debug menu"
                exit 0
                ;;
            *)
                print_warning "Invalid option. Please select 1-8."
                ;;
        esac
    done
}

# Main script execution
print_status "Agua de Florida Plugin Debug Script"
print_status "===================================="

# Check server status
check_server

# If arguments provided, run specific tests
if [[ $# -gt 0 ]]; then
    case "$1" in
        "status")
            check_plugin
            ;;
        "commands")
            test_commands
            ;;
        "scenarios")
            test_scenarios
            ;;
        "drops")
            test_mob_drops
            ;;
        "logs")
            show_logs "${2:-20}"
            ;;
        *)
            print_error "Unknown argument: $1"
            print_status "Available arguments: status, commands, scenarios, drops, logs"
            exit 1
            ;;
    esac
else
    # Run interactive menu
    interactive_menu
fi
