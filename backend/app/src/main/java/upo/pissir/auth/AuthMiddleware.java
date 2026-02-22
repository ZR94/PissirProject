package upo.pissir.auth;

import io.javalin.Javalin;
import io.javalin.http.Context;

public final class AuthMiddleware {
    public static final String CTX_USER = "auth.user";

    private AuthMiddleware() {}

    public static void install(Javalin app) {
        app.before("/api/*", ctx -> {
            if ("OPTIONS".equals(ctx.method().name())) return;
            if ("/api/health".equals(ctx.path())) return;
            AuthUser user = Auth.requireAuth(ctx);
            ctx.attribute(CTX_USER, user);
        });
    }

    public static AuthUser requireUser(Context ctx) {
        AuthUser user = ctx.attribute(CTX_USER);
        return user != null ? user : Auth.requireAuth(ctx);
    }
}
