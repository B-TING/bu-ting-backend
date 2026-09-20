-- 스케줄러 분산 잠금 테이블. 인스턴스가 여러 개일 때 같은 작업이 동시에 두 번 돌지 않게 한다.
-- 라운드 정산은 중복 실행되면 보상이 두 번 나간다.
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    CONSTRAINT shedlock_pkey PRIMARY KEY (name)
);
