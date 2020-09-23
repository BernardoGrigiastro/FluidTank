package com.kotori316.fluidtank

import cats.data.{Chain, ReaderWriterStateT}
import cats.implicits.{catsSyntaxEq, catsSyntaxGroup, catsSyntaxSemigroup}
import cats.{Foldable, Id, Monad, Monoid}
import net.minecraft.fluid.Fluids
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler

package object fluids {
  type TankOperation = ReaderWriterStateT[Id, Unit, Chain[FluidTransferLog], FluidAmount, Tank]
  type ListTankOperation[F[_]] = ReaderWriterStateT[Id, Unit, Chain[FluidTransferLog], FluidAmount, F[Tank]]

  def fillOp(tank: Tank): TankOperation = ReaderWriterStateT { case (_, s) =>
    if (s.fluid === Fluids.EMPTY || s.amount === 0L) {
      // Nothing to fill, skip.
      (Chain(FluidTransferLog.Empty(s, tank)), s, tank)
    } else if (tank.fluidAmount.isEmpty || (tank.fluidAmount fluidEqual s)) {
      val filledAmount = (tank.capacity |-| tank.amount) min s.amount
      val filledStack = s.copy(amount = filledAmount)
      val newTank = tank.copy(tank.fluidAmount + filledStack)
      (Chain(FluidTransferLog.FillFluid(s, filledStack, tank)), s - filledStack, newTank)
    } else {
      (Chain(FluidTransferLog.FillFailed(s, tank)), s, tank)
    }
  }

  def drainOp(tank: Tank): TankOperation = ReaderWriterStateT { case (_, s) =>
    if (s.amount === 0L) {
      // Nothing to drain.
      (Chain.empty, s, tank)
    } else if (s.fluid === Fluids.EMPTY || (s fluidEqual tank.fluidAmount)) {
      val drainAmount = tank.amount min s.amount
      val drainedStack = tank.fluidAmount.copy(amount = drainAmount)
      val newTank = tank.copy(tank.fluidAmount.copy(amount = tank.amount |-| drainAmount))
      (Chain(FluidTransferLog.DrainFluid(s, drainedStack, tank)), s - drainedStack, newTank)
    } else {
      (Chain(FluidTransferLog.DrainFailed(s, tank)), s, tank)
    }
  }

  def opList[F[+_]](tanks: F[Tank], action: Tank => TankOperation)(implicit monad: Monad[F], F: Foldable[F], monoid: Monoid[F[Tank]]): ListTankOperation[F] = {
    val opList = Monad[F].map(tanks)(action)
    val initialState: ListTankOperation[F] = ReaderWriterStateT((_, f) => Monad[Id].pure((Chain.empty, f, monoid.empty)))
    Foldable[F].foldLeft(opList, initialState) { (s, op) =>
      s.flatMap(filledTankList => op.map(t => filledTankList |+| Monad[F].pure(t)))
    }
  }

  def fillList[F[+_]](tanks: F[Tank])(implicit monad: Monad[F], F: Foldable[F], monoid: Monoid[F[Tank]]): ListTankOperation[F] =
    opList(tanks, fillOp)

  def drainList[F[+_]](tanks: F[Tank])(implicit monad: Monad[F], F: Foldable[F], monoid: Monoid[F[Tank]]): ListTankOperation[F] =
    opList(tanks, drainOp)

  final object EmptyTankHandler extends TankHandler {
    override def setTank(newTank: Tank): Unit = ()

    override def fill(resource: FluidStack, action: IFluidHandler.FluidAction): Int = 0

    override protected def drainInternal(toDrain: FluidAmount, action: IFluidHandler.FluidAction): FluidStack = FluidStack.EMPTY

    override protected def outputLog(logs: Chain[FluidTransferLog], action: IFluidHandler.FluidAction): Unit = ()
  }

}
