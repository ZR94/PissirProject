package upo.pissir.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

import java.net.URI;
import java.text.ParseException;

public final class JwtVerifier {

    private static final ConfigurableJWTProcessor<SecurityContext> PROCESSOR;
    private static final String ISSUER;   // optional
    private static final String AUDIENCE; // optional

    static {
        try {
            ISSUER = trimToNull(System.getenv("KEYCLOAK_ISSUER"));
            AUDIENCE = trimToNull(System.getenv("KEYCLOAK_AUDIENCE"));
            String jwksUrl = trimToNull(System.getenv("KEYCLOAK_JWKS_URL"));

            if (jwksUrl == null) {
                throw new IllegalStateException("KEYCLOAK_JWKS_URL missing");
            }

            URI jwksUri = URI.create(jwksUrl);

            @SuppressWarnings("deprecation")
            JWKSource<SecurityContext> keySource = new RemoteJWKSet<>(jwksUri.toURL());

            ConfigurableJWTProcessor<SecurityContext> p = new DefaultJWTProcessor<>();
            JWSKeySelector<SecurityContext> keySelector =
                    new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);

            p.setJWSKeySelector(keySelector);
            PROCESSOR = p;
        } catch (Exception e) {
            throw new RuntimeException("Failed to init JwtVerifier", e);
        }
    }

    private JwtVerifier() {}

    public static JWTClaimsSet verify(String token)
            throws ParseException, BadJOSEException, JOSEException {

        JWTClaimsSet claims = PROCESSOR.process(token, (SecurityContext) null);

        // issuer check (optional)
        if (ISSUER != null) {
            String tokenIssuer = claims.getIssuer();
            if (!ISSUER.equals(tokenIssuer)) {
                throw new BadJWTException("Invalid issuer");
            }
        }

        // audience check (optional)
        if (AUDIENCE != null) {
            if (claims.getAudience() == null || !claims.getAudience().contains(AUDIENCE)) {
                throw new BadJWTException("Invalid audience");
            }
        }

        return claims;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

