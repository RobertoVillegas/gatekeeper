# Gatekeeper v2.0 - GUI Wizard Implementation Plan

## Overview

Replace the chat-based application wizard with a command + inventory GUI approach that:
- Avoids Minecraft 1.19.1+ signed chat issues
- Provides a better UX with visual server selection
- Works on both Java and Bedrock (Geyser translates to Bedrock forms)

## New User Flow

### Step 1: Player runs command with arguments
```
/apply "RealName" "Discord#1234" "InviterName"
```

Optional notes:
```
/apply "RealName" "Discord#1234" "InviterName" "I'm a friend of InviterName from school"
```

### Step 2: GUI opens for server selection and confirmation

```
+---------------------------------------+
|       Application Summary             |
+---------------------------------------+
|  [Paper] Name: RealName               |
|  [Book]  Discord: Discord#1234        |
|  [Head]  Invited by: InviterName      |
+---------------------------------------+
|  Select servers to request access:    |
|                                       |
|  [Grass]    [Painting]    [Chest]     |
|  Survival   Creative      SMP2        |
|    (*)        ( )          ( )        |
+---------------------------------------+
|  [Emerald]              [Barrier]     |
|   Submit                 Cancel       |
+---------------------------------------+
```

- Clicking a server toggles selection (glowing effect when selected)
- Default servers pre-selected based on config
- Submit button disabled until at least one server selected

### Step 3: Confirmation
- On submit: Application created, player notified, Discord webhook fired
- On cancel: GUI closes, nothing saved

## Technical Implementation

### Dependencies

Add to `velocity/build.gradle`:
```gradle
compileOnly("dev.simplix:protocolize-api:2.4.2")
```

Server requirement:
- Protocolize plugin must be installed on Velocity

### Files to Modify

1. **ApplyCommand.java** - Complete rewrite
   - Parse positional arguments: name, discord, inviter, notes (optional)
   - Validate input (non-empty, reasonable lengths)
   - Open GUI instead of starting wizard

2. **Delete wizard package** - No longer needed
   - `wizard/WizardManager.java`
   - `wizard/WizardSession.java`
   - `wizard/WizardState.java`

3. **Delete ChatListener.java** - No longer intercepting chat

4. **GatekeeperPlugin.java** - Remove wizard initialization

### New Files to Create

1. **gui/ApplicationGui.java** - Main GUI class
   ```java
   public class ApplicationGui {
       // Inventory setup
       // Click handlers
       // Server toggle logic
       // Submit/cancel actions
   }
   ```

2. **gui/GuiManager.java** - Track open GUIs per player
   ```java
   public class GuiManager {
       private final Map<UUID, ApplicationGui> openGuis = new ConcurrentHashMap<>();

       public void openApplicationGui(Player player, ApplicationData data);
       public void handleClick(Player player, int slot);
       public void handleClose(Player player);
   }
   ```

3. **gui/ItemBuilder.java** - Helper for creating GUI items
   ```java
   public class ItemBuilder {
       public static ItemStack createServerIcon(String serverName, boolean selected);
       public static ItemStack createSubmitButton(boolean enabled);
       public static ItemStack createCancelButton();
       public static ItemStack createInfoItem(String title, String value);
   }
   ```

### Protocolize Integration

Register packet listeners in plugin initialization:
```java
ProtocolizeApi api = ProtocolizeApi.getApi();

// Listen for inventory clicks
api.packetListenerProvider().registerListener(
    new PacketListener<ClickWindow>(ClickWindow.class, Direction.SERVERBOUND) {
        @Override
        public void packetReceive(PacketReceiveEvent<ClickWindow> event) {
            // Handle click
        }
    }
);

// Listen for inventory close
api.packetListenerProvider().registerListener(
    new PacketListener<CloseWindow>(CloseWindow.class, Direction.SERVERBOUND) {
        @Override
        public void packetReceive(PacketReceiveEvent<CloseWindow> event) {
            // Handle close
        }
    }
);
```

