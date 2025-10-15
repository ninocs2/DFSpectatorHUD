package com.ninocs.mygo.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
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
    
    // 玩家名称
    private static String playerName = "PlayerId"; // 默认显示
    
    // 容器尺寸 (9:16比例)
    private static final int FRAME_WIDTH = 108;
    private static final int FRAME_HEIGHT = 192;
    
    // 底部容器高度
    private static final int BOTTOM_CONTAINER_HEIGHT = 30;
    
    // 头像相关常量
    private static final int AVATAR_SIZE = 24; // 头像尺寸 (24x24像素)
    private static final int AVATAR_MARGIN = 3; // 头像边距
    private static final String DEFAULT_AVATAR_PATH = "MCGO/cache/avatar/29e8f55c93fde9711c77c4796331fec536973d8d73f35c1bacc6b71dad1d5efd.png";
    
    // 默认背景图片路径（相对于游戏运行目录）
    private static final String DEFAULT_IMAGE_PATH = "MCGO/cache/card/523f8e5883a34cee54304e35ba7208cbf3c610de891f7646f4eb516ca40141af.jpg";

    /**
     * 启用HUD显示
     */
    public static void enableHud() {
        hudEnabled = true;
        loadExternalImage(DEFAULT_IMAGE_PATH);
        loadAvatarImage(); // 加载默认头像
        
        // 获取当前玩家名称
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            playerName = mc.player.getName().getString();
        }
        
        LOGGER.info("[DFSpectatorUi] HUD显示已启用");
    }

    /**
     * 从外部路径加载头像
     */
    private static void loadAvatarImage() {
        try {
            Path fullPath = Paths.get(DEFAULT_AVATAR_PATH);
            if (!Files.exists(fullPath)) {
                LOGGER.warn("[DFSpectatorUi] 头像文件不存在: {}", fullPath.toAbsolutePath());
                return;
            }

            // 读取头像文件
            try (FileInputStream fis = new FileInputStream(fullPath.toFile())) {
                NativeImage nativeImage = NativeImage.read(fis);
                
                // 创建动态纹理
                DynamicTexture texture = new DynamicTexture(nativeImage);
                avatarTexture = ResourceLocation.fromNamespaceAndPath("dfspectatorui", "dynamic_avatar");
                
                // 注册纹理到纹理管理器
                Minecraft.getInstance().getTextureManager().register(avatarTexture, texture);
                
                LOGGER.info("[DFSpectatorUi] 成功加载头像: {}", fullPath.toAbsolutePath());
            }
        } catch (IOException e) {
            LOGGER.error("[DFSpectatorUi] 加载头像失败: {}", DEFAULT_AVATAR_PATH, e);
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
        LOGGER.info("[DFSpectatorUi] HUD显示已禁用");
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
                
                LOGGER.info("[DFSpectatorUi] 成功加载外部图片: {} ({}x{})", fullPath.toAbsolutePath(), imageWidth, imageHeight);
            }
        } catch (IOException e) {
            LOGGER.error("[DFSpectatorUi] 加载外部图片失败: {}", imagePath, e);
        }
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
        
        // 调试信息
        LOGGER.debug("[DFSpectatorUi] 图片尺寸: {}x{}, 缩放比例: {}, 绘制尺寸: {}x{}", 
                    imageWidth, imageHeight, scale, drawWidth, drawHeight);

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
        if (avatarTexture != null) {
            int avatarX = frameX + AVATAR_MARGIN;
            int avatarY = bottomContainerY + (BOTTOM_CONTAINER_HEIGHT - AVATAR_SIZE) / 2; // 垂直居中
            
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
        if (playerName != null && !playerName.isEmpty()) {
            int textX = frameX + AVATAR_MARGIN + AVATAR_SIZE + 4; // 头像右侧4像素间距
            int textY = bottomContainerY + (BOTTOM_CONTAINER_HEIGHT - 8) / 2; // 垂直居中（字体高度约8像素）
            int textColor = 0xFFFFFFFF; // 白色文字
            
            gui.drawString(Minecraft.getInstance().font, playerName, textX, textY, textColor);
        }

        RenderSystem.disableBlend();
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