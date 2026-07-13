# 🧪 Agua de Florida Plugin

![Paper](https://img.shields.io/badge/Paper-1.21+-blue.svg)
![Java](https://img.shields.io/badge/Java-25-orange.svg)
![Version](https://img.shields.io/badge/Version-1.0.0-green.svg)

> *A mystical Minecraft plugin that brings the spiritual protection of Agua de Florida to your server*

## 🌟 Overview

**Agua de Florida** is a unique Minecraft plugin that introduces a culturally-inspired protective item. This enchanted water bucket replicates the exact behavior of the **Totem of Undying**, providing players with spiritual protection and a second chance at life.

## ✨ Features

### 🯤 **Mystical Protection**
- **Exact Totem Behavior**: Complete death prevention with health restoration
- **Spiritual Effects**: Regeneration, Absorption, and Fire Resistance
- **Authentic Animations**: Original totem sound effects and particle displays
- **Custom Item**: Beautifully crafted enchanted water bucket with custom lore

### 🎮 **Gameplay Integration**
- **Smart Inventory Detection**: Automatically finds and consumes the item when needed
- **Configurable Effects**: Fully customizable potion effects and durations
- **Mob Drops**: Configure specific mobs to drop Agua de Florida items
- **Crafting Recipe**: Optional crafting recipe for player acquisition

### ⚙️ **Administrative Features**
- **Flexible Configuration**: Comprehensive config.yml with all aspects customizable
- **Command System**: Easy item distribution and plugin management
- **Permission-Based**: Granular permission control for all features
- **Debug Logging**: Optional detailed logging for troubleshooting

## 🚀 Quick Start

### Installation
1. Download the latest `AguaDeFloridaPlugin.jar` from releases
2. Place in your server's `plugins/` directory
3. Restart your server
4. Configure `plugins/AguaDeFloridaPlugin/config.yml` as desired

### Basic Commands
```bash
/aguadeflorida give [player] [amount]  # Give Agua de Florida items
/aguadeflorida reload                  # Reload configuration
/aguadeflorida help                    # Show available commands
```

### Permissions
```yaml
aguadeflorida.give    # Allow giving items to players
aguadeflorida.reload  # Allow reloading plugin configuration
```

## 📋 Configuration

The plugin is highly configurable through `config.yml`:

```yaml
# Item appearance and behavior
item:
  name: "&b&lAgua de Florida &r&7(Spiritual Protection)"
  material: WATER_BUCKET
  enchanted: true
  lore:
    - "&7A mystical water blessed with"
    - "&7protective spiritual energy."
    - ""
    - "&6◆ &eGrants one chance to escape death"
    - "&6◆ &eProvides spiritual cleansing"

# Death prevention effects
effects:
  restore_health: 1.0
  regeneration:
    duration: 900
    amplifier: 1
  absorption:
    duration: 100
    amplifier: 1
  fire_resistance:
    duration: 800
    amplifier: 0

# Mob drops configuration
mob_drops:
  enabled: true
  mobs:
    - WITCH
    - EVOKER
  drop_rate: 0.05
  looting_multiplier: 0.02

# Optional crafting recipe
recipe:
  enabled: true
  ingredients:
    - WATER_BUCKET
    - NETHER_STAR
    - GLOWSTONE_DUST
```

## 🛠️ Building from Source

### Prerequisites
- Java 25+
- Maven 3.8+
- Paper 1.21+ server for testing

### Build Process
```bash
# Clone the repository
git clone <repository-url>
cd agua-de-florida

# Build the plugin
mvn clean package

# The jar will be in target/AguaDeFloridaPlugin-1.0.0.jar
```

### Development Environment
```bash
# Run test server with Docker
docker-compose up -d

# Debug the plugin
./debug-plugin.sh

# Run tests
mvn test
```

## 🎯 How It Works

When a player would normally die while holding Agua de Florida:

1. **Death Detection**: Plugin intercepts the `PlayerDeathEvent`
2. **Item Verification**: Searches inventory for Agua de Florida items
3. **Death Prevention**: Cancels death and restores health to 1.0
4. **Effect Application**: Applies regeneration, absorption, and fire resistance
5. **Visual Feedback**: Plays totem sounds and particle effects
6. **Item Consumption**: Removes one Agua de Florida from inventory

## 🔧 Advanced Configuration

### Custom Mob Drops
```yaml
mob_drops:
  enabled: true
  mobs:
    - WITCH
    - EVOKER
    - ZOMBIE  # Add custom mobs
  drop_rate: 0.05
  looting_multiplier: 0.02
  log_drops: true  # Debug mob drop events
```

### Effect Customization
```yaml
effects:
  restore_health: 2.0  # Restore more health
  custom_effects:
    SPEED:
      duration: 600
      amplifier: 1
    NIGHT_VISION:
      duration: 1200
      amplifier: 0
```

### Animation Control
```yaml
animation:
  show_animation: true
  play_sound: true
  custom_particles: true
  consume_on_use: true
```

## 🐛 Troubleshooting

### Common Issues

**Plugin not loading?**
- Ensure Java 25+ is installed
- Check that Paper 1.21+ is running
- Verify plugin jar is in the plugins directory

**Items not working?**
- Check that `consume_on_use` is enabled
- Verify player has the item in their inventory
- Enable debug logging to trace item detection

**Configuration not applying?**
- Use `/aguadeflorida reload` after config changes
- Check console for configuration errors
- Ensure YAML syntax is correct

### Debug Logging
Enable detailed logging in config.yml:
```yaml
debug:
  enabled: true
  log_saves: true
  log_drops: true
```

## 🤝 Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our development standards and submission process.

### Development Standards
- Follow Paper API best practices
- Maintain compatibility with Java 25+
- Write comprehensive Javadoc comments
- Include unit tests for new features

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Inspired by the traditional use of Agua de Florida in spiritual practices
- Built with the excellent [Paper API](https://papermc.io/)
- Community feedback and testing

---

**Made with ❤️ for the Minecraft community**

*Agua de Florida - Where tradition meets digital adventure*
