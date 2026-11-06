# Better MCA

Better MCA is an addon for Minecraft Comes Alive. I made it because I wanted my villages to actually have a history: people get old, retire, die, and you can still find them years later in the family tree or in the cemetery.

## Features

### Aging
Villagers grow up and grow old over the years, retire, and eventually die.

### Time speed
The `mcaVieTimeSpeed` game rule sets how fast the years go by. You pick it when creating the world, and only operators can change it afterwards.

### Calendar and watch
The world keeps a calendar, and the watch shows the current date.

### Graves
When a villager dies, their grave shows their portrait, first name, surname, profession and dates. There are three kinds: a wooden cross, a headstone and a marble tomb, all craftable.

### Surnames
Villagers have family surnames. On an existing world, the last word after a space in a villager's name is taken as their surname.

### Family tree rework
You can add portraits, relatives are shown with no limit on how far back the tree goes, lines no longer cross each other, and dead villagers keep their profession.

### Phone photos
Take photos with the phone.

### Villagers understand floors
Villagers understand buildings with several floors, and the lumberjack ignores logs that players have placed.

## Requirements

- Minecraft 1.20.1 with Fabric (plus Fabric API) or Forge 47.x
- Minecraft Comes Alive 7.6.26
- Architectury API

## Building

The project is split into `common`, `fabric` and `forge` modules (Architectury). It compiles against local MCA jars: set `mca_common_jar`, `mca_fabric_jar`, `mca_forge_jar` and `mca_forge_dev_jar` in `gradle.properties`, then run `./gradlew build` with JDK 17 or newer. The jars to publish are `fabric/build/libs/better-mca-fabric-<version>.jar` and `forge/build/libs/better-mca-forge-<version>.jar`.

## License

GPL-3.0. See [LICENSE](LICENSE).
