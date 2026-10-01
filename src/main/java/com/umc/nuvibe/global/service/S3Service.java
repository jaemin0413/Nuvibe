package com.umc.nuvibe.global.service;

import com.umc.nuvibe.domain.image.dto.request.PreSignedUrlReq;
import com.umc.nuvibe.domain.image.dto.response.PreSignedUrlRes;
import com.umc.nuvibe.global.apiPayLoad.error.ImageErrorCode;
import com.umc.nuvibe.global.apiPayLoad.exception.BusinessException;
import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service {

    private final S3Template s3Template;
    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

    // 이미지 확장자 화이트리스트
    private static final List<String> IMAGE_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "gif");

    // pre-signed-url 발급
    public PreSignedUrlRes getPreSignedUrl(PreSignedUrlReq request, String prefix) {

        // 확장자 검증
        validateFileExtension(request.originalFileName());

        // 저장 경로 및 파일명 생성
        String fileName = createPath(prefix, request.originalFileName());

        //S3 요청 객체 생성 (PUT 메서드)
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(fileName)
                .build();

        // preSigned URL 요청 설정
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .putObjectRequest(putObjectRequest)
                .build();

        //url 발급
        PresignedPutObjectRequest presignedPutObjectRequest = s3Presigner.presignPutObject(presignRequest);
        String url = presignedPutObjectRequest.url().toString();

        // url은 다 남기지 않게
        log.info("Presigned URL 발급 완료: fileName={}", fileName);

        return new PreSignedUrlRes(url, fileName);
    }

    public String upload(MultipartFile file) {

        if (file.isEmpty()) {
            throw new BusinessException(ImageErrorCode.IMAGE_IS_EMPTY);
        }

        // 파일 검증
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new BusinessException(ImageErrorCode.INVALID_FILE_EXTENSION);
        }
        validateFileExtension(originalFilename);

        // UUID로 파일명 안 겹치게 난수화
        String fileName = createFileName(originalFilename);

        // s3에 업로드
        try (InputStream inputStream = file.getInputStream()) {
            S3Resource resource = s3Template.upload(bucket, fileName, inputStream,
                    ObjectMetadata.builder().contentType(file.getContentType()).build());

            return resource.getURL().toString();

        } catch (IOException e) {
            log.error("s3 업로드 실패", e);
            throw new BusinessException(ImageErrorCode.IMAGE_UPLOAD_FAIL);
        }
    }

    // S3에서 파일 삭제
    public boolean deleteFile(String fileName) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileName)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("S3 파일 삭제 완료: {}", fileName);
            return true;
        } catch (Exception e) {
            log.error("S3 파일 삭제 실패 : {}", fileName, e);
            return false;
        }
    }



    // 파일 확장자 검증
    private void validateFileExtension(String originalFilename) {
        String extension = getFileExtension(originalFilename);
        if (!IMAGE_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new BusinessException(ImageErrorCode.INVALID_FILE_EXTENSION);
        }
    }

    // 확장자 추출
    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf(".");

        // .이 없거나 맨 앞에 있을 때
        if (dotIndex == -1 || dotIndex == 0) {
            throw new BusinessException(ImageErrorCode.INVALID_FILE_EXTENSION);
        }

        // .이 맨 끝에 있을 때
        if (dotIndex == fileName.length() - 1) {
            return "";
        }

        return fileName.substring(dotIndex + 1);
    }

    // UUID로 랜덤 파일명 생성
    private String createFileName(String originalFileName) {
        return UUID.randomUUID().toString().concat(".").concat(getFileExtension(originalFileName));
    }

    // 경로 포함 파일명 생성 로직
    private String createPath(String prefix, String originalFileName) {
        String fileName = createFileName(originalFileName);

        // prefix가 유효하면 prefix/파일명, 없으면 그냥 파일명
        if (prefix != null && !prefix.isBlank()) {
            return prefix.concat("/").concat(fileName);
        }
        return fileName;
    }



    // MinIO에 저장된 객체 내려받는 용도. 이미지 처리 전에 원본 내려받는거임
    // 파일 전체를 메모리에 올리기 때문에 일단 나중에 리팩토링 해봐야할듯
    public byte[] downloadBytes(String objectKey) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey) // 버킷 안의 경로객체임
                .build();

        return s3Client.getObjectAsBytes(request).asByteArray();
    }

    // 바이트배열을 MinIO의 지정경로에 처리 결과를 저장한다. 썸네일이랑 기타등등 object키 그대로 쓰기 때문에 리팩필요할듯?
    public void uploadBytes(
            String objectKey,
            byte[] content,
            String contentType
    ) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(content));
    }


    // 비공개 객체를 조회할 수 있는 임시 GET URL 발급
    // 객체 키를 전달하면 조회할 수 있는 url을 주는거임
    public String createPresignedGetUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("객체 키가 비어 있습니다.");
        }

        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        GetObjectPresignRequest presignRequest =
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(15))
                        .getObjectRequest(objectRequest)
                        .build();

        return s3Presigner.presignGetObject(presignRequest)
                .url()
                .toString();
    }




}