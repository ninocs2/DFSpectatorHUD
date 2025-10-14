package com.ninocs.mygo;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

// 这个值应该与META-INF/mods.toml文件中的条目匹配
@Mod(DFSpectatorUi.MODID)
public class DFSpectatorUi {

    // 在一个通用位置定义mod id，供所有内容引用
    public static final String MODID = "dfspectatorui";
    // 直接引用slf4j日志记录器
    private static final Logger LOGGER = LogUtils.getLogger();

    public DFSpectatorUi() {

        // 注册我们mod的ForgeConfigSpec，以便Forge可以为我们创建和加载配置文件
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

    }

    // 你可以使用SubscribeEvent并让事件总线发现要调用的方法
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // 在服务器启动时做一些事情
    }

    // 你可以使用EventBusSubscriber自动注册类中所有用@SubscribeEvent注解的静态方法
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // 一些客户端设置代码
        }
    }
}
