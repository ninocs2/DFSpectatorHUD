package com.ninocs.mygo.downloads;

import com.ninocs.mygo.util.SHA256;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * 异步图片下载器
 * 用于下载头像和用户卡片图片到本地缓存目录
 */
public class ImageDownloader {
    private static final Logger logger = Logger.getLogger(ImageDownloader.class.getName());
    
    // HTTP客户端配置
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    
    // 线程池用于异步下载
    private static final Executor downloadExecutor = Executors.newFixedThreadPool(4);
    
    // 缓存目录路径
    private static final String GAME_DIR = "MCGO";
    private static final String CACHE_DIR = "cache";
    private static final String AVATAR_DIR = "avatar";
    private static final String CARD_DIR = "card";
    
    // 下载状态跟踪
    private static final ConcurrentHashMap<String, CompletableFuture<DownloadResult>> downloadTasks = new ConcurrentHashMap<>();
    
    /**
     * 下载结果类
     */
    public static class DownloadResult {
        private final boolean success;
        private final String localPath;
        private final String errorMessage;
        private final long fileSize;
        
        public DownloadResult(boolean success, String localPath, String errorMessage, long fileSize) {
            this.success = success;
            this.localPath = localPath;
            this.errorMessage = errorMessage;
            this.fileSize = fileSize;
        }
        
        public boolean isSuccess() { return success; }
        public String getLocalPath() { return localPath; }
        public String getErrorMessage() { return errorMessage; }
        public long getFileSize() { return fileSize; }
    }
    
    /**
     * 下载进度回调接口
     */
    public interface DownloadProgressCallback {
        void onProgress(long bytesDownloaded, long totalBytes);
        void onComplete(DownloadResult result);
        void onError(String error);
    }
    
    /**
     * 异步下载头像图片
     * @param avatarUrl 头像URL
     * @param playerId 玩家ID（用于日志记录）
     * @param callback 下载进度回调（可选）
     * @return CompletableFuture包装的下载结果
     */
    public static CompletableFuture<DownloadResult> downloadAvatar(String avatarUrl, String playerId, 
                                                                   DownloadProgressCallback callback) {
        // 从URL中提取文件名（不含扩展名）
        String fileName = extractFileNameFromUrl(avatarUrl);
        if (fileName == null) {
            // 如果无法提取文件名，使用playerId作为备用文件名
            logger.log(Level.WARNING, "Could not extract filename from avatar URL, using playerId as filename: " + avatarUrl);
            fileName = playerId;
        }
        return downloadImage(avatarUrl, fileName, AVATAR_DIR, callback);
    }
    
    /**
     * 异步下载头像图片（无回调版本）
     * @param avatarUrl 头像URL
     * @param playerId 玩家ID（用于日志记录）
     * @return CompletableFuture包装的下载结果
     */
    public static CompletableFuture<DownloadResult> downloadAvatar(String avatarUrl, String playerId) {
        return downloadAvatar(avatarUrl, playerId, null);
    }
    
    /**
     * 异步下载用户卡片图片
     * @param cardUrl 用户卡片URL
     * @param playerId 玩家ID（用于日志记录）
     * @param callback 下载进度回调（可选）
     * @return CompletableFuture包装的下载结果
     */
    public static CompletableFuture<DownloadResult> downloadUserCard(String cardUrl, String playerId, 
                                                                     DownloadProgressCallback callback) {
        // 从URL中提取文件名（不含扩展名）
        String fileName = extractFileNameFromUrl(cardUrl);
        if (fileName == null) {
            // 如果无法提取文件名，使用playerId作为备用文件名
            logger.log(Level.WARNING, "Could not extract filename from user card URL, using playerId as filename: " + cardUrl);
            fileName = playerId;
        }
        return downloadImage(cardUrl, fileName, CARD_DIR, callback);
    }
    
    /**
     * 异步下载用户卡片图片（无回调版本）
     * @param cardUrl 用户卡片URL
     * @param playerId 玩家ID（用于日志记录）
     * @return CompletableFuture包装的下载结果
     */
    public static CompletableFuture<DownloadResult> downloadUserCard(String cardUrl, String playerId) {
        return downloadUserCard(cardUrl, playerId, null);
    }
    
