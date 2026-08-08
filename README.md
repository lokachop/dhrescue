# DH Rescue

> [!CAUTION]
> **DON'T USE THIS ON A PRODUCTION SERVER — THIS IS UNSAFE, ONLY USE IT ON LOCAL WORLDS**

> [!WARNING]
> This crashes on dedicated servers <sub>(you shouldn't use it there regardless)</sub>

> [!WARNING]
> This crashes whenever some block mods get restored <sub>(the only known one so far is [Farmer's Delight](https://modrinth.com/mod/farmers-delight))</sub>

Turns Distant Horizons LOD data back into ingame voxels, for recovery purposes.  
This is my first time writing java and making a fabric mod so i am sorry for the programming crimes in the code...

> [!NOTE]
> PR's welcome! <sub>(please help me make my horrible code actually usable)</sub>

> [!IMPORTANT]
> This will get no real support, other than accepting PRs

## Commands

| Command | Description |
|---|---|
| `/dhrescue_restoreradius <radiusChunks>` | Restores a square of chunks (Y 0–400) centered on the player. |
| `/dhrescue_restorearea <minX> <minY> <minZ> <maxX> <maxY> <maxZ>` | Restores a specific block-coordinate bounding box. |

## Dependencies

- Minecraft 1.21.1
- [Fabric Loader](https://fabricmc.net/use/) 0.14.21+
- [Fabric API](https://modrinth.com/mod/fabric-api)
- Java 21+
- [Distant Horizons](https://modrinth.com/mod/distanthorizons), with LOD data already generated for the area you want to restore

## Building

```bash
./gradlew build
```
