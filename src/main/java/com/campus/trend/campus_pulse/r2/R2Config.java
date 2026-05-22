package com.campus.trend.campus_pulse.r2;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class R2Config {

    private final R2Properties props;

    @Bean(destroyMethod = "close")
    public S3Client r2S3Client() {
        validate();
        log.info("初始化 R2 S3Client, endpoint={}, bucket={}", props.getEndpoint(), props.getBucket());
        return S3Client.builder()
                .endpointOverride(URI.create(props.getEndpoint()))
                .region(Region.of(props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKeyId(), props.getSecretAccessKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .build())
                .build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner r2S3Presigner() {
        validate();
        return S3Presigner.builder()
                .endpointOverride(URI.create(props.getEndpoint()))
                .region(Region.of(props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKeyId(), props.getSecretAccessKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .build())
                .build();
    }

    private void validate() {
        if (props.getEndpoint() == null || props.getEndpoint().isBlank()) {
            throw new IllegalStateException("campus.r2.endpoint 未配置（环境变量 R2_ENDPOINT）");
        }
        if (props.getAccessKeyId() == null || props.getAccessKeyId().isBlank()) {
            throw new IllegalStateException("campus.r2.access-key-id 未配置（环境变量 R2_ACCESS_KEY_ID）");
        }
        if (props.getSecretAccessKey() == null || props.getSecretAccessKey().isBlank()) {
            throw new IllegalStateException("campus.r2.secret-access-key 未配置（环境变量 R2_SECRET_ACCESS_KEY）");
        }
        if (props.getBucket() == null || props.getBucket().isBlank()) {
            throw new IllegalStateException("campus.r2.bucket 未配置（环境变量 R2_BUCKET）");
        }
    }
}
