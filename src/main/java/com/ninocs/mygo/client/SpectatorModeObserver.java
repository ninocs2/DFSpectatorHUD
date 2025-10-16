package com.ninocs.mygo.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import com.ninocs.mygo.map.GameModeChangeObserver;
import com.ninocs.mygo.client.hud.ImageOverlayRenderer;

/**
 * 当进入观察者模式时打印日志并提供切换钩子（客户端层面）。
 */
public final class SpectatorModeObserver implements GameModeChangeObserver {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Entity lastCamera = null;

    @Override
    public void onGameModeChange(GameType previous, GameType current) {
        LOGGER.info("[DFSpectatorUi] 玩家客户端模式变化: {} -> {}", previous, current);

        if (current == GameType.SPECTATOR) {
            onEnterSpectator(previous);
        } else if (previous == GameType.SPECTATOR) {
            onExitSpectator(current);
        }
    }

    private void onEnterSpectator(GameType previous) {
        // 在观察者模式下的处理：启用HUD显示
        LOGGER.info("[DFSpectatorUi] 玩家进入观察者模式。启用HUD显示。");
        
        // 启用图片HUD显示
        ImageOverlayRenderer.enableHud();
    }

    private void onExitSpectator(GameType current) {
        LOGGER.info("[DFSpectatorUi] 玩家退出观察者模式，当前模式: {}", current);
        
        // 禁用图片HUD显示
        ImageOverlayRenderer.disableHud();
        
        // 清空摄像头状态
        lastCamera = null;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return;

        // 当前游戏模式
        GameType mode = mc.gameMode.getPlayerMode();

        // 只在观察者模式下监听
        if (mode == GameType.SPECTATOR) {
            Entity camera = mc.getCameraEntity();

            // 如果附身目标变化
            if (camera != lastCamera) {
                lastCamera = camera;

                if (camera instanceof Player targetPlayer) {
                    String targetName = targetPlayer.getName().getString();
                    System.out.println("👁️ 现在正在观察玩家：" + targetName);
                } else {
                    System.out.println("👁️ 当前没有观察任何玩家（自由观察状态）");
                }
            }
        } else {
            lastCamera = null; // 离开观察者模式就清空状态
        }
    }
}