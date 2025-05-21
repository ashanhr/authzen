#!/bin/bash

# Variables
WSO2_APIM_VERSION="4.5.0"
WSO2_APIM_DOWNLOAD_URL="https://github.com/wso2/product-apim/releases/download/v$WSO2_APIM_VERSION/wso2am-$WSO2_APIM_VERSION.zip"
WSO2_APIM_HOME="wso2am-$WSO2_APIM_VERSION"
API_MGR_HOST="https://localhost:9443"
USERNAME="admin"
PASSWORD="admin"
API_PAYLOAD_FILE_WITHOUT_IDS="resources/api-payload.json"
API_PAYLOAD_FILE="resources/updated-payload.json"
NEW_API_MANAGER_SCRIPT="resources/api-manager.sh"
API_MANAGER_SCRIPT_TO_REPLACE="$WSO2_APIM_HOME/bin/api-manager.sh"
DEPLOYMENT_TOML_TO_REPLACE="$WSO2_APIM_HOME/repository/conf/deployment.toml"
NEW_DEPLOYMENT_TOML="resources/deployment.toml"
AUTHZEN_FAULT_HANDLER="resources/_authzen_policy_failure_handler_.xml"
AUTHZEN_JAR="authzen-mediator/target/authzen-1.0-SNAPSHOT.jar"
JAR_DESTINATION="$WSO2_APIM_HOME/repository/components/lib"
AUTHZEN_POLICY_FILE="resources/authzen-policy.xml"
TODO_OPENAPI_JSON="resources/todo-openapi.json"

# Download and extract WSO2 API Manager
echo "Downloading WSO2 API Manager..."
curl -L -o wso2am-$WSO2_APIM_VERSION.zip $WSO2_APIM_DOWNLOAD_URL
unzip -q -o wso2am-$WSO2_APIM_VERSION.zip -d .
rm wso2am-$WSO2_APIM_VERSION.zip

# Replace the deployment.toml file to configure API Manager
echo "Replacing deployment.toml file..."
cp -f "$NEW_DEPLOYMENT_TOML" "$DEPLOYMENT_TOML_TO_REPLACE"

# Replace the API Manager startup script
echo "Replacing api-manager.sh file..."
cp -f "$NEW_API_MANAGER_SCRIPT" "$API_MANAGER_SCRIPT_TO_REPLACE"

# Add the Authzen policy failure handler in required locations
echo "Adding authzen policy failure handler..."
cp -f "$AUTHZEN_FAULT_HANDLER" "$WSO2_APIM_HOME/repository/resources/apim-synapse-config"
cp -f "$AUTHZEN_FAULT_HANDLER" "$WSO2_APIM_HOME/repository/deployment/server/synapse-configs/default/sequences"

# Copy the Authzen mediator JAR file to WSO2 API Manager's lib directory
echo "Copying authzen jar..."
cp "$AUTHZEN_JAR" "$JAR_DESTINATION"

# Start WSO2 API Manager
echo "Starting WSO2 API Manager..."
./$WSO2_APIM_HOME/bin/api-manager.sh > /dev/null 2>&1 &

# Wait for WSO2 API Manager to start
echo "Waiting for WSO2 API Manager to start..."
while ! nc -z localhost 9443; do
    sleep 5
done

echo "WSO2 API Manager started successfully."

# Encode credentials for Basic Authentication
echo "Encoding credentials..."
AUTH=YWRtaW46YWRtaW4=

# Create and upload the Authzen policy
echo "Creating policy..."
POLICY_RESPONSE=$(curl -k -s -X POST "$API_MGR_HOST/api/am/publisher/v4/operation-policies" \
  -H "Authorization: Basic $AUTH" \
  -H "Content-Type: multipart/form-data" \
  -F 'policySpecFile={
    "category": "Mediation",
    "name": "Authzen",
    "displayName": "Authzen",
    "version": "v1",
    "description": "Authzen",
    "applicableFlows": ["request"],
    "supportedApiTypes": ["HTTP"],
    "supportedGateways": ["Synapse"],
    "policyAttributes": []
  }' \
  -F synapsePolicyDefinitionFile=@"$AUTHZEN_POLICY_FILE")

