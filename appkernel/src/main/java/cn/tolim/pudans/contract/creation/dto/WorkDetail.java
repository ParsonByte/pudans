package cn.tolim.pudans.contract.creation.dto;

import cn.tolim.pudans.contract.creation.Work;

public record WorkDetail(
        Work work,
        long viewCount,
        long likeCount,
        long favoriteCount,
        long shareCount,
        long remixCount,
        boolean liked,
        boolean favorited,
        boolean creatorFollowed) {}
