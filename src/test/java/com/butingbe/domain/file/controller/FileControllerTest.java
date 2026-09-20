package com.butingbe.domain.file.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.file.dto.FileUploadResDto;
import com.butingbe.domain.file.service.FileStorageService;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

  private static final UUID UPLOADER = UUID.fromString("88888888-0000-0000-0000-000000000001");

  private MockMvc mockMvc;

  @Mock private FileStorageService fileStorageService;

  @InjectMocks private FileController fileController;

  @BeforeEach
  void setUp() {
    mockMvc =
        mockMvcWithPrincipal(new AuthenticatedUser(UPLOADER, "up@example.com", "up", List.of()));
  }

  private MockMvc mockMvcWithPrincipal(AuthenticatedUser principal) {
    return MockMvcBuilders.standaloneSetup(fileController)
        .setCustomArgumentResolvers(uploaderResolver(principal))
        .setMessageConverters(new MappingJackson2HttpMessageConverter())
        .build();
  }

  private HandlerMethodArgumentResolver uploaderResolver(AuthenticatedUser principal) {
    return new HandlerMethodArgumentResolver() {
      @Override
      public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
      }

      @Override
      public Object resolveArgument(
          MethodParameter parameter,
          ModelAndViewContainer mavContainer,
          NativeWebRequest webRequest,
          WebDataBinderFactory binderFactory) {
        return principal;
      }
    };
  }

  @Test
  @DisplayName("멀티파트 파일을 업로드하면 저장된 파일 정보를 반환한다")
  void uploadReturnsStoredFileMetadata() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, "binary-content".getBytes());
    when(fileStorageService.upload(any(), any()))
        .thenReturn(
            new FileUploadResDto(
                "uploads/photo.jpg",
                "photo.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                14L,
                "https://cdn.example.com/uploads/photo.jpg"));

    mockMvc
        .perform(multipart("/files").file(file))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fileKey").value("uploads/photo.jpg"))
        .andExpect(jsonPath("$.originalFileName").value("photo.jpg"))
        .andExpect(jsonPath("$.contentType").value(MediaType.IMAGE_JPEG_VALUE))
        .andExpect(jsonPath("$.fileSize").value(14))
        .andExpect(jsonPath("$.url").value("https://cdn.example.com/uploads/photo.jpg"));

    verify(fileStorageService).upload(any(), org.mockito.ArgumentMatchers.eq(UPLOADER));
  }

  @Test
  @DisplayName("파일 키로 삭제를 요청하면 204를 반환하고 서비스에 위임한다")
  void deleteRemovesFileAndReturnsNoContent() throws Exception {
    mockMvc
        .perform(delete("/files").param("fileKey", "uploads/photo.jpg"))
        .andExpect(status().isNoContent());

    verify(fileStorageService).delete("uploads/photo.jpg", UPLOADER);
  }

  @Test
  @DisplayName("인증 없이 삭제를 요청하면 서비스를 호출하지 않는다")
  void deleteRequiresAuthentication() {
    MockMvc anonymous = mockMvcWithPrincipal(null);

    assertThatThrownBy(
            () -> anonymous.perform(delete("/files").param("fileKey", "uploads/photo.jpg")))
        .hasRootCauseInstanceOf(UnauthenticatedException.class);

    verifyNoInteractions(fileStorageService);
  }

  @Test
  @DisplayName("id 없는 개발 관리자 토큰으로는 업로드할 수 없다")
  void uploadRejectsPrincipalWithoutId() {
    MockMvc developmentAdmin = mockMvcWithPrincipal(AuthenticatedUser.developmentAdmin());
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, "binary-content".getBytes());

    assertThatThrownBy(() -> developmentAdmin.perform(multipart("/files").file(file)))
        .hasRootCauseInstanceOf(UnauthenticatedException.class);

    verifyNoInteractions(fileStorageService);
  }
}
