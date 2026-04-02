CREATE TABLE IF NOT EXISTS member
(
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    email           VARCHAR(200) NOT NULL UNIQUE,
    password        VARCHAR(200) NOT NULL,
    slack_member_id VARCHAR(30) UNIQUE,
    first_login_yn  VARCHAR(1)   NOT NULL DEFAULT 'Y',
    created_at      DATETIME              DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      BIGINT,
    updated_by      BIGINT
);
CREATE TABLE IF NOT EXISTS attendance
(
    id               BIGSERIAL PRIMARY KEY,
    member_id        BIGINT      NOT NULL, -- Member FK (필수)
    attendance_dt    VARCHAR(10) NOT NULL, -- 날짜
    plan_type        VARCHAR(20),          -- 계획 근무 유형
    plan_in_out      VARCHAR(20),          -- 계획 출퇴근 시간 (ex: 08:30~17:30)
    plan_work_hour   FLOAT,                -- 계획 근무 시간 (소수 허용)
    actual_type      VARCHAR(20),          -- 실제 근무 유형
    actual_in        VARCHAR(5),           -- 실제 출근 시각
    actual_out       VARCHAR(5),           -- 실제 퇴근 시각
    actual_work_hour VARCHAR(30),          -- 실제 근무 시간
    late             VARCHAR(10),          -- 지각 여부 ("" or 값)
    exception_work   VARCHAR(20),          -- 예외 근무
    early_leave      VARCHAR(10),          -- 조퇴 여부
    ot               FLOAT,                -- 연장근무 시간(시간 단위, 소수 허용)
    vacation         VARCHAR(20),          -- 휴가
    approval_request VARCHAR(20),          -- 승인 요청
    created_at       DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT fk_member_id FOREIGN KEY (member_id) REFERENCES member (id),
    UNIQUE KEY uk_attendance_dt_member (attendance_dt, member_id)
);
CREATE TABLE IF NOT EXISTS notification
(
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT        NOT NULL,
    slackInfo    VARCHAR(300)  NOT NULL,
    type         VARCHAR(20)   NOT NULL,
    message      VARCHAR(2000) NOT NULL,
    result       VARCHAR(20)   NOT NULL,
    error_reason VARCHAR(500),
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by   BIGINT,
    updated_by   BIGINT,
    FOREIGN KEY (user_id) REFERENCES member (id)
);
