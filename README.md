# Sorting Daemon

A simple mod for Minecraft that adds some QoL features.

![License](https://img.shields.io/badge/license-CC0--1.0-lightgrey)
![Game Version](https://img.shields.io/badge/Minecraft-1.21.8-blue)
![Mod Loader](https://img.shields.io/badge/Loader-Fabric-orange)

![main-banner-min](https://github.com/user-attachments/assets/07251739-9497-4af3-a3ef-4876782e34d8)

# Features
- 📦 Sort chests and inventory with a single button
  <img width="1951" height="714" alt="screen-sort-min" src="https://github.com/user-attachments/assets/4d0e8984-b3c7-4085-a2e5-74c03779ac00" />
- 🖱️ **Drag to drop** — hold **Shift + LMB** and drag across slots → items are transferred just like with Shift+Click, but without spamming clicks

  ![min-drag-to-drop](https://github.com/user-attachments/assets/9ea44eb6-5555-448a-80ee-839de1bab827)
- 📥 **Quick deposit** — move matching items from your inventory into the opened container

  ![min-quick-deposit-and-sort](https://github.com/user-attachments/assets/aee100e5-984b-4b64-9f04-9c50d117bff5)
- ⭐ **Favorite Slots** — mark specific slots in your inventory as *favorite* so they won’t be sorted or quick-deposited, keeping your essentials safe

  ![min-favorite-slots-and-quick-deposit](https://github.com/user-attachments/assets/7042ad26-ff0b-4d9f-a1dc-94846d9d45d1)
- ⌨️ Set up key binds for yourself
- ⚙️ Easy installation (just drop it into `mods/`)

## Requirements
- Fabric Loader
- Fabric API

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/).  
2. Download and place **Fabric API** into your `mods/` folder.  
   - [Fabric API on Modrinth](https://modrinth.com/mod/fabric-api)  
   - [Fabric API on CurseForge](https://www.curseforge.com/minecraft/mc-mods/fabric-api)  
3. Download the latest release from the [Releases](../../releases) page  
   (e.g. `sortingdaemon-0.1.4-mc1.21.8.jar`) and put it into the same `mods/` folder.
4. Launch the game.

## How to use it
- Open your inventory and press the assigned sorting key (primary or alternative).
- Open any container (e.g. a chest) and press the sorting key to tidy it up.
- Drag to drop — hold down **Shift + LMB** on the slots to quickly transfer items between containers.  
- Quick Deposit — press assigned key while a container is open to move matching items into it.  
- Favorite Slots — hover over a slot and press **Ctrl + Z** to mark/unmark it as favorite.  
  - Favorite slots will not be affected by sorting or quick deposit.
- You can change keybinds in **Options → Controls → Key Binds**.

## Keybinds
| Action            | Default          |
|-------------------|------------------|
| Primary sort key  | **MMB**          |
| Alt sort key      | **G**            |
| Drag to drop      | **Shift + LMB**  |
| Quick deposit     | **K**            |
| Mark  as favorite | **Ctrl + Z**     |

## Compatibility
- Works in **Survival** and **Adventure** modes.
- In **Creative Mode**, the player's inventory cannot be sorted. ⚠️

## Building from source
```bash
# Clone and build
git clone https://github.com/Ahyysar/sortingdaemon
cd sortingdaemon
./gradlew build
# The mod jar will be in build/libs/
```

## License

This project is available under the CC0 license. Feel free to use the code, modify it, or include it in your own projects.
