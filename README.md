<img width="4094" height="1023" alt="banner-2-min" src="https://github.com/user-attachments/assets/a7bcf539-34e4-4739-9c44-0cd2a90d06c3" />

![License](https://img.shields.io/badge/license-MIT-green)
![Game Version](https://img.shields.io/badge/Minecraft-1.21.8-blue)
![Mod Loader](https://img.shields.io/badge/Loader-Fabric-orange)

# About
Sorting Daemon is a lightweight mod that focuses on making inventory management in Minecraft faster and more convenient, without changing the core vanilla experience. It provides simple, intuitive tools that save time and keep gameplay smooth, while staying compatible with other mods.

# Features
- 📦 Sort chests and inventory with a single button
  <img width="1951" height="714" alt="screen-sort-min" src="https://github.com/user-attachments/assets/4d0e8984-b3c7-4085-a2e5-74c03779ac00" />
- 🖱️ **Drag to drop** — hold **Shift + LMB** and drag across slots → items are transferred just like with *Shift + Click*, but without spamming

  ![drag to drop (1)](https://github.com/user-attachments/assets/ba5dfa35-aa31-4207-9cd7-d17dea9bdd86)
- 📥 **Quick deposit** — move matching items from your inventory into the opened container

  ![quick deposit (1)](https://github.com/user-attachments/assets/6d469941-c251-4f00-801e-b5a5f8668567)
- ⭐ **Favorite slots** — mark specific slots in your inventory as favorite so they won’t be sorted or quick-deposited, keeping your essentials safe

  ![favorite slots (1)](https://github.com/user-attachments/assets/135c6db9-d21c-468a-8bf2-351a8b5536b3)
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
- Open any container (e.g. a chest) and press the sorting key to tidy it up.
- Drag to drop — hold down **Shift + LMB** on the slots to quickly transfer items between containers.  
- Quick deposit — press assigned key while a container is open to move matching items into it.  
- Favorite slots — hover over a slot and press **Ctrl + Z** to mark/unmark it as favorite. Favorite slots will not be affected by sorting or quick deposit.
- You can change keybinds in **Options → Controls → Key Binds**.

## Keybinds
| Action            | Default          |
|-------------------|------------------|
| Primary sort key  | **MMB**          |
| Alt sort key      | **G**            |
| Drag to drop      | **Shift + LMB**  |
| Quick deposit     | **K**            |
| Mark  as favorite | **Ctrl + Z**     |

## Building from source
```bash
# Clone and build
git clone https://github.com/Ahyysar/sortingdaemon
cd sortingdaemon
./gradlew build
# The mod jar will be in build/libs/
```

## License

This project is licensed under the **MIT License**.  
See the [LICENSE](LICENSE) file for details.
