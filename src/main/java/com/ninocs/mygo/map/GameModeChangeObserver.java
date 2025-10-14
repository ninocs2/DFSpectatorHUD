package com.ninocs.mygo.map;

import net.minecraft.world.level.GameType;

/**
 * 观察者接口：当玩家客户端游戏模式发生变化时触发。
 */
public interface GameModeChangeObserver {
    /**
     * 当模式变化时调用。
     *
     * @param previous 上一次模式，可能为 null（首次获取或世界尚未就绪）
     * @param current  当前模式，非 null
     */
    void onGameModeChange(GameType previous, GameType current);
}