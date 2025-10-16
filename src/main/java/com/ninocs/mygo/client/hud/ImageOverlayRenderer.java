package com.ninocs.mygo.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.ninocs.mygo.client.data.PlayerExtendedInfo;
import com.ninocs.mygo.map.PlayerExtendedInfoReader;
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
    
    // 调试日志开关
    private static final boolean DEBUG_LOGGING = false;
    
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
    
    // 容器尺寸 (9:16比例)
    private static final int FRAME_WIDTH = 108;
    private static final int FRAME_HEIGHT = 192;
    
    // 底部容器高度
    private static final int BOTTOM_CONTAINER_HEIGHT = 30;
    
    // 头像相关常量
    private static final int AVATAR_SIZE = 24; // 头像尺寸 (24x24像素)
    private static final int AVATAR_MARGIN = 3; // 头像边距
    
    // 默认背景图片路径（相对于游戏运行目录）
    private static final String DEFAULT_IMAGE_PATH = "MCGO/cache/card/4cc241234f1bbd63ed4d60ce87c87f8ea81e5bde72aa3954fe3ec1ccf77f9e79.png";

    /**
     * 启用HUD显示
     */
    public static void enableHud() {
        hudEnabled = true;
        loadExternalImage(DEFAULT_IMAGE_PATH);
        
        // 获取当前玩家名称
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            playerName = mc.player.getName().getString();
        }
        
        if (DEBUG_LOGGING) {
            LOGGER.info("[DFSpectatorUi] HUD显示已启用");
        }
    }
    
    /**
     * 从指定路径加载头像
     */
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
                
                if (DEBUG_LOGGING) {
                    LOGGER.info("[DFSpectatorUi] 成功加载头像: {}", fullPath.toAbsolutePath());
                }
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
        if (DEBUG_LOGGING) {
            LOGGER.info("[DFSpectatorUi] HUD显示已禁用");
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
            
            if (DEBUG_LOGGING) {
                LOGGER.info("[DFSpectatorUi] 成功加载外部图片: {} ({}x{})", fullPath.toAbsolutePath(), imageWidth, imageHeight);
            }
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
        
        if (DEBUG_LOGGING) {
            LOGGER.info("[DFSpectatorUi] 开始加载玩家数据: {}", playerName);
        }

        // 使用新的PlayerExtendedInfoReader读取玩家扩展信息
        PlayerExtendedInfo playerInfo = PlayerExtendedInfoReader.readPlayerExtendedInfo(playerName);
        
        if (playerInfo != null) {
            if (DEBUG_LOGGING) {
                LOGGER.info("[DFSpectatorUi] 成功读取玩家 {} 的扩展信息: {}", playerName, playerInfo.toString());
            }

            // 处理用户卡片
            String userCardUrl = playerInfo.getUserCard();
            if (playerInfo.hasUserCard()) {
                if (DEBUG_LOGGING) {
                    LOGGER.info("[DFSpectatorUi] 玩家 {} 有专属卡片URL: {}", playerName, userCardUrl);
                }
                
                // 使用ImageDownloader的方法获取正确的缓存路径
                String cardPath = com.ninocs.mygo.downloads.ImageDownloader.getUserCardCachePath(userCardUrl);
                Path cardPathObj = Paths.get(cardPath);
                if (Files.exists(cardPathObj)) {
                    if (DEBUG_LOGGING) {
                        LOGGER.info("[DFSpectatorUi] 为玩家 {} 加载背景卡片: {}", playerName, cardPath);
                    }
                    loadExternalImage(cardPath);
                } else {
                    LOGGER.warn("[DFSpectatorUi] 玩家 {} 有专属卡片URL但缓存文件不存在: {}", playerName, cardPath);
                    loadExternalImage(DEFAULT_IMAGE_PATH);
                }
            } else {
                // 如果没有玩家专属卡片，使用默认卡片
                if (DEBUG_LOGGING) {
                    LOGGER.info("[DFSpectatorUi] 玩家 {} 没有专属卡片，使用默认卡片", playerName);
                }
                loadExternalImage(DEFAULT_IMAGE_PATH);
            }

            // 处理头像
            String avatarUrl = playerInfo.getAvatar();
            if (playerInfo.hasAvatar()) {
                if (DEBUG_LOGGING) {
                    LOGGER.info("[DFSpectatorUi] 玩家 {} 有头像URL: {}", playerName, avatarUrl);
                }
                
                // 使用ImageDownloader的方法获取正确的缓存路径
                String avatarPath = com.ninocs.mygo.downloads.ImageDownloader.getAvatarCachePath(avatarUrl);
                Path path = Paths.get(avatarPath);
                if (Files.exists(path)) {
                    if (DEBUG_LOGGING) {
                        LOGGER.info("[DFSpectatorUi] 为玩家 {} 加载头像: {}", playerName, avatarPath);
                    }
                    loadAvatarImage(avatarPath);
                    useNativeSkin = false;
                    currentPlayer = null;
                    return;
                } else {
                    LOGGER.warn("[DFSpectatorUi] 玩家 {} 有头像URL但缓存文件不存在: {}", playerName, avatarPath);
                }
            } else {
                if (DEBUG_LOGGING) {
                    LOGGER.info("[DFSpectatorUi] 玩家 {} 没有头像URL", playerName);
                }
            }
        } else {
            // 如果没有玩家扩展信息，使用默认背景
            if (DEBUG_LOGGING) {
                LOGGER.info("[DFSpectatorUi] 玩家 {} 没有扩展信息，使用默认背景", playerName);
            }
            loadExternalImage(DEFAULT_IMAGE_PATH);
        }
        
        // 如果没有自定义头像，使用原生皮肤
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
                // 清理旧的头像纹理
                if (avatarTexture != null) {
                    avatarTexture = null;
                }
                return;
            }
        }
        
        // 如果都找不到，不加载任何头像
        useNativeSkin = false;
        currentPlayer = null;
        avatarTexture = null;
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiEvent.Post event) {
        if (!hudEnabled || dynamicTexture == null) {
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
                if (DEBUG_LOGGING) {
                    LOGGER.info("[DFSpectatorUi] 切换到观察玩家: {}", targetPlayerName);
                }
                loadPlayerData(targetPlayerName);
            } else {
                // 自由观察状态，显示自己的信息
                String selfName = mc.player.getName().getString();
                if (DEBUG_LOGGING) {
                    LOGGER.info("[DFSpectatorUi] 切换到自由观察状态，显示自己: {}", selfName);
                }
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
        mc.getTextureManager().getTexture(dynamicTexture).setFilter(false, false);

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
                // 观察特定玩家
                if (DEBUG_LOGGING) {
                    LOGGER.info("[DFSpectatorUi] 切换到观察玩家: {}", playerName);
                }
                loadPlayerData(playerName);
            } else {
                // 自由观察状态，显示自己的信息
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    String selfName = mc.player.getName().getString();
                    if (DEBUG_LOGGING) {
                        LOGGER.info("[DFSpectatorUi] 切换到自由观察状态，显示自己: {}", selfName);
                    }
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