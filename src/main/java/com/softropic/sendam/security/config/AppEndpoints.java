package com.softropic.sendam.security.config;



import com.softropic.sendam.security.contract.util.AuthoritiesConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


public final class AppEndpoints {

    public static final String                    SECURED           = "/v1/**";
    public static final String                    SECURED_API           = "/api/**";
    // Client-facing API path prefix — controlled by ClientSecurityConfiguration chain (not the JWT chain)
    // Covers all client-facing endpoints: /v1/api/**, /v1/credits/**, /v1/sms/**
    public static final String                    SMS_API           = "/v1/sms/**";
    public static final String                    CREDITS_API       = "/v1/credits/**";
    public static final String                    CLIENT_API_KEYS   = "/v1/api/**";
    public static final String                    WEBHOOKS_API      = "/v1/webhooks/**";
    public static final String                    ACTUATOR           = "/manage/**";
    public static final String                    REFRESH           = "/refresh";
    public static final String                    ADMIN_CLIENTS     = "/api/admin/clients/**";
    public static final String                    ADMIN_TOPUPS      = "/api/admin/topups/**";
    public static final String                    ADMIN_API_KEYS    = "/api/admin/clients/*/keys/**";
    public static final String                    ADMIN_ANALYTICS   = "/api/admin/analytics/**";
    public static final String                    ADMIN_SPEND       = "/api/admin/spend/**";
    public static final String                    ADMIN_HEALTH      = "/api/admin/health/**";
    public static final Map<String, String[]> SECURED_MAPPINGS;
    public static final List<String>          SECURED_ENDPOINTS; //"/api/register"
    public static final String FROM_CHROME = "/.well-known/appspecific/com.chrome.devtools.json"; //TODO investigate how to handle this
    public static final List<String> PUBLIC_STATIC_RESOURCES = List.of("/", "/assets/**", "/scripts/**", "/i18n/**", "/favicon.ico", "/icons/**", "/index.html", FROM_CHROME);
    public static final List<String> PUBLIC_ENDPOINTS = List.of("/v1/account/register**", "/v1/account/regislink**", "/v1/account/activate/**",
                                                                "/v1/account/reset_password/init", "/v1/account/reset_password/finish",
                                                                "/api/v1/emails/**", "/authenticate");
    public static final List<String> ALL_UNRESTRICTED;

    private static final String[] SECURED_AUTHORITIES = new String[]{AuthoritiesConstants.ADMIN, AuthoritiesConstants.USER, AuthoritiesConstants.LTD_ADMIN};


    static {

        SECURED_MAPPINGS = Map.ofEntries(
            Map.entry(SECURED,         Arrays.copyOf(SECURED_AUTHORITIES, SECURED_AUTHORITIES.length)),
            Map.entry(SECURED_API,     Arrays.copyOf(SECURED_AUTHORITIES, SECURED_AUTHORITIES.length)),
            Map.entry(ACTUATOR,        new String[]{AuthoritiesConstants.ADMIN}),
            Map.entry(REFRESH,         Arrays.copyOf(SECURED_AUTHORITIES, SECURED_AUTHORITIES.length)),
            Map.entry(ADMIN_CLIENTS,   new String[]{AuthoritiesConstants.ADMIN}),
            Map.entry(ADMIN_TOPUPS,    new String[]{AuthoritiesConstants.ADMIN}),
            Map.entry(ADMIN_API_KEYS,  new String[]{AuthoritiesConstants.ADMIN}),
            Map.entry(ADMIN_ANALYTICS, new String[]{AuthoritiesConstants.ADMIN}),
            Map.entry(ADMIN_SPEND,     new String[]{AuthoritiesConstants.ADMIN}),
            Map.entry(ADMIN_HEALTH,    new String[]{AuthoritiesConstants.ADMIN})
        );
        SECURED_ENDPOINTS = List.copyOf(SECURED_MAPPINGS.keySet());
        ALL_UNRESTRICTED = new ArrayList<>(PUBLIC_STATIC_RESOURCES);
        ALL_UNRESTRICTED.addAll(PUBLIC_ENDPOINTS);
    }
    private AppEndpoints(){}

}
