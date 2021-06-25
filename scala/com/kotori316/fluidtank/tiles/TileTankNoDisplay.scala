package com.kotori316.fluidtank.tiles

import buildcraft.api.tiles.IDebuggable
import buildcraft.api.transport.pipe.ICustomPipeConnection
import com.kotori316.fluidtank.Utils
import com.kotori316.fluidtank.packet.{PacketHandler, SideProxy, TileMessage}
import com.kotori316.fluidtank.render.Box
import com.kotori316.fluidtank.tiles.TileTankNoDisplay.getFluidHeight
import net.minecraft.block.state.IBlockState
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.network.NetworkManager
import net.minecraft.network.play.server.SPacketUpdateTileEntity
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumFacing.Axis
import net.minecraft.util.math.{BlockPos, MathHelper}
import net.minecraft.util.text.{ITextComponent, TextComponentString}
import net.minecraft.world.World
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fml.common.Optional

import java.util

@Optional.Interface(modid = TileTankNoDisplay.bcId, iface = "buildcraft.api.transport.pipe.ICustomPipeConnection")
@Optional.Interface(modid = TileTankNoDisplay.bcId, iface = "buildcraft.api.tiles.IDebuggable")
class TileTankNoDisplay(var tier: Tiers) extends TileEntity with ICustomPipeConnection with IDebuggable {
  self =>

  def this() {
    this(Tiers.Invalid)
  }

  val tank = new Tank
  var connection = Connection.invalid
  var loading = false
  var stackName: String = _
  val fluidHandlerWrapper = new Connection.HandlerWrapper(() => this.connection)

  override def writeToNBT(compound: NBTTagCompound): NBTTagCompound = {
    compound.setTag(TileTankNoDisplay.NBT_Tank, tank.writeToNBT(new NBTTagCompound))
    compound.setTag(TileTankNoDisplay.NBT_Tier, tier.toNBTTag)
    getStackName.foreach(compound.setString(TileTankNoDisplay.NBT_StackName, _))
    super.writeToNBT(compound)
  }

  def getBlockTag: NBTTagCompound = {
    val nbt = writeToNBT(new NBTTagCompound)
    Seq("x", "y", "z", "id").foreach(nbt.removeTag)
    nbt
  }

  override def getUpdateTag: NBTTagCompound = writeToNBT(new NBTTagCompound)

  override def getUpdatePacket = new SPacketUpdateTileEntity(getPos, 0, getUpdateTag)

  override def readFromNBT(compound: NBTTagCompound): Unit = {
    super.readFromNBT(compound)
    tank.readFromNBT(compound.getCompoundTag(TileTankNoDisplay.NBT_Tank))
    tier = Tiers.fromNBT(compound.getCompoundTag(TileTankNoDisplay.NBT_Tier))
    if (compound.hasKey(TileTankNoDisplay.NBT_StackName)) {
      stackName = compound.getString(TileTankNoDisplay.NBT_StackName)
    }
    loading = true
  }

  def readNBTClient(compound: NBTTagCompound): Unit = {
    tank.readFromNBT(compound.getCompoundTag(TileTankNoDisplay.NBT_Tank))
    tier = Tiers.fromNBT(compound.getCompoundTag(TileTankNoDisplay.NBT_Tier))
    if (compound.hasKey(TileTankNoDisplay.NBT_StackName)) {
      stackName = compound.getString(TileTankNoDisplay.NBT_StackName)
    } else {
      stackName = null
    }
  }

  override def onDataPacket(net: NetworkManager, pkt: SPacketUpdateTileEntity): Unit = handleUpdateTag(pkt.getNbtCompound)

  override def onLoad(): Unit = {
    super.onLoad()
    if (loading && SideProxy.isServer(this)) {
      getWorld.profiler.startSection("Connection Loading")
      if (this.connection == Connection.invalid)
        Connection.load(getWorld, getPos)
      loading = false
      getWorld.profiler.endSection()
    }
  }

  //noinspection ComparingUnrelatedTypes
  override def getCapability[T](capability: Capability[T], facing: EnumFacing): T = {
    if (hasWorld && world.isRemote && capability == net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
      // com.kotori316.fluidtank.FluidTank.LOGGER.info("Called getCapability in client side.")
      // Returning handler on client side? Not recommend, but Evil Craft requires this. See https://github.com/Kotori316/FluidTank/issues/14 .
      return net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(this.tank)
    }
    if (capability == net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
      // Ender IO tries to cache fluid handler. So we have to have mutable handler.
      return net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(this.fluidHandlerWrapper)
    }
    val c = connection.getCapability(capability, facing)
    if (c != null) c else super.getCapability(capability, facing)
  }

