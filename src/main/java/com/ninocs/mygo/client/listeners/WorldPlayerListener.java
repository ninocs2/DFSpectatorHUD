package com.ninocs.mygo.client.listeners;

import com.mojang.logging.LogUtils;
import com.ninocs.mygo.DFSpectatorUi;
import com.ninocs.mygo.api.playerXtnInfoApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 世界玩家监听器
 * 监听进入世界和玩家加入事件，自动获取玩家扩展信息
 */
@Mod.EventBusSubscriber(modid = DFSpectatorUi.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WorldPlayerListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // 玩家状态管理器
    private static final PlayerStateManager playerStateManager = new PlayerStateManager();
    
    // 世界状态
    private static WorldState worldState = WorldState.NOT_IN_WORLD;
    
    // 初始加载延迟（毫秒）
    private static final int INITIAL_LOAD_DELAY = 1000;

    private WorldPlayerListener() {}

    /**
     * 世界状态枚举
     */
    private enum WorldState {
        NOT_IN_WORLD,           // 未在世界中
        ENTERING_WORLD,         // 正在进入世界
        INITIAL_LOADING,        // 初始加载中
        ACTIVE_MONITORING       // 活跃监听中
    }

    /**
     * 玩家状态管理器
     * 负责跟踪玩家状态和处理请求
     */
    private static class PlayerStateManager {
        private final Set<String> knownPlayers = ConcurrentHashMap.newKeySet();
        private final Set<String> requestedPlayers = ConcurrentHashMap.newKeySet();
        
        /**
         * 移除玩家
         */
        public void removePlayer(String playerName) {
            knownPlayers.remove(playerName);
            requestedPlayers.remove(playerName);
        }
        
        /**
         * 检查是否为新玩家
         */
        public boolean isNewPlayer(String playerName) {
            return !knownPlayers.contains(playerName);
        }
        
        /**
         * 标记玩家已请求
         */
        public void markAsRequested(String playerName) {
            requestedPlayers.add(playerName);
            knownPlayers.add(playerName);
        }
        
        /**
         * 检查玩家是否已请求
         */
        public boolean isRequested(String playerName) {
            return requestedPlayers.contains(playerName);
        }
        
        /**
         * 获取所有已知玩家
         */
        public Set<String> getKnownPlayers() {
            return Set.copyOf(knownPlayers);
        }
        
        /**
         * 清理所有状态
         */
        public void clear() {
            knownPlayers.clear();
            requestedPlayers.clear();
        }
        
        /**
         * 获取统计信息
         */
        public int getKnownPlayerCount() {
            return knownPlayers.size();
        }
        
        public int getRequestedPlayerCount() {
            return requestedPlayers.size();
        }
    }

    /**
     * 客户端Tick事件监听器
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        
        // 检查世界状态变化
        if (mc.player == null || mc.level == null) {
            handleWorldExit();
            return;
        }
        
        // 根据当前状态处理
        switch (worldState) {
            case NOT_IN_WORLD:
                handleWorldEntry();
                break;
            case ENTERING_WORLD:
                // 正在进入世界，等待异步初始化完成
                break;
            case INITIAL_LOADING:
                // 正在初始加载，等待加载完成
                break;
            case ACTIVE_MONITORING:
                monitorPlayerChanges();
                break;
        }
    }

    /**
     * 玩家加入世界事件监听器
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // 只处理客户端玩家实体
        if (!event.getLevel().isClientSide() || !(event.getEntity() instanceof Player player)) {
            return;
        }

        String playerName = player.getName().getString();
        
        // 排除本地玩家
        if (isLocalPlayer(playerName)) {
            return;
        }
        
        // 只在活跃监听状态下处理新玩家
        if (worldState == WorldState.ACTIVE_MONITORING) {
            handleNewPlayer(playerName);
        }
    }

    /**
     * 处理世界进入
     */
    private static void handleWorldEntry() {
        worldState = WorldState.ENTERING_WORLD;
        
        // 异步执行初始加载
        CompletableFuture.runAsync(() -> {
            try {
                // 等待世界完全加载
                Thread.sleep(INITIAL_LOAD_DELAY);
                performInitialLoad();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.error("[WorldPlayerListener] 初始加载被中断", e);
                worldState = WorldState.NOT_IN_WORLD;
            } catch (Exception e) {
                LOGGER.error("[WorldPlayerListener] 初始加载失败", e);
                worldState = WorldState.NOT_IN_WORLD;
            }
        });
    }

    /**
     * 执行初始加载
     */
    private static void performInitialLoad() {
        worldState = WorldState.INITIAL_LOADING;
        
        try {
            List<String> allPlayers = getCurrentPlayerList();
            
            if (!allPlayers.isEmpty()) {
                // 批量请求所有玩家信息
                requestPlayersInfo(allPlayers, true);
                
                // 标记所有玩家为已知
                allPlayers.forEach(playerStateManager::markAsRequested);
            }
            
            // 切换到活跃监听状态
            worldState = WorldState.ACTIVE_MONITORING;
            
        } catch (Exception e) {
            LOGGER.error("[WorldPlayerListener] 初始加载过程中发生错误", e);
            worldState = WorldState.NOT_IN_WORLD;
        }
    }

    /**
     * 处理世界退出
     */
    private static void handleWorldExit() {
        if (worldState != WorldState.NOT_IN_WORLD) {
            worldState = WorldState.NOT_IN_WORLD;
            playerStateManager.clear();
        }
    }

    /**
     * 监听玩家变化
     */
    private static void monitorPlayerChanges() {
        try {
            List<String> currentPlayers = getCurrentPlayerList();
            Set<String> currentPlayerSet = Set.copyOf(currentPlayers);
            Set<String> knownPlayers = playerStateManager.getKnownPlayers();
            
            // 找出新玩家 - 使用流式API优化
            Set<String> newPlayers = currentPlayerSet.stream()
                .filter(player -> !knownPlayers.contains(player))
                .collect(Collectors.toSet());
            
            // 找出离开的玩家
            Set<String> leftPlayers = knownPlayers.stream()
                .filter(player -> !currentPlayerSet.contains(player))
                .collect(Collectors.toSet());
            
            // 处理新玩家
            newPlayers.forEach(WorldPlayerListener::handleNewPlayer);
            
            // 清理离开的玩家
            leftPlayers.forEach(playerStateManager::removePlayer);
            
        } catch (Exception e) {
            LOGGER.error("[WorldPlayerListener] 监听玩家变化时发生错误", e);
        }
    }

    /**
     * 处理新玩家
     */
    private static void handleNewPlayer(String playerName) {
        if (playerStateManager.isNewPlayer(playerName) && !playerStateManager.isRequested(playerName)) {
            // 立即请求新玩家信息
            requestPlayersInfo(List.of(playerName), false);
            playerStateManager.markAsRequested(playerName);
        }
    }

    /**
     * 获取当前玩家列表
     */
    private static List<String> getCurrentPlayerList() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getConnection() == null) {
            return List.of();
        }

        try {
            // 从连接获取在线玩家信息 - 使用流式API优化
            Set<String> playerNames = mc.getConnection().getOnlinePlayers().stream()
                .map(playerInfo -> playerInfo.getProfile().getName())
                .filter(name -> !isLocalPlayer(name))
                .collect(Collectors.toSet());
            
            // 从世界实体获取玩家（补充检查）
            if (mc.level instanceof ClientLevel) {
                ClientLevel clientLevel = (ClientLevel) mc.level;
                clientLevel.players().stream()
                    .map(player -> player.getName().getString())
                    .filter(name -> !isLocalPlayer(name))
                    .forEach(playerNames::add);
            }
            
            return List.copyOf(playerNames);
            
        } catch (Exception e) {
            LOGGER.error("[WorldPlayerListener] 获取玩家列表时发生错误", e);
            return List.of();
        }
    }

    /**
     * 检查是否为本地玩家
     */
    private static boolean isLocalPlayer(String playerName) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && playerName.equals(mc.player.getName().getString());
    }

    /**
     * 请求玩家信息
     */
    private static void requestPlayersInfo(List<String> playerNames, boolean isBatchRequest) {
        if (playerNames.isEmpty()) {
            return;
        }

        try {
            var request = new playerXtnInfoApi.QueryUserXtnInfoRequest(playerNames);
            String requestType = isBatchRequest ? "批量" : "单个";

            playerXtnInfoApi.queryUserXtnInfoAsync(request)
                .thenAccept(response -> handlePlayerInfoResponse(response, playerNames, requestType))
                .exceptionally(throwable -> {
                    LOGGER.error("[WorldPlayerListener] {}请求玩家信息时发生异常: {}", 
                               requestType, playerNames, throwable);
                    return null;
                });

        } catch (Exception e) {
            LOGGER.error("[WorldPlayerListener] 创建玩家信息请求时发生错误: {}", playerNames, e);
        }
    }

    /**
     * 处理玩家信息响应
     */
    private static void handlePlayerInfoResponse(playerXtnInfoApi.QueryUserXtnInfoResponse response, 
                                               List<String> requestedPlayers, String requestType) {
        if (!response.isSuccess()) {
            LOGGER.error("[WorldPlayerListener] {}请求失败: {} - {} (玩家: {})", 
                       requestType, response.getCode(), response.getMessage(), requestedPlayers);
        }
    }

    // ========== 公共API方法 ==========

    /**
     * 手动刷新所有玩家信息
     */
    public static void refreshAllPlayers() {
        if (worldState == WorldState.ACTIVE_MONITORING) {
            playerStateManager.clear();
            performInitialLoad();
        } else {
            LOGGER.warn("[WorldPlayerListener] 当前不在活跃监听状态，无法刷新玩家信息");
        }
    }

    /**
     * 获取当前状态信息
     */
    public static String getStatusInfo() {
        return "世界状态: %s, 已知玩家: %d, 已请求玩家: %d".formatted(
                           worldState, 
                           playerStateManager.getKnownPlayerCount(),
                           playerStateManager.getRequestedPlayerCount());
    }

    /**
     * 检查玩家是否已被处理
     */
    public static boolean isPlayerProcessed(String playerName) {
        return playerStateManager.isRequested(playerName);
    }

    /**
     * 获取已处理的玩家数量
     */
    public static int getProcessedPlayerCount() {
        return playerStateManager.getRequestedPlayerCount();
    }

    /**
     * 检查是否在活跃监听状态
     */
    public static boolean isActivelyMonitoring() {
        return worldState == WorldState.ACTIVE_MONITORING;
    }
}