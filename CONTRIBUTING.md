# Contributing to Agua de Florida Plugin

Thank you for your interest in contributing to the Agua de Florida plugin! This document provides guidelines and standards for development.

## 📋 Table of Contents

- [Development Environment](#development-environment)
- [Code Standards](#code-standards)
- [Testing Requirements](#testing-requirements)
- [Submission Process](#submission-process)
- [Plugin Architecture](#plugin-architecture)

## 🛠️ Development Environment

### Prerequisites
- **Java 21+** (OpenJDK recommended)
- **Maven 3.8+** for dependency management
- **Paper 1.21+** for testing
- **IntelliJ IDEA** or **Eclipse** (IDE with Minecraft development plugins)

### Setup
1. Clone the repository
2. Import as Maven project in your IDE
3. Run `mvn clean compile` to download dependencies
4. Set up Paper test server using `docker-compose up -d`

### Project Structure
```
agua-de-florida/
├── src/main/java/org/xpfarm/aguadeflorida/
│   ├── AguaDeFloridaPlugin.java       # Main plugin class
│   ├── commands/
│   │   └── AguaCommand.java           # Command handlers
│   ├── listeners/
│   │   ├── PlayerDeathListener.java   # Death prevention logic
│   │   └── MobDeathListener.java      # Mob drop handling
│   └── utils/
│       ├── ConfigManager.java         # Configuration management
│       └── AguaItemBuilder.java       # Custom item creation
├── src/main/resources/
│   ├── plugin.yml                     # Plugin metadata
│   └── config.yml                     # Default configuration
└── pom.xml                           # Maven configuration
```

## 📐 Code Standards

### Java Conventions
- **Package Naming**: Use `org.xpfarm.aguadeflorida` as base package
- **Class Naming**: PascalCase (e.g., `PlayerDeathListener`)
- **Method Naming**: camelCase (e.g., `handlePlayerDeath`)
- **Variable Naming**: camelCase (e.g., `playerName`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `MAX_HEALTH`)

### Documentation Requirements
- **Javadoc**: All public classes and methods must have comprehensive Javadoc
- **Inline Comments**: Complex logic should be explained with comments
- **README Updates**: Update README.md for new features

Example Javadoc:
```java
/**
 * Handles player death events and activates Agua de Florida protection
 * 
 * @param event The PlayerDeathEvent to process
 * @see PlayerDeathEvent
 * @since 1.0.0
 */
@EventHandler(priority = EventPriority.HIGH)
public void onPlayerDeath(PlayerDeathEvent event) {
    // Implementation
}
```

### Paper API Best Practices
- **Event Priorities**: Use appropriate event priorities (HIGH for death prevention)
- **Adventure API**: Use Adventure components for text formatting
- **Async Safety**: Never modify game state from async threads
- **Resource Management**: Properly clean up resources in `onDisable()`

### Configuration Management
- **Validation**: Always validate configuration values
- **Defaults**: Provide sensible defaults for all options
- **Backwards Compatibility**: Maintain config compatibility between versions
- **Documentation**: Document all configuration options

## 🧪 Testing Requirements

### Unit Testing
- **Coverage**: Aim for 80%+ test coverage
- **Framework**: Use JUnit 5 for unit tests
- **Mocking**: Use Mockito for mocking Bukkit/Paper APIs

Example test structure:
```java
@Test
@DisplayName("Should activate Agua de Florida on player death")
void shouldActivateAguaDeFloridaOnDeath() {
    // Given
    Player mockPlayer = mock(Player.class);
    PlayerDeathEvent mockEvent = mock(PlayerDeathEvent.class);
    
    // When
    listener.onPlayerDeath(mockEvent);
    
    // Then
    verify(mockEvent).setCancelled(true);
}
```

### Integration Testing
- **Test Server**: Use provided Docker setup for integration tests
- **Real Scenarios**: Test complete workflows (death prevention, commands, etc.)
- **Configuration Testing**: Test various configuration combinations

### Manual Testing Checklist
- [ ] Plugin loads without errors
- [ ] Commands work with proper permissions
- [ ] Death prevention activates correctly
- [ ] Effects are applied as configured
- [ ] Mob drops work with configured rates
- [ ] Configuration reloading works
- [ ] Resource cleanup on disable

## 📝 Submission Process

### Pull Request Guidelines
1. **Branch Naming**: Use descriptive branch names (e.g., `feature/mob-drop-improvements`)
2. **Commit Messages**: Use conventional commit format
3. **Description**: Provide clear description of changes
4. **Testing**: Include test results and manual verification
5. **Documentation**: Update relevant documentation

### Commit Message Format
```
type(scope): description

[optional body]

[optional footer]
```

Types:
- `feat`: New features
- `fix`: Bug fixes
- `docs`: Documentation changes
- `style`: Code style changes
- `refactor`: Code refactoring
- `test`: Test additions/modifications
- `chore`: Build/dependency updates

Example:
```
feat(death-listener): add support for custom death messages

- Add configurable death prevention messages
- Support for multiple message formats
- Integration with existing config system

Closes #123
```

### Code Review Process
1. **Automated Checks**: All CI checks must pass
2. **Peer Review**: At least one maintainer review required
3. **Testing**: Manual testing verification required
4. **Documentation**: Ensure documentation is updated

## 🏗️ Plugin Architecture

### Core Components

#### AguaDeFloridaPlugin (Main Class)
- Plugin lifecycle management
- Component initialization
- Configuration coordination
- Logging and debugging

#### ConfigManager (Configuration)
- Configuration file parsing
- Value validation and caching
- Type-safe configuration access
- Runtime configuration updates

#### AguaItemBuilder (Item Creation)
- Custom item generation
- Item identification/validation
- Recipe registration
- Item caching for performance

#### Event Listeners
- **PlayerDeathListener**: Core death prevention logic
- **MobDeathListener**: Mob drop handling
- Event priority management
- Performance optimization

#### Commands
- **AguaCommand**: Primary command handler
- Permission checking
- Tab completion
- User-friendly error messages

### Design Principles

#### Single Responsibility
Each class should have one clear responsibility:
- ConfigManager: Only configuration management
- AguaItemBuilder: Only item-related operations
- Listeners: Only event handling

#### Dependency Injection
- Pass dependencies through constructors
- Avoid static references where possible
- Use plugin instance for component coordination

#### Performance Considerations
- Cache frequently accessed data
- Use efficient data structures
- Minimize object creation in event handlers
- Async operations where appropriate

#### Error Handling
- Graceful degradation on errors
- Comprehensive logging
- User-friendly error messages
- Configuration validation

### API Compatibility

#### Paper API Usage
- Use latest Paper API features when beneficial
- Maintain backwards compatibility when possible
- Follow Paper API deprecation guidelines
- Leverage Adventure API for text components

#### Version Support
- **Primary Target**: Paper 1.21+
- **Java Version**: 21+ (current LTS)
- **Compatibility**: Test with multiple Paper versions

## 🐛 Bug Reporting

### Issue Template
When reporting bugs, include:
- **Environment**: Server version, Java version, plugin version
- **Steps to Reproduce**: Clear, numbered steps
- **Expected Behavior**: What should happen
- **Actual Behavior**: What actually happens
- **Logs**: Relevant console output
- **Configuration**: Relevant config sections

### Debug Information
Enable debug logging:
```yaml
debug:
  enabled: true
  log_saves: true
  log_drops: true
```

## 🎯 Feature Requests

### Proposal Format
- **Use Case**: Why is this feature needed?
- **Proposal**: How should it work?
- **Implementation**: Technical considerations
- **Backwards Compatibility**: Any breaking changes?
- **Configuration**: New config options needed?

### Evaluation Criteria
- Alignment with plugin purpose
- Implementation complexity
- Performance impact
- User experience
- Maintenance burden

## 📞 Getting Help

- **Issues**: Use GitHub issues for bugs and features
- **Discussions**: Use GitHub discussions for questions
- **Documentation**: Check README.md and code comments
- **Examples**: Review existing code for patterns

Thank you for contributing to Agua de Florida! 🙏
