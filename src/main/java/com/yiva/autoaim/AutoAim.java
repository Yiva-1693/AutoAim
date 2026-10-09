package com.yiva.autoaim;

import com.yiva.autoaim.enchantment.AutoAimEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

    public AutoAim() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        IEventBus forgeEventBus = MinecraftForge.EVENT_BUS;

        // 注册附魔
        ENCHANTMENTS.register(modEventBus);

        // 注册客户端事件监听（我们将在下一步创建）
        // 注意：这里不要直接注册普通事件，因为我们只需要客户端逻辑。
        // 更好的做法是使用 @Mod.EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT) 注解在专门的类上。
        // 我们将在下一步创建该类。

        LOGGER.info("AutoAim Mod 初始化完成！");
    }
}