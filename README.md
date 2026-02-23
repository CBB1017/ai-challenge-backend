# 🏗️ Spring Boot Production Template

이 프로젝트는 운영 환경까지 확장 가능한, 실무용 Spring Boot 템플릿입니다.  
JPA, MyBatis, JWT 인증, Swagger 문서화, 로깅, 보안 등 모던 백엔드 필수 구성을 미리 제공합니다.
boot v3.5.3
jdk 21
gradle
---

## 📑 목차
1. [📂 템플릿 프로젝트 구조](#1--템플릿-프로젝트-구조)
2. [🔒 인증 모드 설명 (Auth Modes)](#2--인증-모드-설명-auth-modes)
3. [🛡️ Interceptor, Filter, Aspect, ExceptionHandler](#3--interceptor-filter-aspect-exceptionhandler)
4. [📝 로깅 (Logback + OTEL)](#4--로깅-logback--otel)
5. [🔍 Swagger (OpenAPI 3.0)](#5--swagger-openapi-30)
6. [⚙️ 개발/운영 환경(dev, prod) 설정 주의점](#6--개발운영-환경dev-prod-설정-주의점)
7. [🗄️ H2 DB & SQL](#7--h2-db--sql)
8. [🔐 보안 요구사항](#8--보안-요구사항)
9. [🧪 테스트 전략 & 커버리지](#9--테스트-전략--커버리지)
10. [⚠️ 주의사항](#10-주의사항)

---

## 1. 📂 템플릿 프로젝트 구조

| 디렉토리                                      | 설명                                | 주요 라이브러리/설명                                                                     |
| ----------------------------------------- | --------------------------------- | ------------------------------------------------------------------------------- |
| `src/main/java/com/brycenkorea/template/` |                                   |                                                                                 |
| ├── `annotation/`                         | 커스텀 어노테이션                         | lombok, **springdoc-openapi** (Swagger)                                         |
| │   └── `swagger/`                        | Swagger 관련 어노테이션                  | **springdoc-openapi**                                                           |
| ├── `aspect/`                             | 공통 Aspect (로깅 등)               | spring-boot-starter-aop, lombok (Slf4j)              |
| ├── `config/`                             | 환경별 Config, Security, **Swagger 등** | Spring Boot, Spring Security, **springdoc-openapi**                             |
| ├── `controller/`                         | API 컨트롤러                          | spring-boot-starter-web, validation, **springdoc-openapi (Swagger annotation)** |
| ├── `dto/`                                | 요청/응답 DTO                         | lombok, javax/jakarta validation                                                |
| │   ├── `api/`                            | API 관련 DTO                        | lombok, **springdoc-openapi (Schema/ApiModel 등)**                               |
| │   ├── `common/`                         | 공통 DTO                            | lombok                                                                          |
| │   ├── `request/`                        | 요청 DTO                            | lombok, validation                                                              |
| │   └── `response/`                       | 응답 DTO                            | lombok                                                                          |
| ├── `entity/`                             | JPA Entity 클래스                    | spring-boot-starter-data-jpa, lombok                                            |
| ├── `exception/`                          | 예외, 에러코드, 핸들러                     | Spring 표준, lombok, custom exception                                             |
| ├── `filter/`                             | JWT 등 Filter (OncePerRequestFilter) | spring-boot-starter-security, jjwt, spring-web                                  |
| ├── `interceptor/`                        | 인증/로깅 등 Interceptor               | spring-web, HandlerInterceptor                                                  |
| ├── `mapper/`                             |                                   |                                                                                 |
| │   ├── `mapstruct/`                      | MapStruct Mapper (DTO <-> Entity) | mapstruct, mapstruct-processor                                                  |
| ├── `repository/`                         | JPA Repository                    | spring-boot-starter-data-jpa                                                    |
| ├── `security/`                           | 암호화, UserDetails 등                | spring-boot-starter-security, jjwt                                              |
| ├── `service/`                            | 비즈니스 서비스 레이어                      | spring-boot-starter-web, lombok                                                 |
| └── `util/`                               | 공용 유틸리티 (JWT, 암복호화 등)             | jjwt, 표준 java, commons                                 |
| `src/main/resources/`                     |                                   |                                                                                 |
| ├── `static/`                             | 정적 리소스 폴더                         | -                                                                               |
| ├── `templates/`                          | 템플릿 폴더                            | thymeleaf 등(선택)                                                                 |
| ├── `application.yml`                     | 기본 애플리케이션 설정                      | Spring Boot                                                                     |
| ├── `application-dev.yml`                 | 개발환경 설정                           | Spring Boot                                                                     |
| ├── `application-prod.yml`                | 운영환경 설정                           | Spring Boot                                                                     |
| ├── `data-dev.sql`                        | 개발환경 초기 데이터                       | H2, JPA                                                                         |
| ├── `logback-spring.xml`                  | 로그백 설정                            | logback                                                                         |
| └── `schema-dev.sql`                      | 개발환경 스키마                          | H2, JPA                                                                         |
| `src/test/java/com/brycenkorea/template/`          |                   |                                                            |
| ├── `config/`                                      | 테스트 환경/설정         | spring-boot-starter-test, security-test 등                  |
| │   ├── `MockConfig`                               | Mock Bean 등 테스트 전용 설정 |                                                            |
| │   └── `TestSecurityConfig`                       | 테스트 전용 시큐리티 설정   |                                                            |
| ├── `controller/`                                  | 컨트롤러 통합/단위 테스트     | spring-boot-starter-test, mockito-core, security-test       |
| │   └── `MemberControllerIntegrationTest`            | Member 컨트롤러 통합테스트     | MockMvc, 인증/권한, 통합 API 테스트                        |
| ├── `exception/`                                   | 전역 예외처리 단위 테스트     | spring-boot-starter-test, mockito-core                      |
| │   └── `GlobalExceptionHandlerTest`               | 예외핸들러 단위테스트        | ExceptionHandler, 커스텀 예외 응답 구조 검증                |
| ├── `service/`                                     | 서비스(비즈니스) 단위테스트   | spring-boot-starter-test, mockito-core                      |
| │   └── `MemberServiceTest`                          | Member 서비스 단위테스트       | Mocking, 비즈니스 분기/예외 검증                           |
| ├── `util/`                                        | 공통/테스트 유틸리티         | assertj-core, lombok 등                                     |
| │   ├── `TestJwtUtil`                              | JWT 유틸 단위테스트          | Jwt 토큰 발급/파싱 테스트                                   |
| │   └── `TestUtil`                                 | 테스트 보조 유틸             | Exception/Request 등 테스트용 객체 생성                      |
| └── `TemplateApplicationTests`                     | (필요시) contextLoads 테스트 | Spring Boot context 전체 로딩 테스트                        |
| `README.md`                               | 프로젝트 설명 파일                        | -                                                                               |

---

## 2. 🔒 인증 모드 설명 (Auth Modes)

- **JWT 인증**
    - 디폴트 방식
    - `JwtAuthFilter`에서 AccessToken 헤더 파싱 후 인증 처리
- **Form, Basic 인증 지원**
    - `SecurityConfig`에서 인증 모드를 모듈화해서 전환 가능
- **커스텀 인증 방식 적용**
    - config에서 Auth provider, Filter, PasswordEncoder 등 손쉽게 교체

---

## 3. 🛡️ Interceptor, Filter, Aspect, ExceptionHandler

- **Filter**
    - 인증(JWT) 여부, 토큰 검증
    - Spring Security FilterChain, OncePerRequestFilter로 구현
    - Interceptor,Aspect와 security filter는 다른 영역이라는 것을 인지
    - 동일 Response 객체 사용
- **Interceptor**
    - 로그인 여부, 권한체크, 공통 Pre/Post 처리가 필요할 때 사용
    - HandlerInterceptorAdapter 상속
    - 이 템플릿에서는 부여한 역할 없음
    - `Aspect`보다 먼저 호출되고 나중에 호출됨
- **Aspect**
    - 로깅, 성능측정, 파라미터 트래킹
    - @Aspect + @Around, @AfterThrowing 등 AOP 방식
- **ExceptionHandler**
    - 전역 예외 처리 및 통일된 Response 반환
    - @ControllerAdvice, @ExceptionHandler
    - 예외 발생 시 API 표준 구조로 리턴, 기본적으로 traceId 포함하며 에러 발생 시 spanId 포함

---

## 4. 📝 로깅 (Logback + OTEL)

- **Logback**
    - `logback-spring.xml`
    - Console & Rolling FileAppender, 패턴/컬러/traceId/spanId 포함
    - 파일 롤링(일자/용량 기준)
    - root를 제외한 패키지별 레벨은 application yml에 명세
- **OpenTelemetry(OTEL)**
    - traceId, spanId로 추적성 확보 (분산추적 연동시 사용)
    - agent: WAS 프로세스, library: 코드 삽입 방식 지원
    - agent는 https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases 에서 다운받고 
    jar를 적절한 경로에 포함한다.(아래 OpenTelemetry VM 인수에서 설명)
    - 이 방식의 장점은 TraceId, SpanId를 자동으로 헤더에 설정
      - traceId: 요청 당 생성, spanId: 요청 내에 분기가 될만한 다른 요청(DB, http, thread 등) 시 생성
    - agent가 있으면 library는 필요없으나, 이 프로젝트에서는 비즈니스 추적을 위해 TraceId, SpanId를 ResponseEntity data에 포함시켜 library 추가함
      - agent 없이 library만 사용하려면 직접 요청 시마다 생성해야 함. 컨테이너 환경에서는 agent 추천, 클라우드에서는 논의 후 걷어내도 무방
      - 아예 OpenTelemetry가 필요없다면 VM 인수만 제거하고 trace/span ID 제거 작업
- **로그 레벨**
    - DEBUG/INFO/WARN/ERROR 개별 조정
    - 패키지별 세부 지정 가능(예: Hibernate, MyBatis, Security 등)
    - log.info(…)시 traceId도 자동 노출

### *OpenTelemetry VM 인수
    - javaagent:/path/to/opentelemetry-javaagent.jar
    - Dotel.service.name=brycen-template
    # openTelemetry-collector를 사용한다면(PLT or ELK)
    - Dotel.exporter.otlp.endpoint=http://localhost:4317
    # 사용하지 않는다면 아래 내용들 추가(export 하지 않고 traceId, spanId만 자동 생성)
    -Dotel.traces.exporter=none
    -Dotel.metrics.exporter=none
    -Dotel.logs.exporter=none

---

## 5. 🔍 Swagger (OpenAPI 3.0)

- **springdoc-openapi** 사용
- @Operation, @Parameter, @Schema 등으로 API 문서/샘플 자동화
- 공통 응답/에러 샘플은 커스텀 어노테이션(@SwaggerApiResponse 등) 활용
- `/swagger-ui.html` 접속
- JWT/Basic 인증 등 Security 스키마 연동

---

## 6. ⚙️ 개발/운영 환경(dev, prod) 설정 주의점

- **application.yml**
    - 공통/환경별 분리
    - DB, 시크릿, 포트, actuator 등 프로파일 관리
- **security config**
    - dev 환경에서 h2-console, swagger, actuator 경로는 화이트리스트
    - csrf, 기타 보안 요건과 함께 prod 환경에서는 접근제한 필수
- **actuator**
    - health, metrics, info 등 모니터링 포인트 노출
    - 운영에서는 인증/접근제어 적용
    - 필요가 없다면 바로 제거
- **devtools**
    - 개발 환경에서 hot reload 및 자동 빌드 지원
    - 운영 배포 시 반드시 exclude

---

## 7. 🗄️ H2 DB & SQL

- **h2 DB**
    - 개발 환경에서만 in-memory DB로 동작
    - 운영에서는 별도 DB(mysql, postgresql 등)로 대체
    - `schema-dev.sql`, `data-dev.sql` 사용
- **주의:**
    - 운영 배포시 application.yml, h2 dependency, 스키마/데이터 분리 관리
    - 실서비스 DB 연결 시 H2 설정, 데이터 삭제/주석 처리

---

## 8. 🔐 보안 요구사항

- **비밀번호 암호화:**
    - PasswordEncoder (bcrypt or SHA-256)
- **개인정보 암복호화:**
    - Jasypt, AES 등(추가 필요시 util 패키지로 모듈화)
- **CSRF:**
    - REST API는 CSRF disable, 필요시 Token 방식 구현
- **CORS:**
    - dev/prod 별 정책 분리, origins 제한 설정
- **로그 민감정보 마스킹:**
    - Custom log mask util로 key-value 치환 등

---
## 9. 🧪 테스트 전략 & 커버리지

### ▶️ 테스트 코드 작성 원칙

- **서비스/비즈니스 로직 단위 테스트**
  - Mockito, JUnit5 기반 Mocking으로 **비즈니스 분기, 예외, 성공 케이스** 집중 검증
  - BDD 스타일로 `@DisplayName`과 `.as()` 활용하여 **테스트 의도 명확화**

- **컨트롤러 & API 통합 테스트**
  - `@WebMvcTest`, `@SpringBootTest` 사용
  - MockMvc/RestAssured 기반 **HTTP API 레이어** 실제 동작 테스트
  - Security, Validation, ExceptionHandler 등 **API 표준 응답 구조** 일관성 검증

- **예외/전역 핸들러 테스트**
  - `@ControllerAdvice`, `@ExceptionHandler`가 커버하는 **모든 예외 타입별 직접 단위/통합 테스트**
  - `CommonResponse`의 code, message, error.detail 등 **응답 구조** 검증
--- 
## 10. 주의사항

- **테스트 코드(Junit, Mockito) 기본 제공**
- **Actuator, OpenTelemetry, Swagger 연동**
- **MapStruct 기반 DTO 매핑**
- **JPA, Mybatis 병행하고 있으나, 선택하여 한쪽만 사용하는 것을 권장**
- **cross-IDE 설정을 위해 .editorConfig으로 java 언어 정렬 방식 제공(수정하여 최적화)**
- **web server를 undertow로 변경했는데 익숙치 않다면 tomcat으로 변경 (dependency 수정)**

---