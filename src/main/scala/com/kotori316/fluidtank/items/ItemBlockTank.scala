package com.kotori316.fluidtank.items

import java.util.function.Consumer

import com.kotori316.fluidtank.FluidTank
import com.kotori316.fluidtank.blocks.BlockTank
import com.kotori316.fluidtank.fluids.FluidAmount
import com.kotori316.fluidtank.integration.Localize
import com.kotori316.fluidtank.network.ClientProxy
import com.kotori316.fluidtank.tiles.TileTank
import javax.annotation.Nullable
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.{Component, TranslatableComponent}
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.{BlockItem, ItemStack, Rarity, TooltipFlag}
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraftforge.api.distmarker.{Dist, OnlyIn}
import net.minecraftforge.client.IItemRenderProperties
import net.minecraftforge.common.capabilities.ICapabilityProvider
import net.minecraftforge.fluids.FluidUtil
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction

class ItemBlockTank(val blockTank: BlockTank) extends BlockItem(blockTank, FluidTank.proxy.getTankProperties) {
  setRegistryName(FluidTank.modID, blockTank.namePrefix + blockTank.tier.toString.toLowerCase)

  override def getRarity(stack: ItemStack): Rarity =
    if (stack.hasTag && stack.getTag.contains(TileTank.NBT_BlockTag)) Rarity.RARE
    else Rarity.COMMON

  def hasInvisibleRecipe = true

  @OnlyIn(Dist.CLIENT)
  override def appendHoverText(stack: ItemStack, @Nullable level: Level, tooltip: java.util.List[Component], flag: TooltipFlag): Unit = {
    val nbt = stack.getTagElement(TileTank.NBT_BlockTag)
    if (nbt != null) {
      val tankNBT = nbt.getCompound(TileTank.NBT_Tank)
      val fluid = FluidAmount.fromNBT(tankNBT)
      val c = tankNBT.getInt(TileTank.NBT_Capacity)
      tooltip.add(new TranslatableComponent(Localize.TOOLTIP, fluid.toStack.getDisplayName, fluid.amount, c))
    } else {
      tooltip.add(new TranslatableComponent(Localize.CAPACITY, blockTank.tier.amount))
    }
  }

  override def initCapabilities(stack: ItemStack, nbt: CompoundTag): ICapabilityProvider = {
    new TankItemFluidHandler(blockTank.tier, stack)
  }

  override def updateCustomBlockEntityTag(pos: BlockPos, level: Level, @Nullable player: Player, stack: ItemStack, state: BlockState): Boolean = {
    if (level.getServer != null) {
      val tileentity = level.getBlockEntity(pos)
      if (tileentity != null) {
        val subTag = stack.getTagElement(TileTank.NBT_BlockTag)
        if (subTag != null) {
          if (!(!level.isClientSide && tileentity.onlyOpCanSetNbt) || !(player == null || !player.canUseGameMasterBlocks)) {
            val nbt = tileentity.save(new CompoundTag)
            nbt.merge(subTag)
            nbt.putInt("x", pos.getX)
            nbt.putInt("y", pos.getY)
            nbt.putInt("z", pos.getZ)
            tileentity.load(nbt)
            tileentity.setChanged()
          }
        }
        if (stack.hasCustomHoverName) {
          tileentity match {
            case tank: TileTank => tank.stackName = stack.getDisplayName
            case _ =>
          }
        }
        return true
      }
    }
    false
  }

  override def place(context: BlockPlaceContext): InteractionResult = {
    if (Option(context.getPlayer).exists(_.isCreative)) {
      val size = context.getItemInHand.getCount
      val result = super.place(context)
      context.getItemInHand.setCount(size)
      result
    } else {
      super.place(context)
    }
  }

  override def getContainerItem(itemStack: ItemStack): ItemStack = {
    import com.kotori316.fluidtank._
    FluidUtil.getFluidHandler(itemStack.copy()).asScala
      .map { f => f.drain(FluidAmount.AMOUNT_BUCKET, FluidAction.EXECUTE); f.getContainer }
      .getOrElse(itemStack)
      .value
  }

  override def hasContainerItem(stack: ItemStack): Boolean = stack.getTagElement(TileTank.NBT_BlockTag) != null

  override def initializeClient(consumer: Consumer[IItemRenderProperties]): Unit = {
    consumer.accept(new IItemRenderProperties {
      override def getItemStackRenderer: BlockEntityWithoutLevelRenderer = ClientProxy.RENDER_ITEM_TANK.value
    })
  }
}
