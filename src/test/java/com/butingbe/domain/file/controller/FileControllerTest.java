package com.butingbe.domain.file.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.file.dto.FileUploadResDto;
import com.butingbe.domain.file.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

  private MockMvc mockMvc;

  @Mock private FileStorageService fileStorageService;

  @InjectMocks private FileController fileController;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(fileController)
            .setMessageConverters(new MappingJackson2HttpMessageConverter())
            .build();
  }

  @Test
  @DisplayName("멀티파트 파일을 업로드하면 저장된 파일 정보를 반환한다")
  void uploadReturnsStoredFileMetadata() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, "binary-content".getBytes());
    when(fileStorageService.upload(any()))
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
  }

  @Test
  @DisplayName("파일 키로 삭제를 요청하면 204를 반환하고 서비스에 위임한다")
  void deleteRemovesFileAndReturnsNoContent() throws Exception {
    mockMvc
        .perform(delete("/files").param("fileKey", "uploads/photo.jpg"))
        .andExpect(status().isNoContent());

    verify(fileStorageService).delete("uploads/photo.jpg");
  }
}
