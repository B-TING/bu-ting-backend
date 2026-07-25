package com.butingbe.domain.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.butingbe.domain.file.entity.FileMetadata;
import com.butingbe.domain.file.repository.FileMetadataRepository;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3FileStorageServiceTest {

  @Mock private S3Client s3Client;
  @Mock private FileMetadataRepository fileMetadataRepository;
  @Mock private S3Presigner s3Presigner;
  @Mock private PresignedGetObjectRequest presignedGetObjectRequest;

  private S3FileStorageService service;

  @BeforeEach
  void setUp() {
    service = new S3FileStorageService(s3Client, fileMetadataRepository, s3Presigner);
    ReflectionTestUtils.setField(service, "bucket", "buting-private");
    ReflectionTestUtils.setField(service, "keyPrefix", "uploads");
    ReflectionTestUtils.setField(service, "maxFileSize", 50L * 1024 * 1024);
    ReflectionTestUtils.setField(service, "presignedUrlExpiration", 3600L);
  }

  @Test
  void uploadsFileAndReturnsPresignedGetUrl() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("file", "busan.png", "image/png", new byte[] {1, 2, 3});
    when(fileMetadataRepository.save(any(FileMetadata.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
        .thenReturn(presignedGetObjectRequest);
    when(presignedGetObjectRequest.url())
        .thenReturn(URI.create("https://signed.example.com/file?signature=value").toURL());

    var response = service.upload(file);

    ArgumentCaptor<GetObjectPresignRequest> requestCaptor =
        ArgumentCaptor.forClass(GetObjectPresignRequest.class);
    verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    verify(s3Presigner).presignGetObject(requestCaptor.capture());
    assertThat(requestCaptor.getValue().signatureDuration()).isEqualTo(Duration.ofHours(1));
    assertThat(response.fileKey()).startsWith("uploads/images/");
    assertThat(response.url()).isEqualTo("https://signed.example.com/file?signature=value");
  }

  @Test
  void rejectsFileExceedingSizeLimitBeforeS3Upload() {
    ReflectionTestUtils.setField(service, "maxFileSize", 2L);
    MockMultipartFile file =
        new MockMultipartFile("file", "busan.png", "image/png", new byte[] {1, 2, 3});

    assertThatThrownBy(() -> service.upload(file))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("파일 크기 제한을 초과했습니다.");

    verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }
}
