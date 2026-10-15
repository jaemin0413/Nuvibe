package com.umc.nuvibe.domain.tribe.dto.internal;

import com.umc.nuvibe.domain.image.entity.Image;

import java.time.LocalDateTime;

public record ScrapedImageRow(
        Long scrapImageId,
        Image image,
        LocalDateTime createdAt,
        Long chatId,
        Long chatSenderId,
        String chatSenderNickname
) {
}
