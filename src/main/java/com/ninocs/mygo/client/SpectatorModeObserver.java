package com.ninocs.mygo.client;

import com.mojang.logging.LogUtils;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;
import com.ninocs.mygo.map.GameModeChangeObserver;
import com.ninocs.mygo.client.hud.ImageOverlayRenderer;

/**
 * 当进入观察者模式时打印日志并提供切换钩子（客户端层面）。
 */
public final class SpectatorModeObserver implements GameModeChangeObserver {
    private static final Logger LOGGER = LogUtils.getLogger();

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
    }
}