package io.github.tusharsri.blueprint.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.tusharsri.blueprint.dto.TokenResponse;
import io.github.tusharsri.blueprint.security.JwtService;
import io.github.tusharsri.blueprint.service.AuthService;
import io.github.tusharsri.blueprint.support.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/** AuthService with real user store and BCrypt; only the token issuer is mocked. */
@UnitTest
class AuthServiceTest {

    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        UserDetails bob = User.withUsername("bob").password(encoder.encode("secret")).roles("USER").build();
        jwtService = mock(JwtService.class);
        authService = new AuthService(new InMemoryUserDetailsManager(bob), encoder, jwtService);
    }

    @Test
    void valid_credentials_return_the_issued_token() {
        TokenResponse issued = new TokenResponse("token-value", "Bearer", 60);
        given(jwtService.issue(any(UserDetails.class))).willReturn(issued);

        TokenResponse response = authService.login("bob", "secret");

        assertThat(response).isSameAs(issued);
        verify(jwtService).issue(any(UserDetails.class));
    }

    @Test
    void wrong_password_is_rejected_and_no_token_is_issued() {
        assertThatThrownBy(() -> authService.login("bob", "wrong"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid username or password");
        verifyNoInteractions(jwtService);
    }

    @Test
    void unknown_user_gets_the_same_message_so_accounts_cannot_be_enumerated() {
        assertThatThrownBy(() -> authService.login("nobody", "whatever"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid username or password");
        verifyNoInteractions(jwtService);
    }
}
