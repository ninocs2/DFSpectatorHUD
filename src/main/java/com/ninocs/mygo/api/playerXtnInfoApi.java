package com.ninocs.mygo.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.logging.Level;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.ninocs.mygo.util.PlayerDataStorage;
import com.ninocs.mygo.downloads.ImageDownloader;

public class playerXtnInfoApi {
    private static final String API_URL = "https://api.mcgo.ninocs.com:24264/user/space/queryUserXtnInfo";
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final Gson gson = new GsonBuilder().create();
    private static final Logger logger = Logger.getLogger(playerXtnInfoApi.class.getName());

    // 请求参数类
    public static class QueryUserXtnInfoRequest {
        private List<String> playerIds;  // 游戏ID列表（支持多个）

        public QueryUserXtnInfoRequest() {}

        public QueryUserXtnInfoRequest(List<String> playerIds) {
            this.playerIds = playerIds;
        }

        // Getter和Setter方法
        public List<String> getPlayerIds() {
            return playerIds;
        }

        public void setPlayerIds(List<String> playerIds) {
            this.playerIds = playerIds;
        }
    }

    // 扩展信息数据类
    public static class XtnInfo {
        private String userCard;        // 用户卡片URL
        private String userBanner;      // 用户横幅URL
        private String mvpMusicUrl;     // MVP音乐URL
        private String mvpVideosUrl;    // MVP视频URL
        private String userSpaceContexts; // 用户空间内容URL
        private String mvpMusicNm;      // MVP音乐名称

        public XtnInfo() {}

        public String getUserCard() {
            return userCard;
        }

        public void setUserCard(String userCard) {
            this.userCard = userCard;
        }

        public String getUserBanner() {
            return userBanner;
        }

        public void setUserBanner(String userBanner) {
            this.userBanner = userBanner;
        }

        public String getMvpMusicUrl() {
            return mvpMusicUrl;
        }

        public void setMvpMusicUrl(String mvpMusicUrl) {
            this.mvpMusicUrl = mvpMusicUrl;
        }

        public String getMvpVideosUrl() {
            return mvpVideosUrl;
        }

        public void setMvpVideosUrl(String mvpVideosUrl) {
            this.mvpVideosUrl = mvpVideosUrl;
        }

        public String getUserSpaceContexts() {
            return userSpaceContexts;
        }

        public void setUserSpaceContexts(String userSpaceContexts) {
            this.userSpaceContexts = userSpaceContexts;
        }

        public String getMvpMusicNm() {
            return mvpMusicNm;
        }

        public void setMvpMusicNm(String mvpMusicNm) {
            this.mvpMusicNm = mvpMusicNm;
        }
    }

    // 用户信息数据类
    public static class UserInfo {
        private String playerUUID;    // 玩家UUID
        private String userNm;        // 用户名
        private String avatar;        // 头像URL
        private String loginIdNbr;    // 登录ID
        private XtnInfo xtnInfo;      // 扩展信息
        private String playerId;      // 玩家ID

        public UserInfo() {}

        // Getter和Setter方法
        public String getPlayerUUID() {
            return playerUUID;
        }

        public void setPlayerUUID(String playerUUID) {
            this.playerUUID = playerUUID;
        }

        public String getUserNm() {
            return userNm;
        }

        public void setUserNm(String userNm) {
            this.userNm = userNm;
        }

        public String getAvatar() {
            return avatar;
        }

        public void setAvatar(String avatar) {
            this.avatar = avatar;
        }

        public String getLoginIdNbr() {
            return loginIdNbr;
        }

        public void setLoginIdNbr(String loginIdNbr) {
            this.loginIdNbr = loginIdNbr;
        }

        public XtnInfo getXtnInfo() {
            return xtnInfo;
        }

        public void setXtnInfo(XtnInfo xtnInfo) {
            this.xtnInfo = xtnInfo;
        }

        public String getPlayerId() {
            return playerId;
        }

        public void setPlayerId(String playerId) {
            this.playerId = playerId;
        }

        // 便捷方法：获取用户卡片URL
        public String getUserCardUrl() {
            return xtnInfo != null ? xtnInfo.getUserCard() : null;
        }

        // 便捷方法：获取用户横幅URL
        public String getUserBannerUrl() {
            return xtnInfo != null ? xtnInfo.getUserBanner() : null;
        }

        // 便捷方法：获取MVP音乐URL
        public String getMvpMusicUrl() {
            return xtnInfo != null ? xtnInfo.getMvpMusicUrl() : null;
        }

        // 便捷方法：获取MVP视频URL
        public String getMvpVideosUrl() {
            return xtnInfo != null ? xtnInfo.getMvpVideosUrl() : null;
        }

        // 便捷方法：获取用户空间内容URL
        public String getUserSpaceContextsUrl() {
            return xtnInfo != null ? xtnInfo.getUserSpaceContexts() : null;
        }

        // 便捷方法：获取MVP音乐名称
        public String getMvpMusicName() {
            return xtnInfo != null ? xtnInfo.getMvpMusicNm() : null;
        }
    }

    // 响应数据类
    public static class QueryUserXtnInfoResponse {
        private String code;                        // 响应状态码（注意：API返回的是字符串类型）
        private String message;                     // 响应消息
        private Map<String, UserInfo> data;        // 响应数据，key为playerId，value为用户信息

        public QueryUserXtnInfoResponse() {}

        // Getter和Setter方法
        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Map<String, UserInfo> getData() {
            return data;
        }

        public void setData(Map<String, UserInfo> data) {
            this.data = data;
        }

