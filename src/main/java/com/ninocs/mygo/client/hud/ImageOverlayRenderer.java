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
    
    // 容器尺寸 (9:16比例)
    private static final int FRAME_WIDTH = 108;
    private static final int FRAME_HEIGHT = 192;
    
    // 默认图片路径（相对于游戏运行目录）
    private static final String DEFAULT_IMAGE_PATH = "MCGO/cache/card/523f8e5883a34cee54304e35ba7208cbf3c610de891f7646f4eb516ca40141af.jpg";

    /**
     * 启用HUD显示
     */
    public static void enableHud() {
        hudEnabled = true;
        loadExternalImage(DEFAULT_IMAGE_PATH);
        LOGGER.info("[DFSpectatorUi] HUD显示已启用");
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
                dynamicTexture = new ResourceLocation("dfspectatorui", "dynamic_overlay");
                
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

        // 容器居中计算
        int frameX = screenWidth - FRAME_WIDTH - 1; // 右边距离1px
        int frameY = (screenHeight / 2) - (FRAME_HEIGHT / 2); // 垂直居中

        // 容器（半透明黑色背景，无边框）
        int bgColor = 0x80000000; // 半透明黑色背景
        gui.fill(frameX, frameY, frameX + FRAME_WIDTH, frameY + FRAME_HEIGHT, bgColor);

        // 图片缩放比例（自适应容器最大宽度高度，保持宽高比，无内边距）
        float scaleX = (float) FRAME_WIDTH / imageWidth;
        float scaleY = (float) FRAME_HEIGHT / imageHeight;
        float scale = Math.min(scaleX, scaleY); // 选择较小的缩放比例以保持宽高比
        
        int drawWidth = (int) (imageWidth * scale);
        int drawHeight = (int) (imageHeight * scale);

        int imgX = frameX + (FRAME_WIDTH - drawWidth) / 2;
        int imgY = frameY + (FRAME_HEIGHT - drawHeight) / 2;
        
        // 调试信息
        LOGGER.debug("[DFSpectatorUi] 图片尺寸: {}x{}, 缩放比例: {}, 绘制尺寸: {}x{}", 
                    imageWidth, imageHeight, scale, drawWidth, drawHeight);

        // 禁用模糊过滤，确保原分辨率绘制
        mc.getTextureManager().getTexture(dynamicTexture).setFilter(false, false);

        // 开启混合以支持透明像素
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 绘制图片 - 修复纹理坐标映射
        gui.blit(
                dynamicTexture,
                imgX, imgY,
                0, 0,
                drawWidth, drawHeight,
                drawWidth, drawHeight
        );

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