    /**
     * 通用图片下载方法
     * @param imageUrl 图片URL
     * @param fileName 文件名（不含扩展名）
     * @param subDir 子目录（avatar或card）
     * @param callback 下载进度回调
     * @return CompletableFuture包装的下载结果
     */
    private static CompletableFuture<DownloadResult> downloadImage(String imageUrl, String fileName, 
                                                                   String subDir, DownloadProgressCallback callback) {
        // 参数验证
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            var errorResult = new DownloadResult(false, null, "Image URL is null or empty", 0);
            if (callback != null) {
                callback.onError("Image URL is null or empty");
            }
            return CompletableFuture.completedFuture(errorResult);
        }
        
        if (fileName == null || fileName.trim().isEmpty()) {
            var errorResult = new DownloadResult(false, null, "File name is null or empty", 0);
            if (callback != null) {
                callback.onError("File name is null or empty");
            }
            return CompletableFuture.completedFuture(errorResult);
        }
        
        // 生成唯一的任务键
        String taskKey = subDir + "_" + fileName + "_" + imageUrl.hashCode();
        
        // 检查是否已有相同的下载任务在进行
        var existingTask = downloadTasks.get(taskKey);
        if (existingTask != null && !existingTask.isDone()) {
            return existingTask;
        }
        
        // 创建新的下载任务
        var downloadTask = CompletableFuture.supplyAsync(() -> {
            try {
                return performDownload(imageUrl, fileName, subDir, callback);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Unexpected error during download", e);
                var errorResult = new DownloadResult(false, null, "Unexpected error: " + e.getMessage(), 0);
                if (callback != null) {
                    callback.onError("Unexpected error: " + e.getMessage());
                }
                return errorResult;
            } finally {
                // 任务完成后从跟踪Map中移除
                downloadTasks.remove(taskKey);
            }
        }, downloadExecutor);
        
        // 将任务添加到跟踪Map
        downloadTasks.put(taskKey, downloadTask);
        
