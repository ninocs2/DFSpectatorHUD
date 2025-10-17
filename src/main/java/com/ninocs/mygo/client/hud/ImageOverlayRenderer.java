package com.ninocs.mygo.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.ninocs.mygo.client.data.PlayerExtendedInfo;
import com.ninocs.mygo.map.PlayerExtendedInfoReader;
import com.ninocs.mygo.downloads.ImageDownloader;
import com.ninocs.mygo.util.SHA256;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Mod.EventBusSubscriber(modid = "dfspectatorui", value = Dist.CLIENT)
public class ImageOverlayRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // 控制HUD显示的开关
    private static boolean hudEnabled = false;
    
    // 动态加载的纹理
    private static ResourceLocation dynamicTexture = null;
    private static int imageWidth = 256;
    private static int imageHeight = 128;
    
    // 头像纹理
    private static ResourceLocation avatarTexture = null;
    
    // 当前玩家实体（用于获取皮肤）
    private static Player currentPlayer = null;
    
    // 是否使用原生皮肤头像
    private static boolean useNativeSkin = false;
    
    // 玩家名称
    private static String playerName = "PlayerId"; // 默认显示
    
    // 当前观察的玩家名称（用于动态加载数据）
    private static String currentObservedPlayer = null;
    
    // 下载状态跟踪
    private static boolean isAvatarDownloading = false;
    private static String downloadingAvatarUrl = null;
    private static String downloadingCardUrl = null;
    
    // 容器尺寸 (9:16比例)
    private static final int FRAME_WIDTH = 108;
    private static final int FRAME_HEIGHT = 192;
    
    // 底部容器高度
    private static final int BOTTOM_CONTAINER_HEIGHT = 30;
    
    // 头像相关常量
    private static final int AVATAR_SIZE = 24; // 头像尺寸 (24x24像素)
    private static final int AVATAR_MARGIN = 3; // 头像边距

    /**
     * 启用HUD显示
     */
    public static void enableHud() {
        hudEnabled = true;
        
        // 获取当前玩家名称
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            playerName = mc.player.getName().getString();
        }
    }
    
    /**
     * 验证图片文件的SHA256哈希值
     * @param imagePath 图片文件路径
     * @param imageUrl 图片URL（包含SHA256）
     * @return true如果验证通过，false如果验证失败
     */
    private static boolean verifyImageSHA256(String imagePath, String imageUrl) {
        if (imagePath == null || imageUrl == null) {
            return false;
        }
        
        try {
            Path path = Paths.get(imagePath);
            if (!Files.exists(path)) {
                return false;
            }
            
            // 使用SHA256工具类验证文件
            boolean isValid = SHA256.verifyUrlFileSHA256(imageUrl, imagePath);
            
            if (!isValid) {
                // 删除无效文件
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    LOGGER.error("[DFSpectatorUi] 删除无效图片文件失败: {}", imagePath, e);
                }
            }
            
            return isValid;
        } catch (Exception e) {
            LOGGER.error("[DFSpectatorUi] 验证图片SHA256时发生错误: {}", imagePath, e);
            return false;
        }
    }

    /**
     * 开始下载卡片
     * @param playerName 玩家名称
     * @param userCardUrl 卡片URL
     */
    private static void startCardDownload(String playerName, String userCardUrl) {
        // 检查是否正在下载
        if (ImageDownloader.isUserCardDownloading(userCardUrl)) {
            // 显示透明背景，等待下载完成
            dynamicTexture = null;
            downloadingCardUrl = userCardUrl;
            
            // 设置下载完成回调
            var downloadTask = ImageDownloader.getUserCardDownloadTask(userCardUrl);
            if (downloadTask != null) {
                downloadTask.thenAccept(result -> {
                    if (result.isSuccess() && userCardUrl.equals(downloadingCardUrl)) {
                        // 下载完成，重新加载
                        loadExternalImage(result.getLocalPath());
                        downloadingCardUrl = null;
                    }
                });
            }
        } else {
            // 开始下载
            dynamicTexture = null;
            downloadingCardUrl = userCardUrl;
            
            ImageDownloader.downloadUserCard(userCardUrl, playerName, new ImageDownloader.DownloadProgressCallback() {
                @Override
                public void onProgress(long bytesDownloaded, long totalBytes) {
                    // 可以在这里添加进度显示逻辑
                }
                
                @Override
                public void onComplete(ImageDownloader.DownloadResult result) {
                    if (result.isSuccess() && userCardUrl.equals(downloadingCardUrl)) {
                        // 下载完成，重新加载
                        loadExternalImage(result.getLocalPath());
                        downloadingCardUrl = null;
                    }
                }
                
                @Override
                public void onError(String error) {
                    LOGGER.warn("[DFSpectatorUi] 玩家 {} 的卡片下载失败: {}", playerName, error);
                    // 下载失败，不显示背景
                    dynamicTexture = null;
                    downloadingCardUrl = null;
                }
            });
        }
    }

    /**
     * 开始下载头像
     * @param playerName 玩家名称
     * @param avatarUrl 头像URL
     */
    private static void startAvatarDownload(String playerName, String avatarUrl) {
        // 检查是否正在下载
        if (ImageDownloader.isAvatarDownloading(avatarUrl)) {
            // 使用原生皮肤，等待下载完成
            isAvatarDownloading = true;
            downloadingAvatarUrl = avatarUrl;
            
            // 设置下载完成回调
            var downloadTask = ImageDownloader.getAvatarDownloadTask(avatarUrl);
            if (downloadTask != null) {
                downloadTask.thenAccept(result -> {
                    if (result.isSuccess() && avatarUrl.equals(downloadingAvatarUrl)) {
                        // 下载完成，重新加载
                        loadAvatarImage(result.getLocalPath());
                        useNativeSkin = false;
                        currentPlayer = null;
                        isAvatarDownloading = false;
                        downloadingAvatarUrl = null;
                    }
                });
            }
        } else {
            // 开始下载
            isAvatarDownloading = true;
            downloadingAvatarUrl = avatarUrl;
            
            ImageDownloader.downloadAvatar(avatarUrl, playerName, new ImageDownloader.DownloadProgressCallback() {
                @Override
                public void onProgress(long bytesDownloaded, long totalBytes) {
                    // 可以在这里添加进度显示逻辑
                }
                
                @Override
                public void onComplete(ImageDownloader.DownloadResult result) {
                    if (result.isSuccess() && avatarUrl.equals(downloadingAvatarUrl)) {
                        // 下载完成，重新加载
                        loadAvatarImage(result.getLocalPath());
                        useNativeSkin = false;
                        currentPlayer = null;
                        isAvatarDownloading = false;
                        downloadingAvatarUrl = null;
                    }
                }
                
                @Override
                public void onError(String error) {
                    LOGGER.warn("[DFSpectatorUi] 玩家 {} 的头像下载失败: {}", playerName, error);
                    isAvatarDownloading = false;
                    downloadingAvatarUrl = null;
                    // 下载失败，继续使用原生皮肤逻辑
                }
            });
        }
    }

    private static void loadAvatarImage(String avatarPath) {
        try {
            Path fullPath = Paths.get(avatarPath);
            if (!Files.exists(fullPath)) {
                LOGGER.warn("[DFSpectatorUi] 头像文件不存在: {}", fullPath.toAbsolutePath());
                return;
            }

            // 清理旧的头像纹理
            if (avatarTexture != null) {
                Minecraft.getInstance().getTextureManager().release(avatarTexture);
            }

            // 读取头像文件
            try (FileInputStream fis = new FileInputStream(fullPath.toFile())) {
                NativeImage nativeImage = NativeImage.read(fis);
                
                // 创建动态纹理
                DynamicTexture texture = new DynamicTexture(nativeImage);
                avatarTexture = ResourceLocation.fromNamespaceAndPath("dfspectatorui", "dynamic_avatar");
                
                // 注册纹理到纹理管理器
                Minecraft.getInstance().getTextureManager().register(avatarTexture, texture);
            }
        } catch (IOException e) {
            LOGGER.error("[DFSpectatorUi] 加载头像失败: {}", avatarPath, e);
        }
    }

    /**
     * 禁用HUD显示
     */
    public static void disableHud() {
        hudEnabled = false;
        if (dynamicTexture != null) {
            // 清理纹理资源
            Minecraft.getInstance().getTextureManager().release(dynamicTexture);
            dynamicTexture = null;
        }

    }

    /**
     * 从外部路径加载图片
     */
    private static void loadExternalImage(String imagePath) {
        try {
            Path fullPath = Paths.get(imagePath);
            if (!Files.exists(fullPath)) {
                LOGGER.warn("[DFSpectatorUi] 图片文件不存在: {}", fullPath.toAbsolutePath());
                return;
            }

            // 清理旧的纹理
            if (dynamicTexture != null) {
                Minecraft.getInstance().getTextureManager().release(dynamicTexture);
            }

            // 读取图片文件
            try (FileInputStream fis = new FileInputStream(fullPath.toFile())) {
                NativeImage nativeImage = NativeImage.read(fis);
                
                // 更新图片尺寸
                imageWidth = nativeImage.getWidth();
                imageHeight = nativeImage.getHeight();
                
                // 创建动态纹理
                DynamicTexture texture = new DynamicTexture(nativeImage);
                dynamicTexture = ResourceLocation.fromNamespaceAndPath("dfspectatorui", "dynamic_overlay");
                
                // 注册纹理到纹理管理器
                Minecraft.getInstance().getTextureManager().register(dynamicTexture, texture);
            }
        } catch (IOException e) {
            LOGGER.error("[DFSpectatorUi] 加载外部图片失败: {}", imagePath, e);
        }
    }
    
    /**
     * 加载玩家数据（包括头像和卡片）
     */
    private static void loadPlayerData(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return;
        }

        // 使用新的PlayerExtendedInfoReader读取玩家扩展信息
        PlayerExtendedInfo playerInfo = PlayerExtendedInfoReader.readPlayerExtendedInfo(playerName);
        
        if (playerInfo != null) {
            // 处理用户卡片
            String userCardUrl = playerInfo.getUserCard();
            if (playerInfo.hasUserCard()) {
                // 使用ImageDownloader的方法获取正确的缓存路径
                String cardPath = ImageDownloader.getUserCardCachePath(userCardUrl);
                Path cardPathObj = Paths.get(cardPath);
                
                // 检查文件是否已存在并验证SHA256
                if (Files.exists(cardPathObj)) {
                    // 验证SHA256哈希值
                    if (verifyImageSHA256(cardPath, userCardUrl)) {
                        loadExternalImage(cardPath);
                        downloadingCardUrl = null;
                    } else {
                        // SHA256验证失败，重新下载
                        // 文件已在verifyImageSHA256中被删除，继续下载流程
                        startCardDownload(playerName, userCardUrl);
                    }
                } else {
                    startCardDownload(playerName, userCardUrl);
                }
            } else {
                // 如果没有玩家专属卡片，不显示背景
                dynamicTexture = null;
                downloadingCardUrl = null;
            }

            // 处理头像
            String avatarUrl = playerInfo.getAvatar();
            if (playerInfo.hasAvatar()) {
                // 使用ImageDownloader的方法获取正确的缓存路径
                String avatarPath = ImageDownloader.getAvatarCachePath(avatarUrl);
                Path path = Paths.get(avatarPath);
                
                // 检查文件是否已存在并验证SHA256
                if (Files.exists(path)) {
                    // 验证SHA256哈希值
                    if (verifyImageSHA256(avatarPath, avatarUrl)) {
                        loadAvatarImage(avatarPath);
                        useNativeSkin = false;
                        currentPlayer = null;
                        isAvatarDownloading = false;
                        downloadingAvatarUrl = null;
                        return;
                    } else {
                        // SHA256验证失败，重新下载
                        // 文件已在verifyImageSHA256中被删除，继续下载流程
                        startAvatarDownload(playerName, avatarUrl);
                        return;
                    }
                } else {
                    startAvatarDownload(playerName, avatarUrl);
                }
            } else {
                isAvatarDownloading = false;
                downloadingAvatarUrl = null;
            }
        } else {
            // 如果没有玩家扩展信息，不显示背景
            dynamicTexture = null;
            downloadingCardUrl = null;
            isAvatarDownloading = false;
            downloadingAvatarUrl = null;
        }
        
        // 如果没有自定义头像或头像正在下载，使用原生皮肤
        if (!isAvatarDownloading && avatarTexture == null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                // 查找玩家实体
                Player targetPlayer = null;
                
                // 如果是当前玩家
                if (mc.player != null && playerName.equals(mc.player.getName().getString())) {
                    targetPlayer = mc.player;
                } else {
                    // 查找其他玩家
                    for (Player player : mc.level.players()) {
                        if (playerName.equals(player.getName().getString())) {
                            targetPlayer = player;
                            break;
                        }
                    }
                }
                
                if (targetPlayer != null) {
                    currentPlayer = targetPlayer;
                    useNativeSkin = true;
                    avatarTexture = null;
                }
            }
        }
        
        // 如果头像正在下载，也使用原生皮肤作为临时显示
        if (isAvatarDownloading) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                // 查找玩家实体
                Player targetPlayer = null;
                
                // 如果是当前玩家
                if (mc.player != null && playerName.equals(mc.player.getName().getString())) {
                    targetPlayer = mc.player;
                } else {
                    // 查找其他玩家
                    for (Player player : mc.level.players()) {
                        if (playerName.equals(player.getName().getString())) {
                            targetPlayer = player;
                            break;
                        }
                    }
                }
                
                if (targetPlayer != null) {
                    currentPlayer = targetPlayer;
                    useNativeSkin = true;
                    return;
                }
            }
        }
        
        // 如果都找不到，不加载任何头像
        useNativeSkin = false;
        currentPlayer = null;
        if (!isAvatarDownloading) {
            avatarTexture = null;
        }
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiEvent.Post event) {
        if (!hudEnabled) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        // 检查是否在观察者模式
        if (!mc.player.isSpectator()) {
            return;
        }

        // 获取当前观察的玩家名称
        String observedPlayerName = getCurrentObservedPlayerName(mc);
        
        // 如果观察的玩家发生变化，重新加载数据
        if (!observedPlayerName.equals(currentObservedPlayer)) {
            currentObservedPlayer = observedPlayerName;
            playerName = observedPlayerName;
            
            // 清理旧的纹理
            if (dynamicTexture != null) {
                mc.getTextureManager().release(dynamicTexture);
                dynamicTexture = null;
            }
            if (avatarTexture != null) {
                mc.getTextureManager().release(avatarTexture);
                avatarTexture = null;
            }
            
            // 重置状态
            useNativeSkin = false;
            currentPlayer = null;
            isAvatarDownloading = false;
            downloadingAvatarUrl = null;
            downloadingCardUrl = null;
            
            // 加载新玩家的数据
            loadPlayerData(observedPlayerName);
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // 计算容器位置（右上角）
        int containerX = screenWidth - FRAME_WIDTH - 10;
        int containerY = 10;

        // 绘制背景容器
        guiGraphics.fill(containerX, containerY, containerX + FRAME_WIDTH, containerY + FRAME_HEIGHT, 0x80000000);

        // 绘制背景图片（如果有）
        if (dynamicTexture != null) {
            RenderSystem.setShaderTexture(0, dynamicTexture);
            guiGraphics.blit(dynamicTexture, containerX, containerY, 0, 0, FRAME_WIDTH, FRAME_HEIGHT - BOTTOM_CONTAINER_HEIGHT, imageWidth, imageHeight);
        }

        // 绘制底部容器
        int bottomY = containerY + FRAME_HEIGHT - BOTTOM_CONTAINER_HEIGHT;
        guiGraphics.fill(containerX, bottomY, containerX + FRAME_WIDTH, containerY + FRAME_HEIGHT, 0xCC000000);

        // 绘制头像
        int avatarX = containerX + AVATAR_MARGIN;
        int avatarY = bottomY + (BOTTOM_CONTAINER_HEIGHT - AVATAR_SIZE) / 2;

        if (avatarTexture != null) {
            // 使用自定义头像
            RenderSystem.setShaderTexture(0, avatarTexture);
            guiGraphics.blit(avatarTexture, avatarX, avatarY, 0, 0, AVATAR_SIZE, AVATAR_SIZE, AVATAR_SIZE, AVATAR_SIZE);
        } else if (useNativeSkin && currentPlayer != null) {
            // 使用原生皮肤头像
            PlayerInfo playerInfo = mc.getConnection().getPlayerInfo(currentPlayer.getUUID());
            if (playerInfo != null) {
                ResourceLocation skinLocation = playerInfo.getSkinLocation();
                RenderSystem.setShaderTexture(0, skinLocation);
                
                // 绘制头部（8x8像素，从皮肤纹理的8,8位置开始）
                guiGraphics.blit(skinLocation, avatarX, avatarY, AVATAR_SIZE, AVATAR_SIZE, 8, 8, 8, 8, 64, 64);
                
                // 绘制头部覆盖层（帽子等，从皮肤纹理的40,8位置开始）
                guiGraphics.blit(skinLocation, avatarX, avatarY, AVATAR_SIZE, AVATAR_SIZE, 40, 8, 8, 8, 64, 64);
            }
        }

        // 绘制玩家名称
        int textX = avatarX + AVATAR_SIZE + 5;
        int textY = bottomY + (BOTTOM_CONTAINER_HEIGHT - mc.font.lineHeight) / 2;
        
        // 计算可用文本宽度
        int availableWidth = FRAME_WIDTH - AVATAR_SIZE - AVATAR_MARGIN - 5 - 5; // 减去头像、边距和额外间距
        
        // 如果文本太长，进行截断
        String displayName = playerName;
        if (mc.font.width(displayName) > availableWidth) {
            displayName = mc.font.plainSubstrByWidth(displayName, availableWidth - mc.font.width("...")) + "...";
        }
        
        guiGraphics.drawString(mc.font, displayName, textX, textY, 0xFFFFFF);
    }

    /**
     * 获取当前观察的玩家名称
     * @param mc Minecraft实例
     * @return 观察的玩家名称，如果是自由观察则返回null
     */
    private static String getCurrentObservedPlayerName(Minecraft mc) {
        if (mc.getCameraEntity() != null && mc.getCameraEntity() != mc.player) {
            Entity cameraEntity = mc.getCameraEntity();
            if (cameraEntity instanceof Player player) {
                return player.getName().getString();
            }
        }
        return null; // 自由观察状态
    }

    /**
     * 通知观察状态变化（由SpectatorModeListener调用）
     */
    public static void notifyObservedPlayerChange(String playerName) {
        // 强制重新加载数据
        currentObservedPlayer = null;
    }

    /**
     * 设置自定义图片路径
     */
    public static void setImagePath(String imagePath) {
        if (hudEnabled) {
            loadExternalImage(imagePath);
        }
    }
}