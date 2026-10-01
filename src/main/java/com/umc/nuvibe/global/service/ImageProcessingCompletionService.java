package com.umc.nuvibe.global.service;


import com.umc.nuvibe.domain.image.entity.Image;
import com.umc.nuvibe.domain.image.repository.ImageRepository;
import com.umc.nuvibe.global.apiPayLoad.error.ImageErrorCode;
import com.umc.nuvibe.global.apiPayLoad.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ImageProcessingCompletionService {

    // MInIO 저장과 db 저장을 다른 작업으로 분리하기 위함
    private final ImageRepository imageRepository;

    @Transactional
    public void complete(
            ImageProcessingService.ProcessingResult result
    ) {
        if (result == null
                || result.fileName() == null
                || result.fileName().isBlank()) {
            throw new IllegalArgumentException(
                    "이미지 처리 결과가 올바르지 않습니다."
            );
        }

        Image image = imageRepository.findByFileName(result.fileName())
                .orElseThrow(() -> new BusinessException(
                        ImageErrorCode.IMAGE_NOT_FOUND
                ));

        image.updateObjectKeys(
                result.originalKey(),
                result.thumbnailKey()
        );

        image.activate();
    }
}
