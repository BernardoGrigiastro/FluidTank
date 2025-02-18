## v17.0.1
* Changed dependency system.

Build with 1.17.1-37.0.90, Mapping: Official 1.17.1

## v17.0.0
* First release for Minecraft 1.17.1 with Forge

Build with 1.17.1-37.0.84, Mapping: Official 1.17.1

## v16.1.8
* Fixed performance issue of large capacity tanks.
* Make the "top" and "bottom" of rendering fluid configurable. See the entries in config, `renderLowerBound` and `renderUpperBound`.

Build with net.minecraftforge:forge:1.16.5-36.1.0, Mapping: 20201028-1.16.3

## v16.1.7
* Added new property, "tank_pos" to the block of tanks. 
  [#79](https://github.com/Kotori316/FluidTank/issues/79) [#80](https://github.com/Kotori316/FluidTank/issues/80)
  * This property is created for changing models that depend on the position of tank.
  * This property doesn't affect anything in server side. (the content of tanks or drain/fill action)
* Changed the content and format of block state json.
  * For users, this change doesn't affect anything.

Build with net.minecraftforge:forge:1.16.5-36.1.0, Mapping: 20201028-1.16.3

## v16.1.6
* You can set sub items of reservoir recipe in json in datapack.

Build with net.minecraftforge:forge:1.16.5-36.0.42, Mapping: 20201028-1.16.3

## v16.1.5
* Fixed a bug that tank recipe didn't check the nbt of fluid.
* Added recipes to combine the content in tanks.
* Added reservoirs. Use them to drain fluids in the world and use it to tanks to transfer.
* Internal changes.

Build with net.minecraftforge:forge:1.16.5-36.0.42, Mapping: 20201028-1.16.3

## v16.1.4
* Fixed an issue of fluid NBT.
* Respect colors from fluid NBT.
* Rendering improvement. Honey and Chocolate from mod "Create" will be correctly rendered.

Build with net.minecraftforge:forge:1.16.5-36.0.1, Mapping: 20201028-1.16.3

## v16.1.3
* Fixed a problem of drain/fill tanks when world was reloaded.
* Removed usage of tick action of tanks.

Build with net.minecraftforge:forge:1.16.5-36.0.1, Mapping: 20201028-1.16.3

## v16.1.2
* Add a config option to disable convert invisible recipe.
* Add a config option to hide non-creatable tanks from high tier tank recipe.

Build with net.minecraftforge:forge:1.16.5-36.0.1, Mapping: 20201028-1.16.3

## v16.1.1
* Show tank content even if it has less than 1% of its capacity. #69
* Stopped log spam of converting message.
* Fixed that pipe didn't drain from Creative Tank.

Build with net.minecraftforge:forge:1.16.5-36.0.1, Mapping: 20201028-1.16.3

## v16.1.0
* Update for Minecraft 1.16.5
* Support Milk provided by Forge.
  * Milks by this mod will be converted into forge's one if you're in 1.16.5.
* Internal changes.

Build with net.minecraftforge:forge:1.16.5-36.0.1, Mapping: 20201028-1.16.3

## v16.0.9
* Fixed transparent texture not rendered correctly. #68
* Fixed fluid pipe wasn't visually connected.

Build with net.minecraftforge:forge:1.16.4-35.1.4, Mapping: 20201028-1.16.3

## v16.0.8
* Remove usage of unstable mixin. 
The error of 
"java.util.NoSuchElementException: Supplier is empty. Mixin system for `kotori_scala`(Scalable Cats Force) seems not working."
is fixed.

Build with net.minecraftforge:forge:1.16.4-35.0.18, Mapping: 20201028-1.16.3

## v16.0.7
* Update library, requires 2.13.3-build-6
* Many internal changes.
* Added support of mod "look".
* Fixed a potential bug of sync.
* Pipes can be dyed with leather armors.

Build with net.minecraftforge:forge:1.16.4-35.0.18, Mapping: 20201028-1.16.3

## v16.0.6
* Update Russian lang file.

Build with net.minecraftforge:forge:1.16.3-34.1.12, Mapping: 20200723-1.16.1

## v16.0.5
* The log is limited only in dev environment.
* Update library, requesting ScalableCatsForce >= 2.13.3
* Update forge.
* Added lang file of pt_br and fr_fr.

Build with net.minecraftforge:forge:1.16.3-34.1.0, Mapping: 20200723-1.16.1

## v16.0.4
* Support HWYLA
* Fixed a bug that Creative Tank fills normal tanks with empty fluid.

Build with net.minecraftforge:forge:1.16.2-33.0.5, Mapping: 20200723-1.16.1

## v16.0.3
* Update for 1.16.2

Build with net.minecraftforge:forge:1.16.2-33.0.0, Mapping: 20200723-1.16.1

## v16.0.1
* Fixed CAT got fluid from empty inventory.
* Fixed the pipe didn't have dummy bounding box when pipe was not connected to tank.
* Remove shadow from text in CAT tile.

Build with net.minecraftforge:forge:1.16.1-32.0.23, Mapping: 20200514-1.16

## v16.0.0-SNAPSHOT
* First release for Minecraft 1.16.1
* No support of tooltip mod.
* **Changed tank capacity.**
Build with net.minecraftforge:forge:1.16.1-32.0.6
