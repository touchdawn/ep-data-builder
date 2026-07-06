package com.ep.databuilder.security;

/** 当前登录用户的线程上下文，由 AuthInterceptor 维护 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static String usernameOrSystem() {
        LoginUser u = HOLDER.get();
        return u == null ? "system" : u.getUsername();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
