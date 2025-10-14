package com.ninocs.mygo.client;

import com.mojang.logging.LogUtils;
import com.ninocs.mygo.DFSpectatorUi;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
/**
 * 客户端 Tick 监听器：比较上一次与当前的玩家模式，实现"无事件"检测。
 */
@Mod.EventBusSubscriber(modid = DFSpectatorUi.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpectatorModeListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static GameType lastMode = null;
    private static final SpectatorModeObserver observer = new SpectatorModeObserver();

    private SpectatorModeListener() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            // 世界未就绪/未进入游戏
            lastMode = null;
            return;
        }

        GameType current = getCurrentGameType(mc);
        if (current == null) {
            return;
        }

        if (lastMode == null || current != lastMode) {
            // 直接调用观察者
            observer.onGameModeChange(lastMode, current);
            lastMode = current;
        }
    }

    private static GameType getCurrentGameType(Minecraft mc) {
        try {
            // 优先使用客户端的 MultiPlayerGameMode 信息
            if (mc.gameMode != null) {
                return mc.gameMode.getPlayerMode();
            }
        } catch (Throwable t) {
            // 兼容不同映射的潜在差异
            LOGGER.debug("获取客户端 gameMode.getPlayerMode() 失败，尝试后备方案", t);
        }

        // 后备：仅能检测是否观察者，其余模式不可区分
        // 仍返回对应 GameType（若为观察者），否则返回 null 表示未知
        try {
            if (mc.player != null && mc.player.isSpectator()) {
                return GameType.SPECTATOR;
            }
        } catch (Throwable ignored) {}

        return null;
    }
}