        // 便捷方法：检查请求是否成功
        public boolean isSuccess() {
            return "0".equals(code);
        }

        // 便捷方法：获取特定玩家的信息
        public UserInfo getUserInfo(String playerId) {
            return data != null ? data.get(playerId) : null;
        }

        // 便捷方法：获取所有用户信息
        public List<UserInfo> getAllUserInfos() {
            return data != null ? List.copyOf(data.values()) : List.of();
        }
    }

    /**
     * 查询用户扩展信息 - 同步方法
     * @param request 请求参数
     * @return 响应结果
     * @throws IOException 网络请求异常
     * @throws InterruptedException 请求中断异常
     */
    public static QueryUserXtnInfoResponse queryUserXtnInfo(QueryUserXtnInfoRequest request) 
            throws IOException, InterruptedException {
        
        // 参数验证
        if (request == null) {
            logger.log(Level.WARNING, "Request parameter is null");
            throw new IllegalArgumentException("Request parameter cannot be null");
        }
        
        if (request.getPlayerIds() == null || request.getPlayerIds().isEmpty()) {
            logger.log(Level.WARNING, "PlayerIds list is null or empty");
            throw new IllegalArgumentException("PlayerIds list cannot be null or empty");
        }
        
        try {
            // 将请求对象转换为JSON字符串
            String requestBody = gson.toJson(request);
            
            // 构建HTTP请求
            var httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            
            // 发送请求并获取响应
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            
            // 检查HTTP状态码
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logger.log(Level.WARNING, "HTTP request failed with status code: " + response.statusCode());
                var errorResponse = new QueryUserXtnInfoResponse();
                errorResponse.setCode(String.valueOf(response.statusCode()));
                errorResponse.setMessage("HTTP request failed with status: " + response.statusCode());
                return errorResponse;
            }
            
            // 解析响应JSON为对象
            QueryUserXtnInfoResponse result;
            try {
                result = gson.fromJson(response.body(), QueryUserXtnInfoResponse.class);
            } catch (JsonSyntaxException e) {
                logger.log(Level.SEVERE, "Failed to parse JSON response", e);
                result = new QueryUserXtnInfoResponse();
                result.setCode("-2");
                result.setMessage("Failed to parse JSON response: " + e.getMessage());
                return result;
            }
            
            // 如果解析失败，创建一个包含原始响应的结果对象
            if (result == null) {
                logger.log(Level.WARNING, "Parsed result is null");
                result = new QueryUserXtnInfoResponse();
                result.setCode(String.valueOf(response.statusCode()));
                result.setMessage("Failed to parse response: " + response.body());
            } else {
                // 自动保存每个玩家的数据到JSON文件
                if (result.isSuccess() && result.getData() != null) {
                    result.getData().forEach((playerId, userInfo) -> {
                        // 保存单个玩家的完整数据
                        boolean saved = PlayerDataStorage.savePlayerData(playerId, userInfo);
                        if (saved) {
                            // 异步下载并缓存玩家头像
                            String avatarUrl = userInfo.getAvatar();
                            if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
                                ImageDownloader.downloadAvatar(avatarUrl, playerId)
                                    .exceptionally(throwable -> {
                                        logger.log(Level.WARNING, "Error caching avatar for player: " + userInfo.getUserNm() + " (" + playerId + ")", throwable);
                                        return null;
                                    });
                            }
                            
                            // 异步下载并缓存用户卡片
                            String userCardUrl = userInfo.getUserCardUrl();
                            if (userCardUrl != null && !userCardUrl.trim().isEmpty()) {
                                ImageDownloader.downloadUserCard(userCardUrl, playerId)
                                    .exceptionally(throwable -> {
                                        logger.log(Level.WARNING, "Error caching user card for player: " + userInfo.getUserNm() + " (" + playerId + ")", throwable);
                                        return null;
                                    });
                            }
                        } else {
                            logger.log(Level.WARNING, "Failed to save player data for playerId: " + playerId + " (Player: " + userInfo.getUserNm() + ")");
                        }
                    });
                }
            }
            
            return result;
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, "IO exception during API request", e);
            throw e;
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "Request was interrupted", e);
            Thread.currentThread().interrupt(); // 恢复中断状态
            throw e;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected exception during API request", e);
            throw new RuntimeException("Unexpected error during API request", e);
        }
    }

    /**
     * 查询用户扩展信息 - 异步方法
     * @param request 请求参数
     * @return CompletableFuture包装的响应结果
     */
    public static CompletableFuture<QueryUserXtnInfoResponse> queryUserXtnInfoAsync(QueryUserXtnInfoRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return queryUserXtnInfo(request);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "IO exception in async request", e);
                var errorResponse = new QueryUserXtnInfoResponse();
                errorResponse.setCode("-3");
                errorResponse.setMessage("IO exception: " + e.getMessage());
                return errorResponse;
            } catch (InterruptedException e) {
                logger.log(Level.SEVERE, "Interrupted exception in async request", e);
                Thread.currentThread().interrupt(); // 恢复中断状态
                var errorResponse = new QueryUserXtnInfoResponse();
                errorResponse.setCode("-4");
                errorResponse.setMessage("Request interrupted: " + e.getMessage());
                return errorResponse;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Unexpected exception in async request", e);
                var errorResponse = new QueryUserXtnInfoResponse();
                errorResponse.setCode("-1");
                errorResponse.setMessage("Request failed: " + e.getMessage());
                return errorResponse;
            }
        });
    }
}
