package com.butingbe.global.config;

import com.butingbe.domain.auth.repository.OpaqueTokenRepository;
import com.butingbe.domain.storage.controller.StorageLocationController;
import com.butingbe.domain.storage.repository.StorageLocationRepository;
import com.butingbe.domain.storage.service.JpaStorageLocationService;
import com.butingbe.domain.storage.service.StorageLocationService;
import com.butingbe.domain.user.controller.UserController;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.user.service.UserService;
import com.butingbe.domain.user.service.UserServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

  // ==========================================
  // 👤 USER DOMAIN REGION
  // ==========================================
  @Bean
  public UserService userService(
      UserRepository userRepository, OpaqueTokenRepository opaqueTokenRepository) {
    return new UserServiceImpl(userRepository, opaqueTokenRepository);
  }

  @Bean
  public UserController userController(UserService userService) {
    return new UserController(userService);
  }

  @Bean
  public StorageLocationService storageLocationService(StorageLocationRepository repository) {
    return new JpaStorageLocationService(repository);
  }

  @Bean
  public StorageLocationController storageLocationController(
      StorageLocationService storageLocationService) {
    return new StorageLocationController(storageLocationService);
  }
}
