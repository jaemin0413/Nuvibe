package com.umc.nuvibe.domain.image.dto.response;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MinioEventDto(
        @JsonProperty("Records")
        List<EventRecord> records // 이벤트 목록
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EventRecord(
            String eventName, // 이벤트 종류
            S3Info s3
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record S3Info(
            BucketInfo bucket,
            ObjectInfo object
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BucketInfo(
            String name //버킷명
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ObjectInfo(
            String key, // 객체경로
            Long size
    ) {
    }
}