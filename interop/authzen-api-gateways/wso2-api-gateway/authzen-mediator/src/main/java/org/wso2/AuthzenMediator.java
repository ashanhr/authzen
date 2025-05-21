package org.wso2;

import com.google.gson.Gson;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.mediators.AbstractMediator;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;
import java.util.TreeMap;

/**
 * Authzen Mediator
 */
public class AuthzenMediator extends AbstractMediator {

    private static final Log log = LogFactory.getLog(AuthzenMediator.class);

    private AuthzenClient authzenClient;
    private static final Map<String, String> pdps = Map.ofEntries(
            Map.entry("Aserto", "https://topaz-proxy.demo.authzen-interop.net"),
            Map.entry("AVP", "https://authzen-avp.interop-it.org"), Map.entry("Axiomatics", "https://pdp.alfa.guide"),
            Map.entry("Cerbos", "https://authzen-proxy-demo.cerbos.dev"),
            Map.entry("HexaOPA", "https://interop.authzen.hexaorchestration.org"),
            Map.entry("OpenFGA", "https://authzen-interop.openfga.dev/stores/01JNW1803442023HVDKV03FB3A"),
            Map.entry("PingAuthorize", "https://authzen.idpartners.au"),
            Map.entry("PlainID", "https://authzeninteropt.se-plainid.com"),
            Map.entry("Rock Solid Knowledge", "https://authzen.identityserver.com"),
            Map.entry("SGNL", "https://authzen.sgnlapis.cloud"),
            Map.entry("Topaz", "https://topaz-proxy.demo.authzen-interop.net"),
            Map.entry("WSO2", "https://authzen-interop-demo.wso2support.com/api/identity"));

    @Override
    public boolean mediate(MessageContext messageContext) {
        Gson gson = new Gson();
        Map<String, Map<String, String>> pdpKeys = gson.fromJson(System.getProperty(AuthzenConstants.PDPS), Map.class);

        org.apache.axis2.context.MessageContext axis2MessageContext = ((Axis2MessageContext) messageContext).getAxis2MessageContext();
        TreeMap<String, String> transportHeadersMap = (TreeMap<String, String>) axis2MessageContext.getProperty(
                org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);

        String subjectId = extractSubjectId(transportHeadersMap.get(AuthzenConstants.AUTHORIZATION_HEADER));
        String requestMethod = axis2MessageContext.getProperty(AuthzenConstants.HTTP_METHOD_STRING).toString();
        String requestPath = messageContext.getProperty(AuthzenConstants.API_ELECTED_RESOURCE).toString();

        JSONObject authzenPayload = generateAuthzenPayload(subjectId, requestMethod, requestPath);

        try {
            String authzenPayloadString = authzenPayload.toString();
            String pdpName = transportHeadersMap.get(AuthzenConstants.PDP_HEADER);

            if (pdpName == null) {
                log.error("PDP header not found in the request");
                throw new AuthzenSecurityException(AuthzenSecurityException.INTERNAL_ERROR,
                        "X_AUTHZEN_GATEWAY_PDP header not found in the request");
            }

            String evaluationUrl = pdps.get(pdpName) + AuthzenConstants.EVALUATION_URL_SUFFIX;
            String accessKey = pdpKeys.get(pdpName).get(AuthzenConstants.PDP_TOKEN);

            authzenClient = new AuthzenClient(evaluationUrl);
            String authzenResponseString = authzenClient.publish(evaluationUrl, authzenPayloadString, accessKey);

            return processAuthzenResponse(authzenResponseString);
        } catch (AuthzenSecurityException e) {
            AuthzenUtils.handlePolicyFailure(messageContext, e, AuthzenConstants.AUTHZEN_POLICY_FAILURE_HANDLER);
        }
        return false;
    }

    /**
     * Generate the AuthZEN payload
     *
     * @param subjectId     Subject ID
     * @param requestMethod Request method
     * @param requestPath   Request path
     * @return AuthZEN payload
     */
    private JSONObject generateAuthzenPayload(String subjectId, String requestMethod, String requestPath) {
        JSONObject authzenPayload = new JSONObject();
        authzenPayload.put(AuthzenConstants.SUBJECT,
                new JSONObject().put(AuthzenConstants.TYPE, AuthzenConstants.IDENTITY)
                        .put(AuthzenConstants.ID, subjectId));
        authzenPayload.put(AuthzenConstants.ACTION, new JSONObject().put(AuthzenConstants.NAME, requestMethod));
        authzenPayload.put(AuthzenConstants.RESOURCE,
                new JSONObject().put(AuthzenConstants.TYPE, AuthzenConstants.ROUTE)
                        .put(AuthzenConstants.ID, requestPath));
        authzenPayload.put(AuthzenConstants.CONTEXT, new JSONObject());
        return authzenPayload;
    }

    /**
     * Process the AuthZEN response
     *
     * @param authzenResponseString AuthZEN response
     * @return AuthZEN response
     * @throws AuthzenSecurityException
     */
    private boolean processAuthzenResponse(String authzenResponseString) throws AuthzenSecurityException {
        if (authzenResponseString.equals(AuthzenConstants.EMPTY_OPA_RESPONSE)) {
            log.error("Empty result received for the AuthZEN evaluation request");
            throw new AuthzenSecurityException(AuthzenSecurityException.INTERNAL_ERROR,
                    "Empty result received for the AuthZEN evaluation request");
        }
        try {
            JSONObject responseObject = new JSONObject(authzenResponseString);
            boolean authzenResponse = responseObject.getBoolean(AuthzenConstants.AUTHZEN_RESPONSE_DECISION_KEY);
            if (!authzenResponse) {
                throw new AuthzenSecurityException(AuthzenSecurityException.ACCESS_REVOKED,
                        AuthzenSecurityException.ACCESS_REVOKED_MESSAGE);
            }
            return authzenResponse;
        } catch (JSONException e) {
            log.error("Error parsing AuthZEN JSON response, the field \"decision\" not found or not a Boolean", e);
            throw new AuthzenSecurityException(AuthzenSecurityException.INTERNAL_ERROR,
                    AuthzenSecurityException.INTERNAL_ERROR_MESSAGE, e);
        }
    }

    /**
     * Extract the subject ID from the JWT
     *
     * @param authorizationHeader Authorization header
     * @return Subject ID
     */
    private String extractSubjectId(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(AuthzenConstants.BEARER_PREFIX)) {
            return null;
        }
        String token = authorizationHeader.substring(7);
        try {
            JSONObject decodedJwt = new JSONObject(
                    new String(java.util.Base64.getDecoder().decode(token.split("\\.")[1])));
            return decodedJwt.optString(AuthzenConstants.JWT_SUBJECT, null);
        } catch (Exception e) {
            log.error("Error decoding JWT: " + e.getMessage());
            return null;
        }
    }
}
