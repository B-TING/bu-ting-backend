package com.butingbe.domain.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.chat.entity.LocalChatroom;
import com.butingbe.support.AbstractContainerTest;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 인원수 갱신이 실제로 원자적인지 본다.
 *
 * <p>엔티티를 읽어 +1 해 저장하던 방식은 이 테스트에서 값이 유실된다. 트랜잭션이 각각 독립해야 경쟁이 일어나므로 클래스에 {@code @Transactional} 을
 * 붙이지 않는다. 대신 만든 방을 직접 지운다.
 */
class LocalChatroomConcurrencyTest extends AbstractContainerTest {

  private static final int THREADS = 16;

  @Autowired private LocalChatroomRepository localChatroomRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  @DisplayName("동시에 입장해도 인원수가 유실되지 않는다")
  void increaseCurrentMembersDoesNotLoseUpdates() throws Exception {
    UUID roomId = saveRoom(THREADS);

    int succeeded = runConcurrently(() -> localChatroomRepository.increaseCurrentMembers(roomId));

    assertThat(succeeded).isEqualTo(THREADS);
    assertThat(localChatroomRepository.findCurrentMembers(roomId)).contains(THREADS);

    localChatroomRepository.deleteById(roomId);
  }

  @Test
  @DisplayName("정원보다 많이 동시에 들어와도 정원을 넘기지 않는다")
  void increaseCurrentMembersStopsAtCapacity() throws Exception {
    int capacity = 5;
    UUID roomId = saveRoom(capacity);

    int succeeded = runConcurrently(() -> localChatroomRepository.increaseCurrentMembers(roomId));

    // 갱신에 성공한 수가 곧 입장에 성공한 수다. 나머지는 where 절에 걸려 0을 받는다.
    assertThat(succeeded).isEqualTo(capacity);
    assertThat(localChatroomRepository.findCurrentMembers(roomId)).contains(capacity);

    localChatroomRepository.deleteById(roomId);
  }

  private UUID saveRoom(int maxMembers) {
    LocalChatroom room =
        localChatroomRepository.save(
            LocalChatroom.builder()
                .title("동시성 테스트 방")
                .description("동시 입장 검증용")
                .chatZone(ChatZone.SUYEONG_NAMGU)
                .maxMembers(maxMembers)
                .build());
    return room.getRoomId();
  }

  /**
   * 모든 스레드를 같은 순간에 풀어 경쟁을 만든다. 갱신된 행이 1인 호출 수를 센다.
   *
   * <p>각 호출은 독립된 트랜잭션에서 돈다. 하나의 트랜잭션을 공유하면 경쟁 자체가 일어나지 않는다. 스레드에서 터진 예외는 삼키지 않고 테스트를 실패시킨다 -- 삼키면
   * 갱신이 한 번도 일어나지 않아도 통과한 것처럼 보인다.
   */
  private int runConcurrently(java.util.function.IntSupplier update) throws Exception {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    ExecutorService executor = Executors.newFixedThreadPool(THREADS);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(THREADS);
    AtomicInteger succeeded = new AtomicInteger();
    AtomicReference<Throwable> failure = new AtomicReference<>();

    try {
      for (int i = 0; i < THREADS; i++) {
        executor.submit(
            () -> {
              try {
                start.await();
                Integer updated = transactionTemplate.execute(status -> update.getAsInt());
                if (updated != null && updated == 1) {
                  succeeded.incrementAndGet();
                }
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } catch (Throwable e) {
                failure.compareAndSet(null, e);
              } finally {
                done.countDown();
              }
            });
      }
      start.countDown();
      assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
    } finally {
      executor.shutdownNow();
    }
    assertThat(failure.get()).isNull();
    return succeeded.get();
  }
}
