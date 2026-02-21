package upo.pissir.auth;

import io.javalin.security.RouteRole;

public enum Role implements RouteRole {
    CUSTOMER, EMPLOYEE, OPERATOR
}
