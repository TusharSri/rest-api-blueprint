package io.github.tusharsri.blueprint.security;

import io.github.tusharsri.blueprint.config.AppProperties;
import io.github.tusharsri.blueprint.dto.TokenResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues signed HS256 JWTs. Validation is done by the resource-server JwtDecoder (see SecurityConfig). */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final Duration ttl;

    public JwtService(JwtEncoder encoder, AppProperties props) {
        this.encoder = encoder;
        this.ttl = Duration.ofMinutes(props.jwt().ttlMinutes());
    }

    public TokenResponse issue(UserDetails user) {
        Instant now = Instant.now();
        List<String> roles = user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("spring-boot-rest-api-blueprint")
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("roles", roles)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", ttl.toSeconds());
    }
}
