package com.yiva.autoaim;

import com.mojang.blaze3d.platform.InputConstants;
import com.yiva.autoaim.enchantment.AutoAimEnchantment;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

@Mod(AutoAim.MOD_ID)
public class AutoAim {
    public static final String MOD_ID = "autoaim";
    public static final Logger LOGGER = LogManager.getLogger();

    // 创建附魔的延迟注册器
    private static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, MOD_ID);

    // 注册“自瞄”附魔
    public static final RegistryObject<Enchantment> AUTO_AIM_ENCHANTMENT =
            ENCHANTMENTS.register("auto_aim", AutoAimEnchantment::new);

    /**
     * 自瞄按键，默认右键（GLFW_MOUSE_BUTTON_RIGHT）：按住自瞄、松开即停。
     * 注册后即可在“选项 → 控制 → 按键绑定”的“Auto Aim”分类里自行修改；
     * 想默认用键盘按键的话，把 {@code InputConstants.Type.MOUSE} 换成 {@code InputConstants.Type.KEYSYM}
     * 并把键码换成 {@code GLFW.GLFW_KEY_*} 即可。
     */
    public static final KeyMapping AIM_KEY = new KeyMapping(
            "key.autoaim.toggle",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_RIGHT),
            "key.categories.autoaim");

    public AutoAim() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        IEventBus forgeEventBus = MinecraftForge.EVENT_BUS;

        // 注册附魔
        ENCHANTMENTS.register(modEventBus);

        LOGGER.info("AutoAim Mod 初始化完成！");
    }

    /**
     * 按键注册必须走 mod 事件总线（RegisterKeyMappingsEvent 只在 mod 加载阶段派发）。
     */
    @Mod.EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ClientSetup {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(AIM_KEY);
            LOGGER.info("AutoAim 按键已注册：{}", AIM_KEY.getName());
        }
    }
}