Opening inventory to player:
```java
Inventory inventory = Inventory.builder()
    .type(InventoryType.GENERIC_9X4)
    .title(Component.text("Application Summary"))
    .build();

// Set items
inventory.item(slot, itemStack);

// Open to player
ProtocolizePlayer protocolizePlayer = ProtocolizeApi.getApi()
    .playerProvider()
    .player(player.getUniqueId());
protocolizePlayer.openInventory(inventory);
```

### GUI Layout (36 slots - 9x4)

```
Slot layout:
 0  1  2  3  4  5  6  7  8
 9 10 11 12 13 14 15 16 17
18 19 20 21 22 23 24 25 26
27 28 29 30 31 32 33 34 35

Assignments:
- Row 0 (0-8): Title/decoration (gray glass panes)
- Row 1 (9-17): Info items
  - Slot 11: Name info
  - Slot 13: Discord info
  - Slot 15: Inviter info
- Row 2 (18-26): Server selection
  - Slots 19, 21, 23, 25: Server icons (up to 4 servers)
- Row 3 (27-35): Actions
  - Slot 30: Submit button
  - Slot 32: Cancel button
```

### Command Parsing

```java
// /apply "Name" "Discord" "Inviter" ["Notes"]
// Arguments are space-separated, quoted strings supported

public void execute(Player player, String[] args) {
    if (args.length < 3) {
        player.sendMessage("Usage: /apply \"YourName\" \"Discord#1234\" \"InviterName\" [\"Optional notes\"]");
        return;
    }

    String name = args[0];
    String discord = args[1];
    String inviter = args[2];
    String notes = args.length > 3 ? args[3] : null;

    // Validate
    if (name.length() > 32 || discord.length() > 37 || inviter.length() > 16) {
        player.sendMessage("Input too long");
        return;
    }

    // Open GUI
    guiManager.openApplicationGui(player, new ApplicationData(name, discord, inviter, notes));
}
```

### Bedrock Compatibility

Geyser automatically translates Java inventory GUIs to Bedrock form UIs:
- Chest inventory becomes a form with buttons
- Item names become button labels
- Clicking works the same way

No special handling needed - it just works!

## Migration Notes

### Breaking Changes
- `/apply` command syntax changes completely
- Chat-based wizard removed
- Requires Protocolize plugin to be installed

### Configuration Updates

Add to `config.yml`:
```yaml
gui:
  # Title shown in the GUI
  title: "Server Access Application"

  # Server display configuration
  servers:
    survival:
      display_name: "Survival"
      material: GRASS_BLOCK
      description: "Main survival server"
    creative:
      display_name: "Creative"
      material: PAINTING
      description: "Creative building server"
```

## Testing Plan

1. **Command parsing**
   - Test with valid arguments
   - Test with missing arguments
   - Test with quoted strings containing spaces
   - Test with special characters

2. **GUI functionality**
   - Test server selection toggle
   - Test submit with no servers selected (should fail)
   - Test submit with servers selected
   - Test cancel button
   - Test closing GUI via escape key

3. **Bedrock compatibility**
   - Test on Bedrock client via Geyser
   - Verify form UI appears correctly
   - Verify button clicks work

4. **Integration**
   - Verify application created in database
   - Verify Discord webhook fires
   - Verify player receives confirmation

## Implementation Order

1. Add Protocolize dependency to build.gradle
2. Create ItemBuilder utility class
3. Create GuiManager class
4. Create ApplicationGui class
5. Rewrite ApplyCommand to use new system
6. Remove old wizard classes and ChatListener
7. Update GatekeeperPlugin initialization
8. Update config schema for GUI settings
9. Test on Java client
10. Test on Bedrock client via Geyser
11. Update documentation

## Estimated Scope

- **Files to create**: 3-4 new classes
- **Files to modify**: 2-3 existing classes
- **Files to delete**: 4 wizard-related classes
- **External dependency**: Protocolize plugin required
