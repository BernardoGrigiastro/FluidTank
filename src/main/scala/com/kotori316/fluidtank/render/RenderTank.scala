package com.kotori316.fluidtank.render

import com.kotori316.fluidtank.tiles.TileTank
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.blockentity.{BlockEntityRenderer, BlockEntityRendererProvider}
import net.minecraft.client.renderer.{MultiBufferSource, RenderType}
import net.minecraft.core.BlockPos
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.level.Level
import net.minecraftforge.api.distmarker.{Dist, OnlyIn}
import net.minecraftforge.fluids.FluidAttributes

@OnlyIn(Dist.CLIENT)
class RenderTank(d: BlockEntityRendererProvider.Context) extends BlockEntityRenderer[TileTank] {

  override def render(te: TileTank, partialTicks: Float, matrix: PoseStack, buffer: MultiBufferSource, light: Int, otherLight: Int): Unit = {
    Minecraft.getInstance.getProfiler.push("RenderTank")
    if (te.hasContent) {
      matrix.pushPose()
      val b = buffer.getBuffer(RenderType.translucent)
      val tank = te.internalTank
      if (tank.box != null) {
        val resource = RenderTank.textureName(te)
        val texture = Minecraft.getInstance.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(resource)
        val color = RenderTank.color(te)

        val value = Box.LightValue(light).overrideBlock(te.internalTank.getFluid.fluid.getAttributes.getLuminosity(te.internalTank.getFluid.toStack))
        val alpha = if ((color >> 24 & 0xFF) > 0) color >> 24 & 0xFF else 0xFF
        tank.box.render(b, matrix, texture, alpha, color >> 16 & 0xFF, color >> 8 & 0xFF, color >> 0 & 0xFF)(value)
      }
      matrix.popPose()
    }
    Minecraft.getInstance.getProfiler.pop()
  }
}

object RenderTank {
  private def textureName(tile: TileTank) = {
    val world = getTankWorld(tile)
    val pos = getTankPos(tile)
    tile.internalTank.getFluid.fluid.getAttributes.getStillTexture(world, pos)
  }

  private def color(tile: TileTank) = {
    val fluidAmount = tile.internalTank.getFluid
    val attributes = fluidAmount.fluid.getAttributes
    val normal = attributes.getColor
    if (attributes.getClass == classOf[FluidAttributes]) {
      normal
    } else {
      val stackColor = attributes.getColor(fluidAmount.toStack)
      if (normal == stackColor) {
        val world = getTankWorld(tile)
        val pos = getTankPos(tile)
        val worldColor = attributes.getColor(world, pos)
        worldColor
      } else {
        stackColor
      }
    }
  }

  private final def getTankWorld(tileTank: TileTank): Level = {
    if (tileTank.hasLevel) tileTank.getLevel else Minecraft.getInstance.level
  }

  private final def getTankPos(tileTank: TileTank): BlockPos = {
    if (tileTank.hasLevel) tileTank.getBlockPos else Minecraft.getInstance.player.getOnPos
  }

}
