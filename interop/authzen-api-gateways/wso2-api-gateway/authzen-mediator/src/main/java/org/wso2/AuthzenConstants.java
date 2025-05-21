package org.wso2;

/**
 * Constants for Authzen
 */
public class AuthzenConstants {

    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String PDP_HEADER = "X_AUTHZEN_GATEWAY_PDP";
    public static final String CONTENT_TYPE_HEADER = "Content-Type";
    public static final String APPLICATION_JSON = "application/json";
    public static final String STRICT = "Strict";
    public static final String ALLOW_ALL = "AllowAll";
    public static final String HOST_NAME_VERIFIER = "httpclient.hostnameVerifier";
    public static final String HTTPS = "https";
    public static final String TRUST_STORE_PASSWORD_SYSTEM_PROPERTY = "javax.net.ssl.trustStorePassword";
    public static final String TRUST_STORE_LOCATION_SYSTEM_PROPERTY = "javax.net.ssl.trustStore";
    public static final String HTTP_METHOD_STRING = "HTTP_METHOD";
    public static final String HTTP_RESPONSE_STATUS_CODE = "HTTP_RESPONSE_STATUS_CODE";
    public static final String API_ELECTED_RESOURCE = "API_ELECTED_RESOURCE";
    public static final String EVALUATION_URL_SUFFIX = "/access/v1/evaluation";
    public static final String AUTHZEN_POLICY_FAILURE_HANDLER = "_authzen_policy_failure_handler_";
    public static final String PDPS = "pdps";
    public static final String PDP_TOKEN = "token";
    public static final String JWT_SUBJECT = "sub";
    public static final String KEY_STORE_TYPE = "JKS";

    // AuthZen output fields
    public static final String SUBJECT = "subject";
    public static final String TYPE = "type";
    public static final String ID = "id";
    public static final String IDENTITY = "identity";
    public static final String ACTION = "action";
    public static final String NAME = "name";
    public static final String RESOURCE = "resource";
    public static final String ROUTE = "route";
    public static final String CONTEXT = "context";

    // AuthZEN response
    public static final String EMPTY_OPA_RESPONSE = "{}";
    public static final String AUTHZEN_RESPONSE_DECISION_KEY = "decision";
}
