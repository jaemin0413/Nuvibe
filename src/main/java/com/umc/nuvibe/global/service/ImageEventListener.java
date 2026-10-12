package com.umc.nuvibe.global.service;

import com.umc.nuvibe.domain.image.dto.response.MinioEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class ImageEventListener {

    // 원본 다운로드, 썸네일 생성 및 저장
    private final ImageProcessingService imageProcessingService;

    // 처리 결과의 객체 키 저장 및 ACTIVE 상태 전환
    private final ImageProcessingCompletionService completionService;

    // 설정 파일에서 처리 대상 버킷명 가져오기
    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

    // 지정한 큐에서 이벤트 수신, true일 때 수신 시작
    @RabbitListener(
            queues = "${app.image-processing.queue}",
            autoStartup = "${app.image-processing.enabled:false}"
    )
    public void receiveMessage(MinioEventDto event) throws IOException {
        // 이벤트 목록이 없으면 실패 처리
        if (event == null
                || event.records() == null
                || event.records().isEmpty()) {
            throw new IllegalArgumentException("이벤트 목록이 비어 있습니다.");
        }

        // 메시지에 포함된 이벤트들을 하나씩 처리
        for (MinioEventDto.EventRecord record : event.records()) {
            processRecord(record);
        }
    }

    private void processRecord(MinioEventDto.EventRecord record)
            throws IOException {

        // 이벤트 종류가 있는지 확인
        if (record == null || record.eventName() == null) {
            throw new IllegalArgumentException("이벤트 정보가 없습니다.");
        }

        // 객체 생성 이벤트만 처리
        if (!record.eventName().startsWith("s3:ObjectCreated:")) {
            return;
        }

        // 처리에 필요한 버킷 및 객체 정보 확인
        if (record.s3() == null
                || record.s3().bucket() == null
                || record.s3().object() == null
                || record.s3().object().key() == null) {
            throw new IllegalArgumentException(
                    "버킷 또는 객체 정보가 없습니다."
            );
        }

        // 설정된 버킷에서 발생한 이벤트인지 확인
        if (!bucket.equals(record.s3().bucket().name())) {
            throw new IllegalArgumentException("예상한 버킷이 아닙니다.");
        }

        // URL 인코딩된 객체 키를 원래 경로로 복원
        String objectKey = URLDecoder.decode(
                record.s3().object().key(),
                StandardCharsets.UTF_8
        );

        // raw/ 경로만 처리하고 점검용 객체는 제외
        if (!objectKey.startsWith("raw/")
                || objectKey.startsWith("raw/_checks/")) {
            return;
        }

        var result = imageProcessingService.process(objectKey);
        completionService.complete(result);

        log.info("이미지 처리 및 DB 반영 완료: fileName={}",
                result.fileName());
    }
}