  override def hasCapability(capability: Capability[_], facing: EnumFacing): Boolean =
    connection.hasCapability(capability, facing) || super.hasCapability(capability, facing)

  private def sendPacket(): Unit = {
    if (SideProxy.isServer(this)) PacketHandler.WRAPPER.sendToDimension(TileMessage(this), getWorld.provider.getDimension)
  }

  def hasContent: Boolean = tank.getFluidAmount > 0

  override def hasFastRenderer = true

  def getComparatorLevel: Int = connection.getComparatorLevel

  def onBlockPlacedBy(): Unit = {
    val downTank = Option(getWorld.getTileEntity(getPos.down())).collect { case t: TileTankNoDisplay => t }
    val upTank = Option(getWorld.getTileEntity(getPos.up())).collect { case t: TileTankNoDisplay => t }
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

  def getStackName: Option[String] = Option(stackName)

  override def getDisplayName: ITextComponent = getStackName.map(new TextComponentString(_)).orNull

  class Tank extends net.minecraftforge.fluids.FluidTank(Utils.toInt(tier.amount)) {
    setTileEntity(self)
    var box: Box = _

    override def onContentsChanged(): Unit = {
      super.onContentsChanged()
      sendPacket()
      connection.updateNeighbors()
      if (!SideProxy.isServer(self) && getCapacity != 0) {
        if (getFluidAmount > 0) {
          val d = 1d / 16d
          val lowerBound = 0.001d
          val upperBound = 0.999d
          val (minY, maxY) = getFluidHeight(capacity.toDouble, getFluidAmount.toDouble, lowerBound, upperBound, 0.003, tank.getFluid.getFluid.isGaseous(tank.getFluid))
          box = Box(d * 8, minY, d * 8, d * 8, maxY, d * 8, d * 12 - 0.01, maxY - minY, d * 12 - 0.01, firstSide = true, endSide = true)
        } else {
          box = null
        }
      }
    }

    override def readFromNBT(nbt: NBTTagCompound) = {
      setCapacity(nbt.getInteger(TileTankNoDisplay.NBT_Capacity))
      val fluid = FluidStack.loadFluidStackFromNBT(nbt)
      setFluid(fluid)
      onContentsChanged()
      this
    }

    override def writeToNBT(nbt: NBTTagCompound): NBTTagCompound = {
      super.writeToNBT(nbt)
      nbt.setInteger(TileTankNoDisplay.NBT_Capacity, getCapacity)
      nbt
    }

    override def toString: String = {
      val fluid = getFluid
      if (fluid == null) "Tank : no fluid : Capacity = " + getCapacity
      else "Tank : " + fluid.getLocalizedName + " " + getFluidAmount + "mB : Capacity = " + getCapacity
    }

    override def canFillFluidType(fluid: FluidStack): Boolean = {
      val fluidType = connection.getFluidStack
      fluidType.isEmpty || fluidType.contains(fluid)
    }
  }

  @Optional.Method(modid = TileTankNoDisplay.bcId)
  override def getExtension(world: World, pos: BlockPos, face: EnumFacing, state: IBlockState): Float =
    if (face.getAxis == Axis.Y) 0 else 2 / 16f

  @Optional.Method(modid = TileTankNoDisplay.bcId)
  override def getDebugInfo(left: util.List[String], right: util.List[String], side: EnumFacing): Unit = {
    if (SideProxy.isServer(this)) {
      left add getClass.getName
      left add connection.toString
    }
    left.add("Tier : " + tier)
    left add tank.toString
  }
}

object TileTankNoDisplay {
  final val NBT_Tank = "tank"
  final val NBT_Tier = "tier"
  final val NBT_Capacity = "capacity"
  final val NBT_BlockTag = "BlockEntityTag"
  final val NBT_StackName = "stackName"
  final val bcId = "buildcraftcore"
  final val ae2id = "appliedenergistics2"

  /**
    *
    * @param capacity   the capacity of tank. Must not be 0.
    * @param amount     the amount in the tank, assumed to be grater than 0. (amount > 0)
    * @param lowerBound the minimum of fluid position.
    * @param upperBound the maximum of fluid position.
    * @param isGaseous  whether the fluid is gas or not.
    * @return (minY, maxY)
    */
  def getFluidHeight(capacity: Double, amount: Double, lowerBound: Double, upperBound: Double, minRatio: Double, isGaseous: Boolean): (Double, Double) = {
    val ratio = MathHelper.clamp(amount / capacity, minRatio, 1)
    val height = (upperBound - lowerBound) * ratio
    if (isGaseous) {
      (upperBound - height, upperBound)
    } else {
      (lowerBound, lowerBound + height)
    }
  }
}
