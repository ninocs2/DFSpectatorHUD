package com.ninocs.mygo.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SHA256工具类
 * 用于计算文件的SHA256哈希值和验证文件完整性
 */
public class SHA256 {
    private static final Logger logger = Logger.getLogger(SHA256.class.getName());
    
    // SHA256文件名的正则表达式（64位十六进制字符）
    private static final Pattern SHA256_PATTERN = Pattern.compile("([a-fA-F0-9]{64})");
    
    /**
     * 计算字符串的SHA256哈希值
     * @param input 输入字符串
     * @return SHA256哈希值（小写十六进制字符串）
     */
    public static String calculateSHA256(String input) {
        if (input == null) {
            return null;
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes("UTF-8"));
            return bytesToHex(hashBytes);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to calculate SHA256 for string", e);
            return null;
        }
    }
    
    /**
     * 计算文件的SHA256哈希值
     * @param filePath 文件路径
     * @return SHA256哈希值（小写十六进制字符串），失败返回null
     */
    public static String calculateFileSHA256(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            logger.log(Level.WARNING, "File path is null or empty");
            return null;
        }
        
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            logger.log(Level.WARNING, "File does not exist: " + filePath);
            return null;
        }
        
        return calculateFileSHA256(path);
    }
    
    /**
     * 计算文件的SHA256哈希值
     * @param filePath 文件路径对象
     * @return SHA256哈希值（小写十六进制字符串），失败返回null
     */
    public static String calculateFileSHA256(Path filePath) {
        if (filePath == null || !Files.exists(filePath)) {
            logger.log(Level.WARNING, "File path is null or file does not exist");
            return null;
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            try (InputStream inputStream = new FileInputStream(filePath.toFile())) {
                byte[] buffer = new byte[8192]; // 8KB缓冲区
                int bytesRead;
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
                
                byte[] hashBytes = digest.digest();
                String sha256 = bytesToHex(hashBytes);
                
                logger.log(Level.FINE, "Calculated SHA256 for file " + filePath + ": " + sha256);
                return sha256;
            }
            
        } catch (NoSuchAlgorithmException e) {
            logger.log(Level.SEVERE, "SHA-256 algorithm not available", e);
            return null;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "IO error while calculating file SHA256: " + filePath, e);
            return null;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error while calculating file SHA256: " + filePath, e);
            return null;
        }
    }
    
    /**
     * 从URL中提取SHA256文件名
     * @param url 包含SHA256文件名的URL
     * @return 提取的SHA256值（小写），未找到返回null
     */
    public static String extractSHA256FromUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            logger.log(Level.WARNING, "URL is null or empty");
            return null;
        }
        
        try {
            Matcher matcher = SHA256_PATTERN.matcher(url);
            if (matcher.find()) {
                String sha256 = matcher.group(1).toLowerCase();
                logger.log(Level.FINE, "Extracted SHA256 from URL: " + sha256);
                return sha256;
            } else {
                logger.log(Level.WARNING, "No SHA256 pattern found in URL: " + url);
                return null;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error extracting SHA256 from URL: " + url, e);
            return null;
        }
    }
    
    /**
     * 验证文件的SHA256是否与期望值匹配
     * @param filePath 文件路径
     * @param expectedSHA256 期望的SHA256值
     * @return true如果匹配，false如果不匹配或验证失败
     */
    public static boolean verifyFileSHA256(String filePath, String expectedSHA256) {
        if (filePath == null || expectedSHA256 == null) {
            logger.log(Level.WARNING, "File path or expected SHA256 is null");
            return false;
        }
        
        String actualSHA256 = calculateFileSHA256(filePath);
        if (actualSHA256 == null) {
            logger.log(Level.WARNING, "Failed to calculate actual SHA256 for file: " + filePath);
            return false;
        }
        
        boolean matches = actualSHA256.equalsIgnoreCase(expectedSHA256.trim());
        
        if (matches) {

        } else {
            logger.log(Level.WARNING, "SHA256 verification failed for file: " + filePath + 
                      ". Expected: " + expectedSHA256 + ", Actual: " + actualSHA256);
        }
        
        return matches;
    }
    
    /**
     * 验证文件的SHA256是否与期望值匹配
     * @param filePath 文件路径对象
     * @param expectedSHA256 期望的SHA256值
     * @return true如果匹配，false如果不匹配或验证失败
     */
    public static boolean verifyFileSHA256(Path filePath, String expectedSHA256) {
        if (filePath == null) {
            return false;
        }
        return verifyFileSHA256(filePath.toString(), expectedSHA256);
    }
    
    /**
     * 验证URL对应的文件SHA256是否正确
     * @param url 包含SHA256的URL
     * @param filePath 本地文件路径
     * @return true如果SHA256匹配，false如果不匹配或验证失败
     */
    public static boolean verifyUrlFileSHA256(String url, String filePath) {
        String expectedSHA256 = extractSHA256FromUrl(url);
        if (expectedSHA256 == null) {
            logger.log(Level.WARNING, "Could not extract SHA256 from URL: " + url);
            return false;
        }
        
        return verifyFileSHA256(filePath, expectedSHA256);
    }
    
    /**
     * 验证URL对应的文件SHA256是否正确
     * @param url 包含SHA256的URL
     * @param filePath 本地文件路径对象
     * @return true如果SHA256匹配，false如果不匹配或验证失败
     */
    public static boolean verifyUrlFileSHA256(String url, Path filePath) {
        if (filePath == null) {
            return false;
        }
        return verifyUrlFileSHA256(url, filePath.toString());
    }
    
    /**
     * 检查文件是否需要重新下载（基于SHA256验证）
     * @param url 包含SHA256的URL
     * @param filePath 本地文件路径
     * @return true如果需要重新下载，false如果文件有效
     */
    public static boolean needsRedownload(String url, String filePath) {
        if (url == null || filePath == null) {
            return true;
        }
        
        // 检查文件是否存在
        if (!Files.exists(Paths.get(filePath))) {
            logger.log(Level.INFO, "File does not exist, needs download: " + filePath);
            return true;
        }
        
        // 验证SHA256
        boolean isValid = verifyUrlFileSHA256(url, filePath);
        if (!isValid) {
            logger.log(Level.INFO, "File SHA256 verification failed, needs redownload: " + filePath);
        }
        
        return !isValid;
    }
    
    /**
     * 检查文件是否需要重新下载（基于SHA256验证）
     * @param url 包含SHA256的URL
     * @param filePath 本地文件路径对象
     * @return true如果需要重新下载，false如果文件有效
     */
    public static boolean needsRedownload(String url, Path filePath) {
        if (filePath == null) {
            return true;
        }
        return needsRedownload(url, filePath.toString());
    }
    
    /**
     * 将字节数组转换为十六进制字符串
     * @param bytes 字节数组
     * @return 十六进制字符串（小写）
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    /**
     * 验证SHA256字符串格式是否正确
     * @param sha256 SHA256字符串
     * @return true如果格式正确，false如果格式错误
     */
    public static boolean isValidSHA256(String sha256) {
        if (sha256 == null) {
            return false;
        }
        
        String trimmed = sha256.trim();
        return trimmed.length() == 64 && SHA256_PATTERN.matcher(trimmed).matches();
    }
}
