package com.umc.nuvibe.global.service;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ImageProcessingService {

    private static final Pattern FILE_NAME_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                    + "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
                    + "\\.(?i:jpg|jpeg|png|gif)"
    );

    private final S3Service s3Service;
    private final ImageThumbnailGenerator thumbnailGenerator;

    public ProcessingResult process(String rawObjectKey)
            throws IOException {

        String fileName = extractFileName(rawObjectKey);

        // 저장 경로는 입력 파일명으로 결정
        String originalKey = "original/" + fileName;
        String baseName = fileName.substring(
                0, fileName.lastIndexOf('.')
        );
        String thumbnailKey = "thumbnail/" + baseName + ".png";

        // 다운로드하고 검증 및 썸네일 생성
        byte[] originalBytes = s3Service.downloadBytes(rawObjectKey);
        byte[] thumbnailBytes =
                thumbnailGenerator.generate(originalBytes);

        // 원본은 변환하지 않고 그대로 저장
        s3Service.uploadBytes(
                originalKey,
                originalBytes,
                originalContentType(fileName)
        );

        s3Service.uploadBytes(
                thumbnailKey,
                thumbnailBytes,
                "image/png"
        );

        return new ProcessingResult(
                fileName, originalKey, thumbnailKey
        );
    }

    private String extractFileName(String rawObjectKey) {
        if (rawObjectKey == null || !rawObjectKey.startsWith("raw/")) {
            throw new IllegalArgumentException(
                    "raw/ 경로의 이미지가 아닙니다."
            );
        }

        String fileName = rawObjectKey.substring("raw/".length());

        if (!FILE_NAME_PATTERN.matcher(fileName).matches()) {
            throw new IllegalArgumentException(
                    "허용되지 않는 이미지 파일명입니다."
            );
        }

        return fileName;
    }

    private String originalContentType(String fileName) {
        String extension = fileName.substring(
                fileName.lastIndexOf('.') + 1
        ).toLowerCase(Locale.ROOT);

        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            default -> throw new IllegalArgumentException(
                    "허용되지 않는 확장자입니다."
            );
        };
    }

    public record ProcessingResult(
            String fileName,
            String originalKey,
            String thumbnailKey
    ) {
    }
}
