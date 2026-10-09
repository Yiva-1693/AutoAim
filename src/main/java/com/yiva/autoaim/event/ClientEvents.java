package com.yiva.autoaim.event;

import com.yiva.autoaim.AutoAim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 自瞄客户端逻辑。
 *
 * <p>视角接管方式（重要）：
 * Minecraft 一帧的执行顺序是 {@code MouseHandler.turnPlayer()}（把鼠标位移写进玩家 yaw/xRot）
 * → {@code GameRenderer.render()}（内部触发 {@code RenderTickEvent}）→ {@code Camera.setup()}。
 * 因此我们在 {@link TickEvent.RenderTickEvent} 里按帧写玩家朝向时，写入的值就是本帧最终值：
 * 既覆盖了本帧的鼠标输入，也会被相机与发往服务器的朝向包采用，做到“视角完全由 mod 接管”。</p>
 *
 * <p>触发方式：按住 {@link AutoAim#AIM_KEY}（默认右键）自瞄，松开立即停止并交还鼠标控制权。</p>
 */
@Mod.EventBusSubscriber(modid = AutoAim.MOD_ID, value = Dist.CLIENT)
public class ClientEvents {

    /** 搜索半径（格）。 */
    private static final double SEARCH_RANGE = 50.0D;
    /** 目标必须落在准心多少角度内才会被锁定（0.9 ≈ 25.8°）。 */
    private static final double AIM_CONE_DOT = 0.9D;

    /** 平滑系数（0~1，数值越大转向越快、越小越顺滑）。想更“粘”就调小，想更跟手就调大。 */
    private static final double AIM_SMOOTHING = 0.35D;
    /** 单帧最大转动角度（度），避免目标乱跳时视角瞬移。 */
    private static final double MAX_TURN_PER_FRAME = 25.0D;

    private static boolean aimEnabled = false;
    private static LivingEntity target = null;

    /** 上一次渲染出的朝向，用于按帧做“最短路径”插值。 */
    private static boolean hasLastAim = false;
    private static double lastAimYaw;
    private static double lastAimPitch;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        // 键位（键位设置中可改，默认右键）：按住自瞄，松开即停
        boolean keyDown = AutoAim.AIM_KEY.isDown();
        if (keyDown && !aimEnabled) {
            // 刚按下的这一 tick：清掉上一次的插值状态，从当前朝向平滑转过去
            clearTarget();
            hasLastAim = false;
        }
        aimEnabled = keyDown;

        if (!aimEnabled) {
            clearTarget();
            return;
        }

        // 手持物没有自瞄附魔、玩家死亡/旁观时关闭自瞄
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.isEmpty()
                || mainHand.getEnchantmentLevel(AutoAim.AUTO_AIM_ENCHANTMENT.get()) <= 0
                || !player.isAlive()
                || player.isSpectator()) {
            aimEnabled = false;
            clearTarget();
            hasLastAim = false;
            return;
        }

        // 目标死亡 / 被移除 / 已不可用：立即丢弃并重新索敌（不会去锁正在消散的尸体）
        if (target != null && !isValidTarget(player, target)) {
            clearTarget();
            hasLastAim = false;
        }
        if (target == null) {
            acquireTarget(mc, player);
            hasLastAim = false;
        }
    }

    /**
     * 每帧接管视角。RenderTickEvent 在鼠标位移写入玩家朝向之后触发，所以这里写的就是最终朝向。
     */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!aimEnabled) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        // 打开界面时交出控制权，避免和 GUI 抢视角
        if (player == null || mc.level == null || mc.screen != null || !player.isAlive()) return;
        if (target == null || !isValidTarget(player, target)) return;

        // 用“当前帧插值后”的位置算角度，目标移动时视角也是连续变化的
        float partialTick = event.renderTickTime;
        Vec3 eyePos = player.getEyePosition(partialTick);
        Vec3 targetPos = target.getEyePosition(partialTick);
        Vec3 direction = targetPos.subtract(eyePos);
        if (direction.lengthSqr() < 1.0E-6D) return;

        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        double desiredYaw = -Math.toDegrees(Math.atan2(direction.x, direction.z));
        double desiredPitch = -Math.toDegrees(Math.atan2(direction.y, horizontal));
        desiredPitch = Mth.clamp(desiredPitch, -90.0D, 90.0D);

        double baseYaw = hasLastAim ? lastAimYaw : player.getYRot();
        double basePitch = hasLastAim ? lastAimPitch : player.getXRot();

        // 按最短路径转向，帧率越高步进越小，所以帧率无关、视觉上连续
        double stepYaw = smoothStep(baseYaw, desiredYaw);
        double stepPitch = smoothStep(basePitch, desiredPitch);
        double newYaw = baseYaw + stepYaw;
        double newPitch = Mth.clamp(basePitch + stepPitch, -90.0D, 90.0D);

        lastAimYaw = newYaw;
        lastAimPitch = newPitch;
        hasLastAim = true;

        // 同步新旧朝向，避免原版在 tick 间再做一次插值而产生延迟/抖动
        player.setYRot((float) newYaw);
        player.yRotO = (float) newYaw;
        player.setXRot((float) newPitch);
        player.xRotO = (float) newPitch;
        player.yHeadRot = (float) newYaw;
        player.yHeadRotO = (float) newYaw;
    }

    /**
     * 生物死亡即刻解除锁定，避免继续瞄向还没被移除的尸体。
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (target != null && event.getEntity() == target) {
            clearTarget();
            hasLastAim = false;
        }
    }

    /** 取最短路径（考虑 360° 环绕）并限制单帧最大转动角度。 */
    private static double smoothStep(double from, double to) {
        double delta = Mth.wrapDegrees((float) (to - from));
        double limited = Math.max(-MAX_TURN_PER_FRAME, Math.min(MAX_TURN_PER_FRAME, delta));
        return limited * AIM_SMOOTHING;
    }

    /** 目标是否仍然可以作为自瞄对象。 */
    private static boolean isValidTarget(LocalPlayer player, LivingEntity entity) {
        if (entity == null || entity == player) return false;
        if (entity.isRemoved()) return false;
        if (!entity.isAlive() || entity.isDeadOrDying()) return false;
        return entity.getHealth() > 0.0F;
    }

    private static void clearTarget() {
        target = null;
    }

    private static void acquireTarget(Minecraft mc, LocalPlayer player) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0F);

        AABB searchBox = player.getBoundingBox().inflate(SEARCH_RANGE);
        List<Entity> entities = mc.level.getEntities(player, searchBox, e -> {
            if (!(e instanceof LivingEntity)) return false;
            if (e == player) return false;
            if (e.isSpectator()) return false;
            if (e.isInvisible()) return false;
            if (!e.isAlive() || e.isRemoved()) return false;
            return ((LivingEntity) e).getHealth() > 0.0F;
        });

        LivingEntity bestTarget = null;
        double bestDot = AIM_CONE_DOT;

        for (Entity entity : entities) {
            LivingEntity living = (LivingEntity) entity;
            Vec3 toTarget = living.getEyePosition().subtract(eyePos).normalize();
            double dot = lookVec.dot(toTarget);
            if (dot > bestDot) {
                bestDot = dot;
                bestTarget = living;
            }
        }

        target = bestTarget;
    }
}
