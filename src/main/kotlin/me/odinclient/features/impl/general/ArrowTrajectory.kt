package me.odinclient.features.impl.general

import me.odinclient.OdinClient.Companion.config
import me.odinclient.OdinClient.Companion.mc
import me.odinclient.events.RenderEntityModelEvent
import me.odinclient.features.Category
import me.odinclient.features.Module
import me.odinclient.utils.render.world.OutlineUtils
import me.odinclient.utils.render.world.RenderUtils
import me.odinclient.utils.skyblock.ItemUtils.itemID
import net.minecraft.entity.Entity
import net.minecraft.entity.item.EntityArmorStand
import net.minecraft.entity.projectile.EntityArrow
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.Vec3
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import javax.vecmath.Vector2d
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object ArrowTrajectory : Module(
    "Arrow Trajectory",
    category = Category.GENERAL
) {
    private var boxRenderQueue: MutableList<Pair<Vec3, Vector2d>> = mutableListOf()
    private var entityRenderQueue = mutableListOf<Entity>()

    @SubscribeEvent
    fun onRenderWorldLast(event: RenderWorldLastEvent) {
        entityRenderQueue.clear()
        if (mc.thePlayer?.heldItem?.itemID != "TERMINATOR") return
        setTrajectoryHeading(-5f, 0f)
        setTrajectoryHeading(0f, -0.1f)
        setTrajectoryHeading(5f, 0f)
        drawCollisionBoxes()
    }

    private fun setTrajectoryHeading(yawOffset: Float, yOffset: Float) {
        val yawRadians = ((mc.thePlayer.rotationYaw + yawOffset) / 180) * Math.PI
        val pitchRadians = (mc.thePlayer.rotationPitch / 180) * Math.PI

        val posX = RenderUtils.playerRenderX
        val posY = RenderUtils.playerRenderY + mc.thePlayer.eyeHeight + yOffset
        val posZ = RenderUtils.playerRenderZ

        var motionX = -sin(yawRadians) * cos(pitchRadians)
        var motionY = -sin(pitchRadians)
        var motionZ = cos(yawRadians) * cos(pitchRadians)
        val lengthOffset = sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ)

        motionX = motionX / lengthOffset * 3
        motionY = motionY / lengthOffset * 3
        motionZ = motionZ / lengthOffset * 3

        calculateTrajectory(Vec3(motionX, motionY, motionZ), Vec3(posX, posY, posZ))
    }

    private fun calculateTrajectory(mV: Vec3, pV: Vec3) {
        var hitResult = false
        var motionVec = mV
        var posVec = pV
        for (i in 0..60) {
            if (hitResult) break
            val vec = motionVec.add(posVec)
            val rayTrace = mc.theWorld.rayTraceBlocks(posVec, vec, false, true, false)
            val aabb = AxisAlignedBB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
                    .offset(posVec.xCoord, posVec.yCoord, posVec.zCoord)
                    .addCoord(motionVec.xCoord, motionVec.yCoord, motionVec.zCoord)
                    .expand(0.01, 0.01, 0.01)
            val entityHit = mc.theWorld.getEntitiesWithinAABBExcludingEntity(mc.thePlayer, aabb).filter { it !is EntityArrow && it !is EntityArmorStand}
            if (entityHit.isNotEmpty()) {
                hitResult = true
                entityRenderQueue.addAll(entityHit)
            } else if (rayTrace != null) {
                boxRenderQueue.add(
                    Pair(
                        rayTrace.hitVec.addVector(-0.15 * config.arrowTrajectoryBoxSize, 0.0, -0.15 * config.arrowTrajectoryBoxSize),
                        Vector2d(0.3 * config.arrowTrajectoryBoxSize, 0.3 * config.arrowTrajectoryBoxSize)
                    )
                )
                hitResult = true
            }
            posVec = posVec.add(motionVec)
            motionVec = Vec3(motionVec.xCoord * 0.99, motionVec.yCoord * 0.99 - 0.05, motionVec.zCoord * 0.99)
        }
    }

    private fun drawCollisionBoxes() {
        if (boxRenderQueue.size == 0) return
        for (b in boxRenderQueue) {
            if (
                hypot(
                    RenderUtils.playerRenderX - b.first.xCoord,
                    RenderUtils.playerRenderY + mc.thePlayer.eyeHeight - b.first.yCoord,
                    RenderUtils.playerRenderZ - b.first.zCoord
                ) < 2
            ) {
                boxRenderQueue.clear()
                return
            }
            RenderUtils.drawCustomEspBox(
                b.first.xCoord, b.second.x,
                b.first.yCoord, b.second.y,
                b.first.zCoord, b.second.x,
                config.arrowTrajectoryColor.toJavaColor(),
                config.arrowTrajectoryThickness / 3,
                phase = true
            )
        }
        boxRenderQueue.clear()
    }

    @SubscribeEvent
    fun onRenderModel(event: RenderEntityModelEvent) {
        if (!entityRenderQueue.contains(event.entity)) return
        if (!mc.thePlayer.canEntityBeSeen(event.entity)) return
        OutlineUtils.outlineEntity(
            event,
            config.arrowTrajectoryThickness,
            config.arrowTrajectoryColor.toJavaColor(),
            false
        )
    }

    private fun hypot(x: Double, y: Double, d: Double): Double = sqrt(x * x + y * y + d * d)
}