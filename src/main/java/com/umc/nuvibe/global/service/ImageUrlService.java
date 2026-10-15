package com.umc.nuvibe.global.service;

import com.umc.nuvibe.domain.image.entity.Image;
import com.umc.nuvibe.domain.image.vo.ImageStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ImageUrlService {

    private final S3Service s3Service;

    // 원본 조회용 url 발급
    public String getOriginalUrl(Image image) {
        if (image == null || image.getStatus() != ImageStatus.ACTIVE) {
            return null;
        }

        return createUrl(image.getOriginalObjectKey());
    }

    // 썸네일 객체키가 없으면 원본으로 대체
    public String getThumbnailUrl(Image image) {
        if (image == null || image.getStatus() != ImageStatus.ACTIVE) {
            return null;
        }

        String objectKey = image.getThumbnailObjectKey();
        if (objectKey == null || objectKey.isBlank()) {
            objectKey = image.getOriginalObjectKey();
        }

        return createUrl(objectKey);
    }

    private String createUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }

        return s3Service.createPresignedGetUrl(objectKey);
    }
}
