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
                    return;
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
        GuiGraphics gui = event.getGuiGraphics();

        // 只在观察者模式下显示
        if (mc.player == null || !mc.player.isSpectator()) {
            return;
        }

        // 防止在F1隐藏GUI时渲染
        if (mc.options.hideGui) {
            return;
        }

        // 检测观察状态变化并动态加载玩家数据
        String targetPlayerName = getCurrentObservedPlayerName(mc);
        if (!java.util.Objects.equals(currentObservedPlayer, targetPlayerName)) {
            currentObservedPlayer = targetPlayerName;
            
            if (targetPlayerName != null) {
                // 观察特定玩家
                loadPlayerData(targetPlayerName);
            } else {
                // 自由观察状态，显示自己的信息
                String selfName = mc.player.getName().getString();
                loadPlayerData(selfName);
            }
        }

        // 屏幕宽高
        int screenWidth = event.getWindow().getGuiScaledWidth();
        int screenHeight = event.getWindow().getGuiScaledHeight();

        // 容器居中计算（只基于图片容器高度）
        int frameX = screenWidth - FRAME_WIDTH - 2; // 右边距离2px
        int frameY = (screenHeight / 2) - (FRAME_HEIGHT / 2); // 垂直居中
        
        // 底部容器位置（位于图片容器内部的底部）
        int bottomContainerY = frameY + FRAME_HEIGHT - BOTTOM_CONTAINER_HEIGHT; // 在图片容器底部

        // 绘制图片容器（半透明黑色背景，无边框）
        int bgColor = 0x80000000; // 半透明黑色背景
        gui.fill(frameX, frameY, frameX + FRAME_WIDTH, frameY + FRAME_HEIGHT, bgColor);

        // 如果有背景图片，则绘制图片
        if (dynamicTexture != null) {
            // 图片缩放比例（填充整个容器，保持宽高比）
            float scaleX = (float) FRAME_WIDTH / imageWidth;
            float scaleY = (float) FRAME_HEIGHT / imageHeight;
            float scale = Math.max(scaleX, scaleY); // 选择较大的缩放比例以填充整个容器
            
            int drawWidth = (int) (imageWidth * scale);
            int drawHeight = (int) (imageHeight * scale);

            // 计算裁剪区域（平均裁剪两侧和上下）
            float uMin = 0.0f;
            float vMin = 0.0f;
            float uMax = 1.0f;
            float vMax = 1.0f;
            
            // 如果宽度超出，平均裁剪左右两侧
            if (drawWidth > FRAME_WIDTH) {
                float excess = (drawWidth - FRAME_WIDTH) / (float) drawWidth;
                uMin = excess / 2;
                uMax = 1.0f - excess / 2;
            }
            
            // 如果高度超出，平均裁剪上下两侧
            if (drawHeight > FRAME_HEIGHT) {
                float excess = (drawHeight - FRAME_HEIGHT) / (float) drawHeight;
                vMin = excess / 2;
                vMax = 1.0f - excess / 2;
            }
            
            // 禁用模糊过滤，确保原分辨率绘制
            try {
                mc.getTextureManager().getTexture(dynamicTexture).setFilter(false, false);
            } catch (Exception e) {
                LOGGER.warn("[DFSpectatorUi] Failed to set texture filter: {}", e.getMessage());
            }

            // 开启混合以支持透明像素
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            // 绘制图片 - 使用UV坐标实现平均裁剪
            gui.blit(
                    dynamicTexture,
                    frameX, frameY,
                    FRAME_WIDTH, FRAME_HEIGHT,
                    (int)(uMin * imageWidth), (int)(vMin * imageHeight),
                    (int)(uMax * imageWidth - uMin * imageWidth), (int)(vMax * imageHeight - vMin * imageHeight),
                    imageWidth, imageHeight
            );
            
            RenderSystem.disableBlend();
        }

        // 绘制底部容器（覆盖在图片上方，半透明黑色背景）
        int bottomBgColor = 0x50000000; // 半透明黑色背景
        gui.fill(frameX, bottomContainerY, frameX + FRAME_WIDTH, bottomContainerY + BOTTOM_CONTAINER_HEIGHT, bottomBgColor);

        // 绘制头像（在底部容器中靠左居中）
        int avatarX = frameX + AVATAR_MARGIN;
        int avatarY = bottomContainerY + (BOTTOM_CONTAINER_HEIGHT - AVATAR_SIZE) / 2; // 垂直居中
        
        if (useNativeSkin && currentPlayer != null) {
            // 使用原生皮肤头像
            PlayerInfo playerInfo = mc.getConnection().getPlayerInfo(currentPlayer.getUUID());
            if (playerInfo != null) {
                ResourceLocation skinTexture = playerInfo.getSkinLocation();
                // 绘制头部
                gui.blit(skinTexture, avatarX, avatarY, AVATAR_SIZE, AVATAR_SIZE, 8, 8, 8, 8, 64, 64);
                // 绘制帽子层
                gui.blit(skinTexture, avatarX, avatarY, AVATAR_SIZE, AVATAR_SIZE, 40, 8, 8, 8, 64, 64);
            }
        } else if (avatarTexture != null) {
            // 使用自定义头像
            gui.blit(
                    avatarTexture,
                    avatarX, avatarY,
                    AVATAR_SIZE, AVATAR_SIZE,
                    0, 0,
                    AVATAR_SIZE, AVATAR_SIZE,
                    AVATAR_SIZE, AVATAR_SIZE
            );
        }

        // 绘制玩家名称（在底部容器中右侧显示）
        String displayName = currentObservedPlayer != null ? currentObservedPlayer : playerName;
        if (displayName != null && !displayName.isEmpty()) {
            int textX = frameX + AVATAR_MARGIN + AVATAR_SIZE + 4; // 头像右侧4像素间距
            int textY = bottomContainerY + (BOTTOM_CONTAINER_HEIGHT - 8) / 2; // 垂直居中（字体高度约8像素）
            int textColor = 0xFFFFFFFF; // 白色文字
            
            gui.drawString(Minecraft.getInstance().font, displayName, textX, textY, textColor);
        }

        RenderSystem.disableBlend();
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
        if (!hudEnabled) {
            return;
        }
        
        if (!java.util.Objects.equals(currentObservedPlayer, playerName)) {
            currentObservedPlayer = playerName;
            
            if (playerName != null) {
                loadPlayerData(playerName);
            } else {
                // 自由观察状态，显示自己的信息
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    String selfName = mc.player.getName().getString();
                    loadPlayerData(selfName);
                }
            }
        }
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