# RiceUtils - Usage Guide

## How It Works

RiceUtils automatically detects and tracks players in your Minecraft world based on chat messages.

### Activation

The mod activates when it sees these chat patterns:
- `<player> has joined (1/2)!`
- `<player> has joined (2/2)!`
- `<player> has joined (1/4)!`
- `<player> has joined (2/4)!`
- `<player> has joined (3/4)!`
- `<player> has joined (4/4)!`

### What Happens When Triggered

1. **Instant Scan**: The mod immediately scans all players in your world
2. **Filtering**: It filters players based on these criteria:
   - Entity exists (not null)
   - Both you and the entity are alive
   - Entity is not invisible
   - Entity is within 80 blocks
   - Entity has a different name than you

3. **Chat Output**: For each valid player, it displays:
   ```
   [RiceUtils] ===== Detected 1 Player(s) =====
   [RiceUtils] Player: PlayerName
   [RiceUtils] Position: X: 123.45, Y: 67.89, Z: -234.56
   [RiceUtils] ================================
   ```

4. **Visual Overlay**: A colored bounding box appears around each detected player:
   - **Filled box** with transparency for better visibility
   - **Outlined box** for precise tracking
   - Different colors for different players (Red, Green, Blue, Yellow, etc.)

### Visual Features

- **Auto-color assignment**: Each player gets a unique color
- **Smooth rendering**: Uses partial ticks for smooth interpolation
- **Distance-based cleanup**: Automatically stops tracking players beyond 80 blocks
- **Death detection**: Removes dead players from tracking

### Example Scenario

You're in a Hypixel Bedwars lobby:
1. Someone joins: `Steve has joined (1/4)!`
2. RiceUtils immediately scans and finds Steve
3. Chat shows: `Player: Steve, Position: X: 100.00, Y: 64.00, Z: 200.00`
4. A red box appears around Steve
5. You can see Steve's exact position through walls!

## Technical Details

### Entity Filtering Logic
```kotlin
- entity != null
- entity.displayName != yourName
- entity.isEntityAlive == true
- player.isEntityAlive == true
- entity.isInvisible == false
- distance <= 80 blocks
```

### Rendering Details
- Uses OpenGL for rendering
- Disables depth testing for see-through effect
- Renders both filled and outlined boxes
- Updates every render tick for smooth movement

## Troubleshooting

**Q: The mod doesn't detect players**
- Make sure the chat message matches the exact pattern
- Check if players are within 80 blocks
- Verify players are not invisible

**Q: The boxes don't appear**
- Check if the players are still alive
- Verify they haven't moved beyond 80 blocks
- Make sure your render distance is high enough

**Q: Too many colors are confusing**
- The mod cycles through 8 colors: Red, Green, Blue, Yellow, Magenta, Cyan, Orange, Purple
- Each player gets a consistent color throughout their tracking session


