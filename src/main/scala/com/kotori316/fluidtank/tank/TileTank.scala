package com.kotori316.fluidtank.tank

import alexiil.mc.lib.attributes._
import alexiil.mc.lib.attributes.fluid.FluidInvTankChangeListener
import alexiil.mc.lib.attributes.fluid.amount.{FluidAmount => BCAmount}
import alexiil.mc.lib.attributes.fluid.volume.{FluidKey, FluidVolume}
import com.kotori316.fluidtank.{FluidAmount, ModTank}
import com.kotori316.fluidtank.render.Box
import net.fabricmc.fabric.api.block.entity.BlockEntityClientSerializable
import net.minecraft.block.BlockState
import net.minecraft.block.entity.{BlockEntity, BlockEntityType}
import net.minecraft.nbt.NbtCompound
import net.minecraft.text.{LiteralText, Text}
import net.minecraft.util.Nameable
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

import scala.math.Ordering.Implicits.infixOrderingOps

class TileTank(var tier: Tiers, t: BlockEntityType[_ <: TileTank], pos: BlockPos, state: BlockState)
  extends BlockEntity(t, pos, state)
    with Nameable
    with BlockEntityClientSerializable
    with AttributeProviderBlockEntity {
  self =>

  def this(pos: BlockPos, state: BlockState) = {
    this(Tiers.Invalid, ModTank.Entries.TANK_BLOCK_ENTITY_TYPE, pos, state)
  }

  def this(t: Tiers, pos: BlockPos, state: BlockState) = {
    this(t, ModTank.Entries.TANK_BLOCK_ENTITY_TYPE, pos, state)
  }

  val tank = new Tank
  var connection: Connection = Connection.invalid
  var loading = false
  var stackName: Text = _

  override def writeNbt(compound: NbtCompound): NbtCompound = {
    compound.put(TileTank.NBT_Tank, tank.writeToNBT(new NbtCompound))
    compound.put(TileTank.NBT_Tier, tier.toNBTTag)
    getStackName.foreach(t => compound.putString(TileTank.NBT_StackName, Text.Serializer.toJson(t)))
    super.writeNbt(compound)
  }

  def getBlockTag: NbtCompound = {
    val nbt = writeNbt(new NbtCompound)
    Seq("x", "y", "z", "id").foreach(nbt.remove)
    nbt
  }

  override def readNbt(compound: NbtCompound): Unit = {
    super.readNbt(compound)
    tank.readFromNBT(compound.getCompound(TileTank.NBT_Tank))
    tier = Tiers.fromNBT(compound.getCompound(TileTank.NBT_Tier))
    if (compound.contains(TileTank.NBT_StackName)) {
      stackName = Text.Serializer.fromJson(compound.getString(TileTank.NBT_StackName))
    }
    loading = true
  }

  def readNBTClient(compound: NbtCompound): Unit = {
    tank.readFromNBT(compound.getCompound(TileTank.NBT_Tank))
    tier = Tiers.fromNBT(compound.getCompound(TileTank.NBT_Tier))
    if (compound.contains(TileTank.NBT_StackName)) {
      stackName = Text.Serializer.fromJson(compound.getString(TileTank.NBT_StackName))
    } else {
      stackName = null
    }
  }

  /*
    override def onDataPacket(net: NetworkManager, pkt: SUpdateTileEntityPacket): Unit = handleUpdateTag(pkt.getNbtCompound)
  */
  private def sendPacket(): Unit = {
    if (hasWorld && !world.isClient) sync()
  }

  def hasContent: Boolean = tank.getFluidAmount > 0

  def getComparatorLevel: Int = connection.getComparatorLevel

  def onBlockPlacedBy(): Unit = {
    val downTank = Option(getWorld.getBlockEntity(getPos.down())).collect { case t: TileTank => t }
    val upTank = Option(getWorld.getBlockEntity(getPos.up())).collect { case t: TileTank => t }
    val newSeq = (downTank, upTank) match {
      case (Some(dT), Some(uT)) => (dT.connection.seq :+ this) ++ uT.connection.seq
      case (None, Some(uT)) => this +: uT.connection.seq
      case (Some(dT), None) => dT.connection.seq :+ this
      case (None, None) => Seq(this)
    }
    Connection.createAndInit(newSeq)
  }

  def onDestroy(): Unit = {
    this.connection.remove(this)
  }

  def getStackName: Option[Text] = Option(stackName)

  override def getName: Text = getStackName
    .getOrElse(new LiteralText(tier.toString + " Tank"))

  override def hasCustomName: Boolean = stackName != null

  override def getCustomName: Text = getStackName.orNull

  class Tank extends FluidAmount.Tank {
    var box: Box = _
    var fluid: FluidAmount = FluidAmount.EMPTY
    var capacity: Int = com.kotori316.fluidtank.Utils.toInt(tier.amount)
    var listeners = Map.empty[FluidInvTankChangeListener, ListenerRemovalToken]

    def onContentsChanged(previous: FluidAmount): Unit = {
      sendPacket()
      if (!loading)
        connection.updateNeighbors()
      if ((!hasWorld || self.getWorld.isClient) && capacity != 0) {
        val a = 0.001
        val percent = a * 2.5 max (getFluidAmount.toDouble / capacity.toDouble)
        if (getFluidAmount > 0) {
          val d = 1d / 16d
          var maxY = 0d
          var minY = 0d
          if (tank.getFluid.isGaseous) {
            maxY = 1d - a
            minY = 1d - percent + a
          } else {
            minY = a
            maxY = percent - a
          }
          box = Box(d * 8, minY, d * 8, d * 8, maxY, d * 8, d * 12 - 0.01, percent, d * 12 - 0.01, firstSide = true, endSide = true)
        } else {
          box = null
        }
      }
      listeners.keys.foreach(_.onChange(this, 0, previous.fluidVolume, fluid.fluidVolume))
    }

    def readFromNBT(nbt: NbtCompound): Tank = {
      capacity = nbt.getInt(TileTank.NBT_Capacity)
      val fluid = FluidAmount.fromNBT(nbt)
      setFluid(fluid)
      onContentsChanged(FluidAmount.EMPTY)
      this
    }

    def writeToNBT(nbt: NbtCompound): NbtCompound = {
      fluid.write(nbt)
      nbt.putInt(TileTank.NBT_Capacity, capacity)
      nbt
    }

    override def toString: String = {
      val fluid = getFluid
      if (fluid == null) "Tank : no fluid : Capacity = " + capacity
      else "Tank : " + fluid.getLocalizedName + " " + getFluidAmount + "mB : Capacity = " + capacity
    }

    def canFillFluidType(fluid: FluidAmount): Boolean = {
      val fluidType = connection.getFluidStack
      fluidType.isEmpty || fluidType.exists(fluid.fluidEqual)
    }

    // Util methods
    def getFluidAmount: Long = fluid.fluidVolume.amount().asLong(1000L)

    def getFluid: FluidAmount = fluid

    def setFluid(fluidAmount: FluidAmount): Unit = {
      if (fluidAmount == null) fluid = FluidAmount.EMPTY
      else fluid = fluidAmount
    }

    // Change content

    /**
     * @return Fluid that was accepted by the tank.
     */
    override def fill(fluidAmount: FluidAmount, doFill: Boolean, min: Long = 0): FluidAmount = {
      if (canFillFluidType(fluidAmount) && fluidAmount.nonEmpty) {
        val previous = fluid
        val newAmount = fluid.fluidVolume.amount() add fluidAmount.fluidVolume.amount()
        if (BCAmount.of(capacity, 1000L) >= newAmount) {
          if (doFill) {
            fluid = fluidAmount.setAmount(newAmount)
            onContentsChanged(previous)
          }
          fluidAmount
        } else {
          val accept = BCAmount.of(capacity, 1000L) sub fluid.fluidVolume.amount()
          if (accept >= BCAmount.of(min, 1000L)) {
            if (doFill) {
              fluid = fluidAmount.setAmount(capacity)
              onContentsChanged(previous)
            }
            fluidAmount.setAmount(accept)
          } else {
            FluidAmount.EMPTY
          }
        }
      } else {
        FluidAmount.EMPTY
      }
    }

    /**
     * @param fluidAmount the fluid representing the kind and maximum amount to drain.
     *                    Empty Fluid means fluid type can be anything.
     * @param doDrain     false means simulating.
     * @param min         minimum amount to drain.
     * @return the fluid and amount that is (or will be) drained.
     */
    override def drain(fluidAmount: FluidAmount, doDrain: Boolean, min: Long = 0): FluidAmount = {
      if ((canFillFluidType(fluidAmount) || FluidAmount.EMPTY.fluidEqual(fluidAmount)) && fluid.nonEmpty) {
        val previous = fluid
        val drain = fluid.fluidVolume.amount() min fluidAmount.fluidVolume.amount()
        if (drain >= BCAmount.of(min, 1000L)) {
          val newAmount = fluid.fluidVolume.amount() sub drain
          if (doDrain) {
            fluid = fluid.setAmount(newAmount)
            onContentsChanged(previous)
          }
          fluid.setAmount(drain)
        } else {
          FluidAmount.EMPTY
        }
      } else {
        FluidAmount.EMPTY
      }
    }

    override def isFluidValidForTank(tank: Int, fluid: FluidKey): Boolean = canFillFluidType(FluidAmount(fluid.withAmount(BCAmount.BUCKET)))

    override def setInvFluid(tank: Int, to: FluidVolume, simulation: Simulation): Boolean = {
      if (simulation.isAction) {
        setFluid(FluidAmount(to))
      }
      true
    }

    override def getInvFluid(tank: Int): FluidVolume = getFluid.fluidVolume

    override def addListener(listener: FluidInvTankChangeListener, removalToken: ListenerRemovalToken): ListenerToken = {
      this.listeners = this.listeners + ((listener, removalToken))
      /*return*/ () => {
        this.listeners.get(listener).filter(_ == removalToken) match {
          case Some(value) =>
            this.listeners = this.listeners.removed(listener)
            value.onListenerRemoved()
          case None =>
        }
      }
    }

    override def getMaxAmount_F(tank: Int): BCAmount = BCAmount.of(capacity, 1000)
  }

  override def fromClientTag(tag: NbtCompound): Unit = readNbt(tag)

  override def toClientTag(tag: NbtCompound): NbtCompound = writeNbt(tag)

  override def addAllAttributes(to: AttributeList[_]): Unit = {
    to.offer(this.connection.handler)
  }
}

object TileTank {
  final val NBT_Tank = TankBlock.NBT_Tank
  final val NBT_Tier = TankBlock.NBT_Tier
  final val NBT_Capacity = TankBlock.NBT_Capacity
  final val NBT_BlockTag = TankBlock.NBT_BlockTag
  final val NBT_StackName = TankBlock.NBT_StackName
  final val bcId = "buildcraftcore"
  final val ae2id = "appliedenergistics2"

  def tick(world: World, pos: BlockPos, state: BlockState, tile: TileTank): Unit = {
    if (tile.loading && !world.isClient) {
      world.getProfiler.push("Connection Loading")
      if (tile.connection == Connection.invalid)
        Connection.load(world, pos)
      tile.loading = false
      world.getProfiler.pop()
    }
  }
}
