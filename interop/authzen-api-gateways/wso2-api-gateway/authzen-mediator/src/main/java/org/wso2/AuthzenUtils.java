package org.wso2;

import org.apache.http.HttpStatus;
import org.apache.synapse.Mediator;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.core.axis2.Axis2Sender;
import org.apache.synapse.transport.nhttp.NhttpConstants;

/**
 * Authzen Utils
 */
public class AuthzenUtils {

    /**
     * Handle the policy failure. This can be an internal error or access revoked by the policy
     *
     * @param messageContext Message context
     * @param e              AuthzenSecurityException
     */
    public static void handlePolicyFailure(MessageContext messageContext, AuthzenSecurityException e,
            String faultHandler) {

        int status;
        String errorMessage;
        if (e.getErrorCode() == AuthzenSecurityException.ACCESS_REVOKED) {
            status = HttpStatus.SC_FORBIDDEN;
            errorMessage = "Forbidden";
        } else {
            status = HttpStatus.SC_INTERNAL_SERVER_ERROR;
            errorMessage = "Internal Sever Error";
        }

        messageContext.setProperty(AuthzenConstants.HTTP_RESPONSE_STATUS_CODE, status);
        messageContext.setProperty(SynapseConstants.ERROR_CODE, e.getErrorCode());
        messageContext.setProperty(SynapseConstants.ERROR_MESSAGE, errorMessage);
        messageContext.setProperty(SynapseConstants.ERROR_EXCEPTION, e);

        Mediator sequence = messageContext.getSequence(faultHandler);
        if (sequence != null && !sequence.mediate(messageContext)) {
            // If needed user should be able to prevent the rest of the fault handling
            // logic from getting executed
            return;
        }

        org.apache.axis2.context.MessageContext axis2MC = ((Axis2MessageContext) messageContext).getAxis2MessageContext();
        axis2MC.setProperty(NhttpConstants.HTTP_SC, status);
        Axis2Sender.sendBack(messageContext);
    }
}