echo "Policy created: $POLICY_RESPONSE"

# Create the Authzen Todo API
echo "Creating API..."
API_RESPONSE=$(curl -k -s -X POST "$API_MGR_HOST/api/am/publisher/v4/apis/import-openapi" \
  -H "Authorization: Basic $AUTH" \
  -H "Content-Type: multipart/form-data" \
  -F 'additionalProperties={
    "name": "AuthZENTodo",
    "version": "1.0.0",
    "context": "authzentodo",
    "endpointConfig": {
      "endpoint_type": "http",
      "sandbox_endpoints": {"url": "https://authzen-todo-backend.demo.aserto.com/"},
      "production_endpoints": {"url": "https://authzen-todo-backend.demo.aserto.com/"}
    }
  }' \
  -F file=@"$TODO_OPENAPI_JSON")

echo "API created: $API_RESPONSE"

# Extract API ID from the response
echo "Extracting API ID..."
API_ID=$(echo "$API_RESPONSE" | grep -o '"id":"[^"]*"' | sed -E 's/"id":"([^"]*)"/\1/')
echo "API ID: $API_ID"

# Extract Policy ID from the response
echo "Extracting Policy ID..."
POLICY_ID=$(echo "$POLICY_RESPONSE" | grep -o '"id":"[^"]*"' | sed -E 's/"id":"([^"]*)"/\1/')
echo "Policy ID: $POLICY_ID"

# Update API payload with the extracted IDs
echo "Updating API with policy..."
sed "s/\"id\": \"[^\"]*\"/\"id\": \"$API_ID\"/g; \
     s/\"policyId\": \"[^\"]*\"/\"policyId\": \"$POLICY_ID\"/g" "$API_PAYLOAD_FILE_WITHOUT_IDS" > "$API_PAYLOAD_FILE"

# Update the API with the policy
API_UPDATE_RESPONSE=$(curl -k -s -X PUT "$API_MGR_HOST/api/am/publisher/v4/apis/$API_ID" \
  -H "Authorization: Basic $AUTH" \
  -H "Content-Type: application/json" \
  --data @"$API_PAYLOAD_FILE")

echo "API updated: $API_UPDATE_RESPONSE"

# Create a new API revision
echo "Creating API Revision..."
API_REVISION_RESPONSE=$(curl -k -s -X POST "$API_MGR_HOST/api/am/publisher/v4/apis/$API_ID/revisions" \
  -H "Authorization: Basic $AUTH" \
  -H "Content-Type: application/json" \
  --data '{
    "description": "AuthZENTodo revision"
  }')

echo "API Revision created: $API_REVISION_RESPONSE"

# Extract Revision ID
echo "Extracting Revision ID..."
REVISION_ID=$(echo "$API_REVISION_RESPONSE" | grep -o '"id":"[^"]*"' | head -n1 | sed -E 's/"id":"([^"]*)"/\1/')
echo "Revision ID: $REVISION_ID"

# Deploy the created API revision
echo "Deploying the created Revision..."
DEPLOY_REVISION_RESPONSE=$(curl -k -s -X POST "$API_MGR_HOST/api/am/publisher/v4/apis/$API_ID/deploy-revision?revisionId=$REVISION_ID" \
  -H "Authorization: Basic $AUTH" \
  -H "Content-Type: application/json" \
  --data '[
    {
        "name": "Default",
        "vhost": "localhost",
        "displayOnDevportal": true
    }
]')

echo "API revision deployed: $DEPLOY_REVISION_RESPONSE"

# Publish the API
echo "Publishing the API..."
PUBLISH_API_RESPONSE=$(curl -k -s -X POST "$API_MGR_HOST/api/am/publisher/v4/apis/change-lifecycle?action=Publish&apiId=$API_ID" \
  -H "Authorization: Basic $AUTH")

echo "API published: $PUBLISH_API_RESPONSE"

echo "Setup completed successfully!"
