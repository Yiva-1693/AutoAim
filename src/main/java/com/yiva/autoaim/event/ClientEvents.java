package com.yiva.autoaim.event;

import com.yiva.autoaim.AutoAim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = AutoAim.MOD_ID, value = Dist.CLIENT)
public class ClientEvents {

    private static boolean isAiming = false;
    private static LivingEntity target = null;

    @SubscribeEvent
    public static void onMouseClick(InputEvent.MouseButton event) {
        int button = event.getButton();
        if (button != 1 && button != 4) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.isEmpty() || mainHand.getEnchantmentLevel(AutoAim.AUTO_AIM_ENCHANTMENT.get()) <= 0) {
            isAiming = false;
            target = null;
            return;
        }

        if (event.getAction() == 1) {
            isAiming = true;
            findAndLockTarget(mc);
            if (target != null) {
                aimAtTarget(mc, target);
            }
        } else if (event.getAction() == 0) {
            isAiming = false;
            target = null;
        }
    }

    // 使用 TickEvent，每 tick 更新一次（20Hz），虽然不如每帧平滑，但结合我们强制同步旧值，抖动应该不明显。
    // 如果希望更平滑，可以改为 RenderTickEvent，但这里为了兼容性，先用 TickEvent。
    @SubscribeEvent
    public static void onTick(TickEvent event) {
        // 只处理客户端 tick 结束
        if (event.type != TickEvent.Type.CLIENT) return;
        if (event.phase != TickEvent.Phase.END) return;
        if (!isAiming) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        if (target == null || !target.isAlive() || target.isRemoved()) {
            findAndLockTarget(mc);
            if (target == null) return;
        }

        aimAtTarget(mc, target);
    }

    private static void findAndLockTarget(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0F);

        double range = 50.0;
        AABB searchBox = player.getBoundingBox().inflate(range);
        List<Entity> entities = mc.level.getEntities(player, searchBox, e -> {
            if (!(e instanceof LivingEntity)) return false;
            if (e == player) return false;
            if (e.isSpectator()) return false;
            if (e.isInvisible()) return false;
            return true;
        });

        LivingEntity bestTarget = null;
        double bestDot = 0.9;

        for (Entity entity : entities) {
            LivingEntity living = (LivingEntity) entity;
            Vec3 targetPos = living.getEyePosition();
            Vec3 toTarget = targetPos.subtract(eyePos).normalize();
            double dot = lookVec.dot(toTarget);
            if (dot > bestDot) {
                bestDot = dot;
                bestTarget = living;
            }
        }

        target = bestTarget;
    }

    private static void aimAtTarget(Minecraft mc, LivingEntity target) {
        LocalPlayer player = mc.player;
        if (player == null) return;

        Vec3 eyePos = player.getEyePosition();
        Vec3 targetPos = target.getEyePosition();

        Vec3 direction = targetPos.subtract(eyePos).normalize();

        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        float yaw = (float) -Math.toDegrees(Math.atan2(direction.x, direction.z));
        float pitch = (float) Math.toDegrees(Math.atan2(-direction.y, horizontal));

        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.yHeadRot = yaw;
        player.yHeadRotO = yaw;
    }
}