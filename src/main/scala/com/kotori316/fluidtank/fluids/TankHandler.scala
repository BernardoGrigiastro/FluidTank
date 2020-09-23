package com.kotori316.fluidtank.fluids

import cats.data.Chain
import cats.implicits.catsSyntaxFoldOps
import com.kotori316.fluidtank._
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler

class TankHandler extends IFluidHandler {

  private[this] final var tank: Tank = Tank.EMPTY

  def setTank(newTank: Tank): Unit = {
    this.tank = newTank
    onContentsChanged()
  }

  def getTank: Tank = tank

  def onContentsChanged(): Unit = ()

  override def getTanks: Int = 1

  override def getFluidInTank(tank: Int): FluidStack =
    getDrainOperation.runS((), FluidAmount.EMPTY.setAmount(this.tank.capacity)).toStack

  override def getTankCapacity(tank: Int): Int = Utils.toInt(this.tank.capacity)

  // Discards current state.
  override def isFluidValid(tank: Int, stack: FluidStack): Boolean = true

  protected def getFillOperation: TankOperation = fillOp(this.tank)

  protected def getDrainOperation: TankOperation = drainOp(this.tank)

  override final def fill(resource: FluidStack, action: IFluidHandler.FluidAction): Int = {
    val (log, left, newTank) = getFillOperation.run((), FluidAmount.fromStack(resource))
    val filledAmount: Int = Utils.toInt(resource.getAmount - left.amount)
    if (action.execute())
      setTank(newTank)
    outputLog(log, action)
    filledAmount
  }

  private final def drainInternal(toDrain: FluidAmount, action: IFluidHandler.FluidAction): FluidStack = {
    val (log, left, newTank) = getDrainOperation.run((), toDrain)
    val drained = toDrain - left
    if (action.execute())
      setTank(newTank)
    outputLog(log, action)
    drained.toStack
  }

  override final def drain(resource: FluidStack, action: IFluidHandler.FluidAction): FluidStack = drainInternal(FluidAmount.fromStack(resource), action)

  override final def drain(maxDrain: Int, action: IFluidHandler.FluidAction): FluidStack = drainInternal(FluidAmount.EMPTY.setAmount(maxDrain), action)

  protected def outputLog(logs: Chain[FluidTransferLog], action: IFluidHandler.FluidAction): Unit = {
    if (Utils.isInDev) {
      FluidTank.LOGGER.debug(ModObjects.MARKER_TankHandler, logs.mkString_(action.toString + " ", ", ", ""))
    }
  }
}

object TankHandler {
  def apply(capacity: Long): TankHandler = {
    val h = new TankHandler
    h.setTank(h.getTank.copy(capacity = capacity))
    h
  }

  def apply(tank: Tank): TankHandler = {
    val h = new TankHandler()
    h setTank tank
    h
  }
}
