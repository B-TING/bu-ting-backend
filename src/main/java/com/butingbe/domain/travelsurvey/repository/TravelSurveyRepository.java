package com.butingbe.domain.travelsurvey.repository;

import com.butingbe.domain.travelsurvey.entity.TravelSurvey;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelSurveyRepository extends JpaRepository<TravelSurvey, UUID> {}
