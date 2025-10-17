package com.ninocs.mygo.client.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ninocs.mygo.api.playerXtnInfoApi;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 玩家数据存储工具类
 * 负责将玩家扩展信息保存为JSON文件到游戏缓存目录
 */
public class PlayerDataStorage {
    private static final Logger logger = Logger.getLogger(PlayerDataStorage.class.getName());
    
    // 缓存目录路径
    private static final String CACHE_DIR = "MCGO/cache/player_data";
    
    // Gson实例，用于JSON序列化
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    
    /**
     * 保存玩家数据到JSON文件
     * 
     * @param playerName 玩家名称，用作文件名
     * @param jsonData JSON数据字符串
     * @return 是否保存成功
     */
    public static boolean savePlayerData(String playerName, String jsonData) {
        if (playerName == null || playerName.trim().isEmpty()) {
            logger.log(Level.WARNING, "Player name is null or empty, cannot save data");
            return false;
        }
        
        if (jsonData == null || jsonData.trim().isEmpty()) {
            logger.log(Level.WARNING, "JSON data is null or empty for player: " + playerName);
            return false;
        }
        
        try {
            // 创建缓存目录
            Path cacheDir = Paths.get(CACHE_DIR);
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);

            }
            
            // 构建文件路径
            String fileName = sanitizeFileName(playerName) + ".json";
            Path filePath = cacheDir.resolve(fileName);
            
            // 格式化JSON数据
            String formattedJson = formatJson(jsonData);
            
            // 写入文件
            try (FileWriter writer = new FileWriter(filePath.toFile(), false)) {
                writer.write(formattedJson);
                writer.flush();
            }
            

            return true;
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save player data for: " + playerName, e);
            return false;
        }
    }
    
    /**
     * 保存玩家数据对象到JSON文件
     * 
     * @param playerName 玩家名称
     * @param dataObject 数据对象
     * @return 是否保存成功
     */
    public static boolean savePlayerData(String playerName, Object dataObject) {
        if (dataObject == null) {
            logger.log(Level.WARNING, "Data object is null for player: " + playerName);
            return false;
        }
        
        try {
            String jsonData = gson.toJson(dataObject);
            return savePlayerData(playerName, jsonData);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to serialize data object for player: " + playerName, e);
            return false;
        }
    }
    
    /**
     * 读取玩家数据JSON文件
     * 如果文件不存在或读取失败，则自动从API重新请求数据
     * 
     * @param playerName 玩家名称
     * @return JSON数据字符串，如果文件不存在或读取失败则返回null
     */
    public static String loadPlayerData(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return null;
        }
        
        try {
            String fileName = sanitizeFileName(playerName) + ".json";
            Path filePath = Paths.get(CACHE_DIR, fileName);
            
            if (!Files.exists(filePath)) {
                return requestPlayerDataFromApi(playerName);
            }
            
            return Files.readString(filePath);
            
        } catch (IOException e) {
            return requestPlayerDataFromApi(playerName);
        }
    }
    
    /**
     * 从API重新请求玩家数据
     * 注意：API调用成功后会自动保存数据到文件，此方法会重试获取保存的文件
     * 
     * @param playerName 玩家名称
     * @return JSON数据字符串，如果请求失败则返回null
     */
    private static String requestPlayerDataFromApi(String playerName) {
        final int MAX_RETRIES = 3;
        final int RETRY_DELAY_MS = 100;
        
        try {
            // 创建API请求
            playerXtnInfoApi.QueryUserXtnInfoRequest request = 
                new playerXtnInfoApi.QueryUserXtnInfoRequest(Collections.singletonList(playerName));
            
            // 调用API获取数据（API会自动保存数据到文件）
            playerXtnInfoApi.QueryUserXtnInfoResponse response = 
                playerXtnInfoApi.queryUserXtnInfo(request);
            
            if (response.isSuccess() && response.getData() != null) {
                playerXtnInfoApi.UserInfo userInfo = response.getUserInfo(playerName);
                if (userInfo != null) {
                    // API已经自动保存了数据，重试读取保存的文件
                    String fileName = sanitizeFileName(playerName) + ".json";
                    Path filePath = Paths.get(CACHE_DIR, fileName);
                    
                    for (int retry = 0; retry < MAX_RETRIES; retry++) {
                        try {
                            if (Files.exists(filePath)) {
                                return Files.readString(filePath);
                            }
                            // 如果文件不存在，等待一段时间后重试
                            if (retry < MAX_RETRIES - 1) {
                                Thread.sleep(RETRY_DELAY_MS);
                            }
                        } catch (IOException e) {
                            if (retry == MAX_RETRIES - 1) {
                                // 最后一次重试失败，使用手动转换
                                return gson.toJson(userInfo);
                            }
                            Thread.sleep(RETRY_DELAY_MS);
                        }
                    }
                    
                    // 所有重试都失败，使用手动转换作为备用方案
                    return gson.toJson(userInfo);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to request player data from API for: " + playerName, e);
        }
        
        return null;
    }
    
    /**
     * 检查玩家数据文件是否存在
     * 
     * @param playerName 玩家名称
     * @return 文件是否存在
     */
    public static boolean playerDataExists(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return false;
        }
        
        String fileName = sanitizeFileName(playerName) + ".json";
        Path filePath = Paths.get(CACHE_DIR, fileName);
        return Files.exists(filePath);
    }
    
    /**
     * 删除玩家数据文件
     * 
     * @param playerName 玩家名称
     * @return 是否删除成功
     */
    public static boolean deletePlayerData(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            logger.log(Level.WARNING, "Player name is null or empty, cannot delete data");
            return false;
        }
        
        try {
            String fileName = sanitizeFileName(playerName) + ".json";
            Path filePath = Paths.get(CACHE_DIR, fileName);
            
            if (!Files.exists(filePath)) {

                return true;
            }
            
            Files.delete(filePath);

            return true;
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to delete player data for: " + playerName, e);
            return false;
        }
    }
    
    /**
     * 获取缓存目录路径
     * 
     * @return 缓存目录的绝对路径
     */
    public static String getCacheDirectory() {
        try {
            Path cacheDir = Paths.get(CACHE_DIR);
            return cacheDir.toAbsolutePath().toString();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to get cache directory path", e);
            return CACHE_DIR;
        }
    }
    
    /**
     * 清理文件名，移除不安全的字符
     * 
     * @param fileName 原始文件名
     * @return 清理后的安全文件名
     */
    private static String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "unknown";
        }
        
        // 移除或替换不安全的字符
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_")
                      .replaceAll("\\s+", "_")
                      .trim();
    }
    
    /**
     * 格式化JSON字符串
     * 
     * @param jsonData 原始JSON字符串
     * @return 格式化后的JSON字符串
     */
    private static String formatJson(String jsonData) {
        try {
            JsonObject jsonObject = JsonParser.parseString(jsonData).getAsJsonObject();
            return gson.toJson(jsonObject);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to format JSON, using original data", e);
            return jsonData;
        }
    }
}