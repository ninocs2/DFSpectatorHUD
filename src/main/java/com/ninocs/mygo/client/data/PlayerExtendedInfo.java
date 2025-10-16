package com.ninocs.mygo.client.data;

/**
 * 玩家扩展信息数据结构
 * 包含玩家的头像、卡片、横幅等扩展信息
 */
public class PlayerExtendedInfo {
    private String playerUUID;
    private String playerName;
    private String userNm;
    private String avatar;
    private String userCard;
    private String userBanner;
    private String mvpMusicUrl;
    private String mvpVideosUrl;
    private String userSpaceContexts;
    private String mvpMusicNm;
    private String loginIdNbr;

    public PlayerExtendedInfo() {}

    public PlayerExtendedInfo(String playerUUID, String playerName) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
    }

    // Getter和Setter方法
    public String getPlayerUUID() {
        return playerUUID;
    }

    public void setPlayerUUID(String playerUUID) {
        this.playerUUID = playerUUID;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
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

    public String getLoginIdNbr() {
        return loginIdNbr;
    }

    public void setLoginIdNbr(String loginIdNbr) {
        this.loginIdNbr = loginIdNbr;
    }

    // 便捷方法
    public boolean hasAvatar() {
        return avatar != null && !avatar.trim().isEmpty();
    }

    public boolean hasUserCard() {
        return userCard != null && !userCard.trim().isEmpty();
    }

    public boolean hasUserBanner() {
        return userBanner != null && !userBanner.trim().isEmpty();
    }

    public boolean hasMvpMusic() {
        return mvpMusicUrl != null && !mvpMusicUrl.trim().isEmpty();
    }

    public boolean hasMvpVideo() {
        return mvpVideosUrl != null && !mvpVideosUrl.trim().isEmpty();
    }

    @Override
    public String toString() {
        return "PlayerExtendedInfo{" +
                "playerUUID='" + playerUUID + '\'' +
                ", playerName='" + playerName + '\'' +
                ", userNm='" + userNm + '\'' +
                ", hasAvatar=" + hasAvatar() +
                ", hasUserCard=" + hasUserCard() +
                ", hasUserBanner=" + hasUserBanner() +
                '}';
    }
}