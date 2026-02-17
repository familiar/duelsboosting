# RiceUtils

A Minecraft 1.8.9 Forge mod that detects and tracks players in real-time.

## Features

- **Automatic Player Detection**: Detects players when they join (2v2 or 4v4 matches)
- **Chat Logging**: Displays player coordinates and Y position in chat
- **Visual Tracking**: Renders colored boxes around detected players
- **Smart Filtering**: Only tracks players that are:
  - Not null
  - Alive
  - Not invisible
  - Within 80 blocks
  - Different from yourself

## Chat Triggers

The mod activates when it detects these chat messages:
- `.* has joined \(./2\)!` (for 2v2 matches)
- `.* has joined \(./4\)!` (for 4v4 matches)

## Building

```bash
./gradlew build
```

The built jar will be in `build/libs/`

## Installation

1. Install Minecraft Forge 1.8.9
2. Drop the jar file into your `mods` folder
3. Launch Minecraft

## Usage

The mod automatically activates when players join a game. No commands or configuration needed!

## Technical Details

- **Scan Range**: 80 blocks
- **Detection Method**: Entity world scanning
- **Rendering**: Custom OpenGL rendering with colored bounding boxes
- **Colors**: Automatically assigns different colors to different players

## Credits

Created by spaghetcodes


