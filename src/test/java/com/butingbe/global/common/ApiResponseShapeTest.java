package com.butingbe.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.support.AbstractContainerTest;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 성공 응답을 {@link ApiResponse} 로 감싸는지 본다.
 *
 * <p>저장소 규칙은 성공·실패 모두 ApiResponse 로 감싸라고 한다. 실제로는 컨트롤러마다 갈려 있어, 클라이언트가 성공과 실패의 JSON 모양이 다른 API 를
 * 상대한다.
 *
 * <p>한 번에 다 바꾸려면 클라이언트 배포와 순서를 맞춰야 해서 지금은 못 한다. 대신 지금 상태를 고정해 <b>더 늘지 않게</b> 막는다. 아래 목록에 없는 핸들러가
 * 감싸지 않은 채로 들어오면 이 테스트가 깨진다.
 *
 * <p>목록에서 지우는 방향으로만 고친다. 새 항목을 추가하려면 그 전에 왜 감쌀 수 없는지 설명이 필요하다.
 */
class ApiResponseShapeTest extends AbstractContainerTest {

  /** 통일 전부터 감싸지 않던 핸들러. 줄어들기만 해야 한다. */
  private static final Set<String> KNOWN_UNWRAPPED =
      new TreeSet<>(
          List.of(
              "FileController.upload",
              "PlaceController.getPlaceDetail",
              "PlaceController.searchFestivals",
              "PlaceController.searchPlaces",
              "PlaceController.searchPlacesByKeyword",
              "PlaceController.searchPlacesByLocation",
              "PlanController.createPlanPlace",
              "PlanController.getPlanPlaces",
              "PlanController.updatePlanPlace",
              "PlanController.updatePlanPlacePlace",
              "PlanController.updatePlanPlaceSequence",
              "PlanController.updatePlanPlaceVisited",
              "PlanPlaceReviewController.createPlaceReview",
              "PlanPlaceReviewController.getPlaceReview",
              "PlanPlaceReviewController.updatePlaceReview",
              "PublicPlaceReviewController.getPlaceReviewSummary",
              "PublicPlaceTravelRecordController.getTravelRecordsByPlace",
              "PublicTravelRecordController.bookmarkTravelRecord",
              "PublicTravelRecordController.cloneToTravel",
              "PublicTravelRecordController.createComment",
              "PublicTravelRecordController.getComments",
              "PublicTravelRecordController.getLatestFeed",
              "PublicTravelRecordController.getMyBookmarkedRecords",
              "PublicTravelRecordController.getMyRecord",
              "PublicTravelRecordController.getMyRecords",
              "PublicTravelRecordController.getPublished",
              "PublicTravelRecordController.hideMyRecord",
              "PublicTravelRecordController.likeTravelRecord",
              "PublicTravelRecordController.republishMyRecord",
              "PublicTravelRecordController.updateComment",
              "PublicTravelRecordController.updateMyRecord",
              "StorageLocationController.search",
              "TravelController.createPlan",
              "TravelController.createTravel",
              "TravelController.generateAiPlans",
              "TravelController.getTravelPlans",
              "TravelController.updateTravelStatus",
              "TravelExpenseController.createEqualExpense",
              "TravelExpenseController.getExpense",
              "TravelExpenseController.getExpenseSummary",
              "TravelExpenseController.getExpenses",
              "TravelExpenseController.updateExpense",
              "TravelRebootController.reboot",
              "TravelRecordController.createDraft",
              "TravelRecordController.getDraft",
              "TravelRecordController.publish",
              "TravelRecordController.updateDraft",
              "TravelRouteController.applyOptimizedOrder",
              "TravelRouteController.generateAlternativeRoute",
              "TravelRouteController.getPlanRoute",
              "TravelRouteController.optimizeVisitOrder",
              "TravelRouteOptimizeController.optimizeTravelRoute",
              "TravelSettlementController.confirmSettlement",
              "TravelSettlementController.getSettlement",
              "TravelSurveyController.getProfile",
              "TravelSurveyController.upsertProfile",
              "TravelTeamController.createInviteLink",
              "UserController.getMyProfile",
              "UserController.updateMyProfile"));

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  @Test
  @DisplayName("새로 추가되는 핸들러는 성공 응답을 ApiResponse 로 감싼다")
  void newHandlersWrapSuccessResponses() {
    List<String> unwrapped = new ArrayList<>();

    handlerMapping
        .getHandlerMethods()
        .forEach(
            (info, handlerMethod) -> {
              if (!handlerMethod.getBeanType().getPackageName().startsWith("com.butingbe.domain")) {
                return;
              }
              if (!returnsApiResponse(handlerMethod)) {
                unwrapped.add(
                    handlerMethod.getBeanType().getSimpleName()
                        + "."
                        + handlerMethod.getMethod().getName());
              }
            });

    // 목록을 못 읽으면 아무것도 검사하지 않고 통과한다. 그 상태를 성공으로 오해하지 않도록 먼저 막는다.
    assertThat(handlerMapping.getHandlerMethods()).hasSizeGreaterThan(100);

    assertThat(new TreeSet<>(unwrapped).stream().filter(h -> !KNOWN_UNWRAPPED.contains(h)).toList())
        .describedAs("성공 응답을 ApiResponse 로 감싸지 않은 새 핸들러")
        .isEmpty();
  }

  @Test
  @DisplayName("목록에 적힌 핸들러가 이미 고쳐졌다면 목록에서 지운다")
  void baselineDoesNotRotStale() {
    Set<String> stillUnwrapped = new TreeSet<>();

    handlerMapping
        .getHandlerMethods()
        .forEach(
            (info, handlerMethod) -> {
              if (!returnsApiResponse(handlerMethod)) {
                stillUnwrapped.add(
                    handlerMethod.getBeanType().getSimpleName()
                        + "."
                        + handlerMethod.getMethod().getName());
              }
            });

    assertThat(KNOWN_UNWRAPPED.stream().filter(h -> !stillUnwrapped.contains(h)).toList())
        .describedAs("이미 ApiResponse 로 감쌌는데 목록에 남아 있는 핸들러. 목록에서 지워야 다음 사람이 헷갈리지 않는다.")
        .isEmpty();
  }

  /** void 와 ResponseEntity<Void> 는 본문이 없으므로 감쌀 것이 없다. */
  private boolean returnsApiResponse(HandlerMethod handlerMethod) {
    Type returnType = handlerMethod.getMethod().getGenericReturnType();
    if (returnType == void.class) {
      return true;
    }
    if (!(returnType instanceof ParameterizedType parameterized)
        || parameterized.getRawType() != ResponseEntity.class) {
      return false;
    }
    Type body = parameterized.getActualTypeArguments()[0];
    if (body == Void.class) {
      return true;
    }
    Type raw = body instanceof ParameterizedType nested ? nested.getRawType() : body;
    return raw == ApiResponse.class;
  }
}
