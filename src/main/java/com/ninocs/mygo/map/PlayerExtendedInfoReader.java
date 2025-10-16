package com.ninocs.mygo.map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ninocs.mygo.client.data.PlayerDataStorage;
import com.ninocs.mygo.client.data.PlayerExtendedInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;

/**
 * 玩家扩展信息读取器
 * 负责从存储的JSON文件中读取玩家的扩展信息
 */
public class PlayerExtendedInfoReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerExtendedInfoReader.class);
    private static final boolean DEBUG_LOGGING = false; // 调试日志开关

    /**
     * 根据玩家名读取玩家扩展信息
     * 
     * @param playerName 玩家名
     * @return 玩家扩展信息，如果读取失败返回null
     */
    public static PlayerExtendedInfo readPlayerExtendedInfo(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            if (DEBUG_LOGGING) {
                LOGGER.info("玩家名为空，无法读取扩展信息");
            }
            return null;
        }

        try {
            String playerData = PlayerDataStorage.loadPlayerData(playerName);
            if (playerData == null || playerData.trim().isEmpty()) {
                if (DEBUG_LOGGING) {
                    LOGGER.info("玩家 {} 没有存储的数据", playerName);
                }
                return null;
            }

            return parsePlayerExtendedInfo(playerName, playerData);
        } catch (Exception e) {
            LOGGER.error("读取玩家 {} 扩展信息时发生错误: {}", playerName, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 根据玩家UUID读取玩家扩展信息
     * 
     * @param playerUUID 玩家UUID
     * @return 玩家扩展信息，如果读取失败返回null
     */
    public static PlayerExtendedInfo readPlayerExtendedInfoByUUID(String playerUUID) {
        if (playerUUID == null || playerUUID.trim().isEmpty()) {
            if (DEBUG_LOGGING) {
                LOGGER.info("玩家UUID为空，无法读取扩展信息");
            }
            return null;
        }

        try {
            // 遍历所有玩家数据文件，查找匹配的UUID
            File dataDir = new File("MCGO/data");
            if (!dataDir.exists() || !dataDir.isDirectory()) {
                if (DEBUG_LOGGING) {
                    LOGGER.info("数据目录不存在: {}", dataDir.getAbsolutePath());
                }
                return null;
            }

            File[] playerFiles = dataDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (playerFiles == null) {
                if (DEBUG_LOGGING) {
                    LOGGER.info("数据目录中没有找到玩家数据文件");
                }
                return null;
            }

            for (File playerFile : playerFiles) {
                try (FileReader reader = new FileReader(playerFile)) {
                    JsonElement element = JsonParser.parseReader(reader);
                    if (element.isJsonObject()) {
                        JsonObject jsonObject = element.getAsJsonObject();
                        if (jsonObject.has("uuid")) {
                            String fileUUID = jsonObject.get("uuid").getAsString();
                            if (playerUUID.equals(fileUUID)) {
                                String playerName = playerFile.getName().replace(".json", "");
                                return parsePlayerExtendedInfo(playerName, jsonObject.toString());
                            }
                        }
                    }
                } catch (Exception e) {
                    if (DEBUG_LOGGING) {
                        LOGGER.info("读取文件 {} 时发生错误: {}", playerFile.getName(), e.getMessage());
                    }
                }
            }

            if (DEBUG_LOGGING) {
                LOGGER.info("未找到UUID为 {} 的玩家数据", playerUUID);
            }
            return null;
        } catch (Exception e) {
            LOGGER.error("根据UUID {} 读取玩家扩展信息时发生错误: {}", playerUUID, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 解析JSON字符串为玩家扩展信息对象
     * 
     * @param playerName 玩家名
     * @param jsonData JSON数据字符串
     * @return 解析后的玩家扩展信息
     */
    private static PlayerExtendedInfo parsePlayerExtendedInfo(String playerName, String jsonData) {
        try {
            JsonElement element = JsonParser.parseString(jsonData);
            if (!element.isJsonObject()) {
                if (DEBUG_LOGGING) {
                    LOGGER.info("玩家 {} 的数据不是有效的JSON对象", playerName);
                }
                return null;
            }

            JsonObject jsonObject = element.getAsJsonObject();
            PlayerExtendedInfo info = new PlayerExtendedInfo();

            // 设置基本信息
            info.setPlayerName(playerName);
            if (jsonObject.has("playerUUID")) {
                info.setPlayerUUID(jsonObject.get("playerUUID").getAsString());
            } else if (jsonObject.has("uuid")) {
                info.setPlayerUUID(jsonObject.get("uuid").getAsString());
            }

            // 从根级别读取基本信息
            if (jsonObject.has("userNm") && !jsonObject.get("userNm").isJsonNull()) {
                info.setUserNm(jsonObject.get("userNm").getAsString());
            }
            if (jsonObject.has("avatar") && !jsonObject.get("avatar").isJsonNull()) {
                info.setAvatar(jsonObject.get("avatar").getAsString());
            }
            if (jsonObject.has("loginIdNbr") && !jsonObject.get("loginIdNbr").isJsonNull()) {
                info.setLoginIdNbr(jsonObject.get("loginIdNbr").getAsString());
            }

            // 检查是否有xtnInfo对象
            JsonObject xtnInfo = null;
            if (jsonObject.has("xtnInfo") && jsonObject.get("xtnInfo").isJsonObject()) {
                xtnInfo = jsonObject.getAsJsonObject("xtnInfo");
            }

            // 从xtnInfo中读取扩展信息
            if (xtnInfo != null) {
                if (xtnInfo.has("userCard") && !xtnInfo.get("userCard").isJsonNull()) {
                    info.setUserCard(xtnInfo.get("userCard").getAsString());
                }
                if (xtnInfo.has("userBanner") && !xtnInfo.get("userBanner").isJsonNull()) {
                    info.setUserBanner(xtnInfo.get("userBanner").getAsString());
                }
                if (xtnInfo.has("mvpMusicUrl") && !xtnInfo.get("mvpMusicUrl").isJsonNull()) {
                    info.setMvpMusicUrl(xtnInfo.get("mvpMusicUrl").getAsString());
                }
                if (xtnInfo.has("mvpVideosUrl") && !xtnInfo.get("mvpVideosUrl").isJsonNull()) {
                    info.setMvpVideosUrl(xtnInfo.get("mvpVideosUrl").getAsString());
                }
                if (xtnInfo.has("userSpaceContexts") && !xtnInfo.get("userSpaceContexts").isJsonNull()) {
                    info.setUserSpaceContexts(xtnInfo.get("userSpaceContexts").getAsString());
                }
                if (xtnInfo.has("mvpMusicNm") && !xtnInfo.get("mvpMusicNm").isJsonNull()) {
                    info.setMvpMusicNm(xtnInfo.get("mvpMusicNm").getAsString());
                }
            }

            if (DEBUG_LOGGING) {
                LOGGER.info("成功解析玩家 {} 的扩展信息: {}", playerName, info.toString());
            }

            return info;
        } catch (Exception e) {
            LOGGER.error("解析玩家 {} 的JSON数据时发生错误: {}", playerName, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 检查玩家是否有扩展信息数据
     * 
     * @param playerName 玩家名
     * @return 如果有数据返回true，否则返回false
     */
    public static boolean hasPlayerExtendedInfo(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return false;
        }

        try {
            String playerData = PlayerDataStorage.loadPlayerData(playerName);
            return playerData != null && !playerData.trim().isEmpty();
        } catch (Exception e) {
            if (DEBUG_LOGGING) {
                LOGGER.info("检查玩家 {} 扩展信息时发生错误: {}", playerName, e.getMessage());
            }
            return false;
        }
    }

    /**
     * 获取玩家的显示名称（优先使用userNm，如果没有则使用playerName）
     * 
     * @param playerName 玩家名
     * @return 显示名称
     */
    public static String getPlayerDisplayName(String playerName) {
        PlayerExtendedInfo info = readPlayerExtendedInfo(playerName);
        if (info != null && info.getUserNm() != null && !info.getUserNm().trim().isEmpty()) {
            return info.getUserNm();
        }
        return playerName;
    }

    /**
     * 获取玩家头像URL
     * 
     * @param playerName 玩家名
     * @return 头像URL，如果没有返回null
     */
    public static String getPlayerAvatarUrl(String playerName) {
        PlayerExtendedInfo info = readPlayerExtendedInfo(playerName);
        return info != null ? info.getAvatar() : null;
    }

    /**
     * 获取玩家卡片URL
     * 
     * @param playerName 玩家名
     * @return 卡片URL，如果没有返回null
     */
    public static String getPlayerCardUrl(String playerName) {
        PlayerExtendedInfo info = readPlayerExtendedInfo(playerName);
        return info != null ? info.getUserCard() : null;
    }

    /**
     * 获取玩家横幅URL
     * 
     * @param playerName 玩家名
     * @return 横幅URL，如果没有返回null
     */
    public static String getPlayerBannerUrl(String playerName) {
        PlayerExtendedInfo info = readPlayerExtendedInfo(playerName);
        return info != null ? info.getUserBanner() : null;
    }
}