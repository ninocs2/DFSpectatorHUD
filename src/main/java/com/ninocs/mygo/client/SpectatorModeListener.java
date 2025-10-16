package com.ninocs.mygo.client;

import com.ninocs.mygo.DFSpectatorUi;
import com.ninocs.mygo.client.hud.ImageOverlayRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
/**
 * 客户端 Tick 监听器：比较上一次与当前的玩家模式，实现"无事件"检测。
 */
@Mod.EventBusSubscriber(modid = DFSpectatorUi.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpectatorModeListener {
    private static GameType lastMode = null;
    private static Entity lastCamera = null;
    private static final SpectatorModeObserver observer = new SpectatorModeObserver();

    private SpectatorModeListener() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) {
            // 世界未就绪/未进入游戏
            lastMode = null;
            lastCamera = null;
            return;
        }

        // 当前游戏模式
        GameType current = mc.gameMode.getPlayerMode();

        // 检查游戏模式变化
        if (lastMode == null || current != lastMode) {
            // 直接调用观察者
            observer.onGameModeChange(lastMode, current);
            lastMode = current;
        }

        // 只在观察者模式下监听摄像头变化
        if (current == GameType.SPECTATOR) {
            Entity camera = mc.getCameraEntity();

            // 如果附身目标变化
            if (camera != lastCamera) {
                lastCamera = camera;

                if (camera instanceof Player targetPlayer) {
                    String targetName = targetPlayer.getName().getString();
                    // 通知ImageOverlayRenderer更新显示
                    ImageOverlayRenderer.notifyObservedPlayerChange(targetName);
                } else {
                    // 通知ImageOverlayRenderer切换到自由观察状态
                    ImageOverlayRenderer.notifyObservedPlayerChange(null);
                }
            }
        } else {
            lastCamera = null; // 离开观察者模式就清空状态
        }
    }
}