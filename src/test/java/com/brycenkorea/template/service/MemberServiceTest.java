package com.brycenkorea.template.service;

import com.brycenkorea.template.dto.api.ApiResultCode;
import com.brycenkorea.template.dto.request.member.MemberRequest;
import com.brycenkorea.template.entity.Member;
import com.brycenkorea.template.exception.ApiException;
import com.brycenkorea.template.mapper.mapstruct.MemberRequestMapper;
import com.brycenkorea.template.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@DisplayName("MemberService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    MemberRepository memberRepository;
    @Mock
    PasswordEncoder sha256PasswordEncoder;
    @Mock
    MemberRequestMapper memberRequestMapper;
    @InjectMocks
    MemberService memberService;

    // =============== JPA 방식 ===============

    @Test
    @DisplayName("[JPA] 중복 name 저장시 예외 발생")
    void save_throws_when_username_exists() {
        MemberRequest req = new MemberRequest();
        req.setName("dupMember");
        given(memberRepository.findByName("dupMember")).willReturn(Optional.of(new Member()));

        assertThatThrownBy(() -> memberService.save(req)).as("이미 존재하는 name이면 MEMBER_ALREADY_EXISTS 예외가 발생해야 한다")
                                                         .isInstanceOf(ApiException.class)
                                                         .hasMessageContaining(ApiResultCode.MEMBER_ALREADY_EXISTS.getMessage());
    }

    @Test
    @DisplayName("[JPA] 신규 회원 저장 성공, 비밀번호 암호화")
    void save_success_encrypts_password() {
        MemberRequest req = new MemberRequest();
        req.setName("newMember");
        req.setPassword("plainpw");
        given(memberRepository.findByName("newMember")).willReturn(Optional.empty());
        Member member = new Member();
        member.setName("newMember");
        member.setPassword("plainpw");
        given(memberRequestMapper.toEntity(req)).willReturn(member);
        given(sha256PasswordEncoder.encode("plainpw")).willReturn("encodedpw");
        given(memberRepository.save(any())).willReturn(member);

        Member saved = memberService.save(req);

        assertThat(saved.getPassword()).as("비밀번호가 반드시 암호화된 값이어야 한다").isEqualTo("encodedpw");
        verify(sha256PasswordEncoder).encode("plainpw");
        verify(memberRepository).save(member);
    }

    @Test
    @DisplayName("[JPA] 회원 단건조회 - 없는 ID는 예외")
    void findById_throws_when_user_not_found() {
        given(memberRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.findById(999L)).as("존재하지 않는 id는 MEMBER_NOT_FOUND 예외 발생")
                                                              .isInstanceOf(ApiException.class)
                                                              .hasMessageContaining(ApiResultCode.MEMBER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("[JPA] 회원 단건조회 - 정상")
    void findById_returns_user() {
        Member member = new Member();
        member.setId(1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        Member found = memberService.findById(1L);

        assertThat(found.getId()).as("존재하는 id로 조회시 일치하는 Member 반환").isEqualTo(1L);
    }

    @Test
    @DisplayName("[JPA] 전체 회원 조회")
    void findAll_returns_list() {
        List<Member> mockMembers = List.of(new Member(), new Member());
        given(memberRepository.findAll()).willReturn(mockMembers);

        List<Member> result = memberService.findAll();

        assertThat(result).as("조회 결과가 비어있지 않아야 함").isNotEmpty();
    }

    @Test
    @DisplayName("[JPA] username으로 회원 조회 - 정상")
    void findByName_success() {
        Member member = new Member();
        member.setName("u1");
        given(memberRepository.findByName("u1")).willReturn(Optional.of(member));

        Member found = memberService.findByName("u1");

        assertThat(found.getName()).as("존재하는 username으로 조회시 일치하는 Member 반환").isEqualTo("u1");
    }

    @Test
    @DisplayName("[JPA] username으로 회원 조회 - 실패")
    void findByName_throws_when_not_found() {
        given(memberRepository.findByName("u2")).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.findByName("u2")).as("존재하지 않는 name으로 조회시 MEMBER_NOT_FOUND 예외 발생")
                                                                .isInstanceOf(ApiException.class)
                                                                .hasMessageContaining(ApiResultCode.MEMBER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("[JPA] 회원 수정 - 중복 name")
    void update_throws_when_duplicate_username() {
        MemberRequest req = new MemberRequest();
        long id = 1L;
        req.setName("dupMember");

        given(memberRepository.findById(id)).willReturn(Optional.of(new Member()));
        Member other = new Member();
        other.setId(2L);
        given(memberRepository.findByName("dupMember")).willReturn(Optional.of(other));

        assertThatThrownBy(() -> memberService.update(id, req)).as("username이 다른 사용자와 중복되면 MEMBER_ALREADY_EXISTS 예외 발생")
                                                               .isInstanceOf(ApiException.class)
                                                               .hasMessageContaining(ApiResultCode.MEMBER_ALREADY_EXISTS.getMessage());
    }

    @Test
    @DisplayName("[JPA] 회원 수정 - 없는 대상")
    void update_throws_when_not_found() {
        MemberRequest req = new MemberRequest();
        long id = 1000000000000000L;

        given(memberRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.update(id, req)).as("존재하지 않는 id로 수정시 MEMBER_NOT_FOUND 예외 발생")
                                                               .isInstanceOf(ApiException.class)
                                                               .hasMessageContaining(ApiResultCode.MEMBER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("[JPA] 회원 수정 - 정상 플로우")
    void update_success() {
        MemberRequest req = new MemberRequest();
        long id = 1L;
        req.setName("editMember");
        req.setPassword("plainpw");

        // 수정할 기존 엔티티
        Member current = new Member();
        current.setId(id);

        given(memberRepository.findById(id)).willReturn(Optional.of(current));
        given(memberRepository.findByName("editMember")).willReturn(Optional.empty());

        // 실제로 password가 반영되도록 mock
        willAnswer(invocation -> {
            MemberRequest src = invocation.getArgument(0);
            Member dest = invocation.getArgument(1);
            dest.setName(src.getName());
            dest.setPassword(src.getPassword());
            return null;
        }).given(memberRequestMapper).updateEntityFromDto(req, current);

        given(sha256PasswordEncoder.encode("plainpw")).willReturn("encodedpw");
        given(memberRepository.save(current)).willReturn(current);

        Member updated = memberService.update(id, req);

        assertThat(updated.getPassword())
            .as("수정시 비밀번호는 암호화된 값이어야 한다")
            .isEqualTo("encodedpw");

        verify(memberRequestMapper).updateEntityFromDto(req, current);
        verify(sha256PasswordEncoder).encode("plainpw");
        verify(memberRepository).save(current);
    }

    @Test
    @DisplayName("[JPA] 회원 삭제 - 없는 ID")
    void deleteById_throws_when_not_found() {
        long notFoundId = 1000000000000000L;
        given(memberRepository.existsById(notFoundId)).willReturn(false);

        assertThatThrownBy(() -> memberService.deleteById(notFoundId)).as("존재하지 않는 id로 삭제시 MEMBER_NOT_FOUND 예외 발생")
                                                                      .isInstanceOf(ApiException.class)
                                                                      .hasMessageContaining(ApiResultCode.MEMBER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("[JPA] 회원 삭제 - 정상")
    void deleteById_success() {
        long existingId = 1L;
        given(memberRepository.existsById(existingId)).willReturn(true);

        assertThatCode(() -> memberService.deleteById(existingId)).as("존재하는 id로 삭제시 예외 없이 통과해야 함")
                                                                  .doesNotThrowAnyException();
        verify(memberRepository).deleteById(existingId);
    }

}
