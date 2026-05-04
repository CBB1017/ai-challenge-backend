package com.brycenkorea.template.controller;

import com.brycenkorea.template.util.TestJwtUtil;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MemberControllerIntegrationTest {

    @LocalServerPort
    int port;

    String jwtToken;

    @Autowired
    TestJwtUtil testJwtUtil;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        jwtToken = testJwtUtil.createTestToken(1L, "test", "USER");
    }
    /*@BeforeEach
    void setUp() {
        RestAssured.port = port;
        Map<String, String> loginReq = Map.of(
            "name", "test",
            "password", "test1234"
        );
        jwtToken = given()
            .contentType(ContentType.JSON)
            .body(loginReq)
            .when()
            .post("/api/v1/auth/login")
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .extract()
            .path("token");
    }*/

    @Test
    @Order(1)
    @DisplayName("JPA 회원 CRUD 정상 플로우")
    void testJPA_CRUD() {
        // 1. 등록
        Map<String, String> req = Map.of(
            "name",
            "jpa_user1",
            "password",
            "jpa_pw1!!",
            "email",
            "jpa_user1@sample.com",
            "position",
            "주임"
        );
        int userId = given().header("Authorization", "Bearer " + jwtToken)
            .contentType(ContentType.JSON)
            .body(req)
            .when()
            .post("/api/v1/member")
            .then()
            .log()
            .ifValidationFails()
            .statusCode(200)
            .body("data.name", equalTo("jpa_user1"))
            .body("code", equalTo(200))
            .extract()
            .path("data.id");

        // 2. 단건조회
        given().header("Authorization", "Bearer " + jwtToken)
            .when()
            .get("/api/v1/member/" + userId)
            .then()
            .log()
            .ifValidationFails()
            .statusCode(200)
            .body("data.name", equalTo("jpa_user1"))
            .body("code", equalTo(200));

        // 3. 전체조회
        given().header("Authorization", "Bearer " + jwtToken)
            .when()
            .get("/api/v1/member")
            .then()
            .log()
            .ifValidationFails()
            .statusCode(200)
            .body("data.name", hasItem("jpa_user1"))
            .body("code", equalTo(200));

        // 4. 수정
        Map<String, String> updateReq = Map.of(
            "name",
            "jpa_user1_edited",
            "password",
            "jpa_pw1_edited!!",
            "email",
            "jpa_user1_edited@sample.com"
        );
        given().header("Authorization", "Bearer " + jwtToken)
            .contentType(ContentType.JSON)
            .body(updateReq)
            .when()
            .put("/api/v1/member/" + userId)
            .then()
            .log()
            .ifValidationFails()
            .statusCode(200)
            .body("data.name", equalTo("jpa_user1_edited"))
            .body("code", equalTo(200));

        // 5. 삭제
        given().header("Authorization", "Bearer " + jwtToken)
            .when()
            .delete("/api/v1/member/" + userId)
            .then()
            .log()
            .ifValidationFails()
            .statusCode(200)
            .body("code", equalTo(200));

        // 6. 삭제 후 단건조회 → 없는 회원 (code 404, statusCode 200)
        given().header("Authorization", "Bearer " + jwtToken)
            .when()
            .get("/api/v1/member/" + userId)
            .then()
            .log()
            .ifValidationFails()
            .statusCode(200)
            .body("code", equalTo(404))
            .body("message", containsString("사용자 없음"));
    }

    @Test
    @Order(3)
    @DisplayName("없는 회원 조회/삭제시 code=404, HTTP 200")
    void testNotFoundCases() {
        long notExistId = 99999999999999L;

        // 없는 회원 조회
        given().header("Authorization", "Bearer " + jwtToken)
            .when()
            .get("/api/v1/member/" + notExistId)
            .then()
            .log()
            .ifValidationFails()
            .statusCode(200)
            .body("code", equalTo(404))
            .body("message", containsString("사용자 없음"));

        // 없는 회원 삭제
        given().header("Authorization", "Bearer " + jwtToken)
            .when()
            .delete("/api/v1/member/" + notExistId)
            .then()
            .log()
            .ifValidationFails()
            .statusCode(200)
            .body("code", equalTo(404))
            .body("message", containsString("사용자 없음"));
    }

    @Test
    @Order(4)
    @DisplayName("이미 존재하는 사용자 등록시 code=409, HTTP 409")
    void testDuplicateUser() {
        Map<String, String> req = Map.of(
            "name", "test", // 이미 있는 유저
            "password", "test1234", "email", "test@sample.com", "position", "주임"
        );

        given().header("Authorization", "Bearer " + jwtToken)
            .contentType(ContentType.JSON)
            .body(req)
            .when()
            .post("/api/v1/member")
            .then()
            .log()
            .ifValidationFails()
            .statusCode(409)
            .body("code", equalTo(409))
            .body("message", containsString("이미 존재하는 사용자"));
    }

    @Test
    @Order(5)
    @DisplayName("Validation 실패 - 잘못된 요청, code=400, HTTP 400")
    void testValidationErrors() {
        // name 누락
        Map<String, String> req1 = Map.of("password", "test1234", "email", "fail@sample.com");
        given().header("Authorization", "Bearer " + jwtToken)
            .contentType(ContentType.JSON)
            .body(req1)
            .when()
            .post("/api/v1/member")
            .then()
            .log()
            .all()
            .statusCode(400)
            .body("code", equalTo(400))
            .body("message", containsString("올바른 데이터로 요청"));

        // 잘못된 이메일, 짧은 비밀번호
        Map<String, String> req2 = Map.of("name", "fail_case", "password", "short", "email", "notanemail");
        given().header("Authorization", "Bearer " + jwtToken)
            .contentType(ContentType.JSON)
            .body(req2)
            .when()
            .post("/api/v1/member")
            .then()
            .log()
            .all()
            .statusCode(400)
            .body("code", equalTo(400))
            .body("message", containsString("올바른 데이터로 요청"));
    }
}