        return downloadTask;
    }
    
    /**
     * 执行实际的下载操作
     * @param imageUrl 图片URL
     * @param fileName 文件名
     * @param subDir 子目录
     * @param callback 进度回调
     * @return 下载结果
     */
    private static DownloadResult performDownload(String imageUrl, String fileName, String subDir, 
                                                  DownloadProgressCallback callback) {
        try {
            // 创建缓存目录
            Path cacheDir = createCacheDirectory(subDir);
            if (cacheDir == null) {
                String error = "Failed to create cache directory: " + subDir;
                if (callback != null) callback.onError(error);
                return new DownloadResult(false, null, error, 0);
            }
            
            // 获取文件扩展名
            String fileExtension = getFileExtension(imageUrl);
            String fullFileName = fileName + fileExtension;
            Path targetFile = cacheDir.resolve(fullFileName);
            
            // 检查文件是否已存在且SHA256验证通过
            if (Files.exists(targetFile)) {
                // 如果URL包含SHA256，验证文件完整性
                if (!SHA256.needsRedownload(imageUrl, targetFile.toString())) {
                    long fileSize = Files.size(targetFile);
                    var result = new DownloadResult(true, targetFile.toString(), null, fileSize);
                    if (callback != null) callback.onComplete(result);
                    return result;
                } else {
                    // 删除无效文件，准备重新下载
                    try {
                        Files.deleteIfExists(targetFile);
                    } catch (IOException deleteEx) {
                        logger.log(Level.WARNING, "Failed to delete invalid file: " + targetFile, deleteEx);
                    }
                }
            }
            
            // 构建HTTP请求
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(imageUrl))
                    .header("User-Agent", "MCGO-Client/1.0")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            
            // 发送请求并获取响应
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            
            // 检查HTTP状态码
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String error = "HTTP request failed with status: " + response.statusCode();
                logger.log(Level.WARNING, error);
                if (callback != null) callback.onError(error);
                return new DownloadResult(false, null, error, 0);
            }
            
            // 获取文件大小
            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            
            // 下载文件
            try (InputStream inputStream = response.body()) {
                long bytesDownloaded = Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
                
                // 验证下载文件的SHA256（如果URL包含SHA256）
                String expectedSHA256 = SHA256.extractSHA256FromUrl(imageUrl);
                if (expectedSHA256 != null) {
                    if (!SHA256.verifyFileSHA256(targetFile.toString(), expectedSHA256)) {
                        // SHA256验证失败，删除文件并返回错误
                        try {
                            Files.deleteIfExists(targetFile);
                        } catch (IOException deleteEx) {
                            logger.log(Level.WARNING, "Failed to delete corrupted file: " + targetFile, deleteEx);
                        }
                        String error = "Downloaded file SHA256 verification failed for: " + imageUrl;
                        logger.log(Level.SEVERE, error);
                        if (callback != null) callback.onError(error);
                        return new DownloadResult(false, null, error, 0);
                    }
                }
                
                // 通知下载完成
                var result = new DownloadResult(true, targetFile.toString(), null, bytesDownloaded);
                if (callback != null) {
                    callback.onProgress(bytesDownloaded, contentLength > 0 ? contentLength : bytesDownloaded);
                    callback.onComplete(result);
                }
                return result;
            }
            
        } catch (IOException e) {
            String error = "IO error during download: " + e.getMessage();
            logger.log(Level.SEVERE, error, e);
            if (callback != null) callback.onError(error);
            return new DownloadResult(false, null, error, 0);
        } catch (InterruptedException e) {
            String error = "Download was interrupted: " + e.getMessage();
            logger.log(Level.WARNING, error, e);
            Thread.currentThread().interrupt(); // 恢复中断状态
            if (callback != null) callback.onError(error);
            return new DownloadResult(false, null, error, 0);
        } catch (Exception e) {
            String error = "Unexpected error during download: " + e.getMessage();
            logger.log(Level.SEVERE, error, e);
            if (callback != null) callback.onError(error);
            return new DownloadResult(false, null, error, 0);
        }
    }
    
    /**
     * 创建缓存目录
     * @param subDir 子目录名称
     * @return 创建的目录路径，失败返回null
     */
    private static Path createCacheDirectory(String subDir) {
        try {
            Path gameDir = Paths.get(System.getProperty("user.dir"), GAME_DIR);
            Path cacheDir = gameDir.resolve(CACHE_DIR).resolve(subDir);
            
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
            }
            
            return cacheDir;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to create cache directory: " + subDir, e);
            return null;
        }
    }
    
    /**
     * 从URL中提取文件扩展名
     * @param url 图片URL
     * @return 文件扩展名（包含点号），默认为.png
     */
    private static String getFileExtension(String url) {
        try {
            // 移除查询参数
            String cleanUrl = url.split("\\?")[0];
            
            // 提取文件扩展名
            int lastDotIndex = cleanUrl.lastIndexOf('.');
            if (lastDotIndex > 0 && lastDotIndex < cleanUrl.length() - 1) {
                String extension = cleanUrl.substring(lastDotIndex).toLowerCase();
                // 验证是否为常见图片格式
                if (extension.matches("\\.(png|jpg|jpeg|gif|bmp|webp)")) {
                    return extension;
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to extract file extension from URL: " + url, e);
        }
        
        // 默认使用.png扩展名
        return ".png";
    }
    
    /**
     * 从URL中提取文件名（不含扩展名）
     * @param url 图片URL
     * @return 文件名（不含扩展名），失败返回null
     */
    private static String extractFileNameFromUrl(String url) {
        try {
            // 移除查询参数
            String cleanUrl = url.split("\\?")[0];
            
            // 提取路径部分
            int lastSlashIndex = cleanUrl.lastIndexOf('/');
            if (lastSlashIndex >= 0 && lastSlashIndex < cleanUrl.length() - 1) {
                String fileName = cleanUrl.substring(lastSlashIndex + 1);
                
                // 移除扩展名
                int lastDotIndex = fileName.lastIndexOf('.');
                if (lastDotIndex > 0) {
                    return fileName.substring(0, lastDotIndex);
                } else {
                    return fileName;
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to extract filename from URL: " + url, e);
        }
        
        return null;
    }
    
    /**
     * 获取头像缓存路径
     * @param avatarUrl 头像URL
     * @return 头像文件的完整路径
     */
    public static String getAvatarCachePath(String avatarUrl) {
        Path gameDir = Paths.get(System.getProperty("user.dir"), GAME_DIR);
        Path avatarDir = gameDir.resolve(CACHE_DIR).resolve(AVATAR_DIR);
        
        // 从URL中提取文件名
        String fileName = extractFileNameFromUrl(avatarUrl);
        if (fileName == null) {
            // 如果无法提取文件名，使用默认名称
            fileName = "unknown";
        }
        
        // 获取文件扩展名
        String extension = getFileExtension(avatarUrl);
        
        return avatarDir.resolve(fileName + extension).toString();
    }
    
    /**
     * 获取用户卡片缓存路径
     * @param cardUrl 用户卡片URL
     * @return 用户卡片文件的完整路径
     */
    public static String getUserCardCachePath(String cardUrl) {
        Path gameDir = Paths.get(System.getProperty("user.dir"), GAME_DIR);
        Path cardDir = gameDir.resolve(CACHE_DIR).resolve(CARD_DIR);
        
        // 从URL中提取文件名
        String fileName = extractFileNameFromUrl(cardUrl);
        if (fileName == null) {
            // 如果无法提取文件名，使用默认名称
            fileName = "unknown";
        }
        
        // 获取文件扩展名
        String extension = getFileExtension(cardUrl);
        
        return cardDir.resolve(fileName + extension).toString();
    }
    
    /**
     * 检查文件是否已缓存
     * @param filePath 文件路径
     * @return 文件是否存在
     */
    public static boolean isCached(String filePath) {
        return Files.exists(Paths.get(filePath));
    }
    
    /**
     * 清理过期缓存文件（可选功能）
     * @param maxAgeHours 最大缓存时间（小时）
     */
    public static void cleanupCache(int maxAgeHours) {
        // TODO: 实现缓存清理逻辑
    }
    
    /**
     * 检查指定URL是否正在下载
     * @param imageUrl 图片URL
     * @param subDir 子目录（avatar或card）
     * @return 是否正在下载
     */
    public static boolean isDownloading(String imageUrl, String subDir) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return false;
        }
        
        String fileName = extractFileNameFromUrl(imageUrl);
        if (fileName == null) {
            return false;
        }
        
        String taskKey = subDir + "_" + fileName + "_" + imageUrl.hashCode();
        var task = downloadTasks.get(taskKey);
        return task != null && !task.isDone();
    }
    
    /**
     * 检查头像是否正在下载
     * @param avatarUrl 头像URL
     * @return 是否正在下载
     */
    public static boolean isAvatarDownloading(String avatarUrl) {
        return isDownloading(avatarUrl, AVATAR_DIR);
    }
    
    /**
     * 检查用户卡片是否正在下载
     * @param cardUrl 用户卡片URL
     * @return 是否正在下载
     */
    public static boolean isUserCardDownloading(String cardUrl) {
        return isDownloading(cardUrl, CARD_DIR);
    }
    
    /**
     * 获取正在进行的下载任务
     * @param imageUrl 图片URL
     * @param subDir 子目录
     * @return 下载任务的CompletableFuture，如果没有则返回null
     */
    public static CompletableFuture<DownloadResult> getDownloadTask(String imageUrl, String subDir) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return null;
        }
        
        String fileName = extractFileNameFromUrl(imageUrl);
        if (fileName == null) {
            return null;
        }
        
        String taskKey = subDir + "_" + fileName + "_" + imageUrl.hashCode();
        return downloadTasks.get(taskKey);
    }
    
    /**
     * 获取头像下载任务
     * @param avatarUrl 头像URL
     * @return 下载任务的CompletableFuture，如果没有则返回null
     */
    public static CompletableFuture<DownloadResult> getAvatarDownloadTask(String avatarUrl) {
        return getDownloadTask(avatarUrl, AVATAR_DIR);
    }
    
    /**
     * 获取用户卡片下载任务
     * @param cardUrl 用户卡片URL
     * @return 下载任务的CompletableFuture，如果没有则返回null
     */
    public static CompletableFuture<DownloadResult> getUserCardDownloadTask(String cardUrl) {
        return getDownloadTask(cardUrl, CARD_DIR);
    }
}