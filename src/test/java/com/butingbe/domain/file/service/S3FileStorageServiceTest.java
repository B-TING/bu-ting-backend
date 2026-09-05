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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
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

  @Test
  void createsPresignedGetUrlForRegisteredFileKey() throws Exception {
    String fileKey = "uploads/images/busan.png";
    when(fileMetadataRepository.findByObjectKey(fileKey))
        .thenReturn(Optional.of(FileMetadata.builder().objectKey(fileKey).build()));
    when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
        .thenReturn(presignedGetObjectRequest);
    when(presignedGetObjectRequest.url())
        .thenReturn(URI.create("https://signed.example.com/busan.png?signature=new").toURL());

    String url = service.getPresignedUrl(fileKey);

    assertThat(url).isEqualTo("https://signed.example.com/busan.png?signature=new");
    verify(fileMetadataRepository).findByObjectKey(fileKey);
  }

  @Test
  @DisplayName("동영상은 videos 경로에 업로드하고 mediaType을 VIDEO로 저장한다")
  void uploadsVideoUnderVideosPrefix() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("file", "clip.mp4", "video/mp4", new byte[] {1, 2, 3});
    when(fileMetadataRepository.save(any(FileMetadata.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
        .thenReturn(presignedGetObjectRequest);
    when(presignedGetObjectRequest.url())
        .thenReturn(URI.create("https://signed.example.com/clip?signature=value").toURL());

    var response = service.upload(file);

    ArgumentCaptor<FileMetadata> metadataCaptor = ArgumentCaptor.forClass(FileMetadata.class);
    verify(fileMetadataRepository).save(metadataCaptor.capture());
    assertThat(metadataCaptor.getValue().getMediaType()).isEqualTo("VIDEO");
    assertThat(response.fileKey()).startsWith("uploads/videos/").endsWith(".mp4");
    assertThat(response.contentType()).isEqualTo("video/mp4");
  }

  @Test
  @DisplayName("메타데이터 저장이 실패하면 이미 올린 S3 객체를 지우고 예외를 그대로 전파한다")
  void deletesUploadedObjectWhenMetadataSaveFails() {
    MockMultipartFile file =
        new MockMultipartFile("file", "busan.png", "image/png", new byte[] {1, 2, 3});
    when(fileMetadataRepository.save(any(FileMetadata.class)))
        .thenThrow(new IllegalStateException("db down"));

    assertThatThrownBy(() -> service.upload(file))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("db down");

    verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    verify(s3Presigner, never()).presignGetObject(any(GetObjectPresignRequest.class));
  }

  @Test
  @DisplayName("빈 파일은 업로드하지 않는다")
  void rejectsEmptyFile() {
    MockMultipartFile file = new MockMultipartFile("file", "busan.png", "image/png", new byte[0]);

    assertThatThrownBy(() -> service.upload(file))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("파일이 비어 있습니다.");

    verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  @DisplayName("허용하지 않는 형식은 업로드하지 않는다")
  void rejectsUnsupportedContentType() {
    MockMultipartFile file =
        new MockMultipartFile("file", "malware.exe", "application/octet-stream", new byte[] {1});

    assertThatThrownBy(() -> service.upload(file))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("지원하지 않는 파일 형식입니다.");

    verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  @DisplayName("파일을 삭제하면 S3 객체와 메타데이터를 함께 지운다")
  void deleteRemovesObjectAndMetadata() {
    String fileKey = "uploads/images/busan.png";
    FileMetadata metadata = FileMetadata.builder().objectKey(fileKey).build();
    when(fileMetadataRepository.findByObjectKey(fileKey)).thenReturn(Optional.of(metadata));

    service.delete(fileKey);

    verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    verify(fileMetadataRepository).delete(metadata);
  }

  @Test
  @DisplayName("메타데이터가 없어도 S3 객체 삭제는 수행한다")
  void deleteToleratesMissingMetadata() {
    String fileKey = "uploads/images/busan.png";
    when(fileMetadataRepository.findByObjectKey(fileKey)).thenReturn(Optional.empty());

    service.delete(fileKey);

    verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    verify(fileMetadataRepository, never()).delete(any(FileMetadata.class));
  }

  @ParameterizedTest
  @DisplayName("상위 경로 탈출이나 빈 값 같은 위험한 파일 키는 거부한다")
  @ValueSource(strings = {"", "   ", "../secret.png", "/etc/passwd", "uploads/../../secret.png"})
  void rejectsUnsafeFileKey(String fileKey) {
    assertThatThrownBy(() -> service.delete(fileKey))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("유효하지 않은 파일 키입니다.");

    verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
  }

  @Test
  @DisplayName("등록되지 않은 파일 키로는 presigned URL을 만들지 않는다")
  void rejectsPresignedUrlForUnregisteredFileKey() {
    String fileKey = "uploads/images/unknown.png";
    when(fileMetadataRepository.findByObjectKey(fileKey)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getPresignedUrl(fileKey))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("등록되지 않은 파일입니다.");
  }
}
