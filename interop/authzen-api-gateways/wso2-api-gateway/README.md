# Authzen Mediator Setup

## Prerequisites

Ensure the following prerequisites are met before proceeding:

- **Java 21**: Set `JAVA_HOME` environment variable to the Java 21 installation path.

  Example:
  ```bash
  export JAVA_HOME=/path/to/java-21
  export PATH=$JAVA_HOME/bin:$PATH
  ```

- **Maven**: Ensure Maven is installed and available in your system path.

## Building the Authzen Mediator

1. Navigate to the `authzen-mediator` directory:

   ```bash
   cd authzen-mediator
   ```

2. Build the project using Maven:

   ```bash
   mvn clean package
   ```

   Upon successful build, the JAR file will be located at:

   ```bash
   authzen-mediator/target/authzen-1.0-SNAPSHOT.jar
   ```

## Running WSO2 API Manager and Deploying the Authzen Todo API

1. Ensure the `JAVA_HOME` environment variable is set to Java 21.

2. Execute the provided `script.sh` to download and run WSO2 API Manager 4.4.0, and create and publish the Authzen Todo API:

   ```bash
   ./script.sh
   ```

   This script performs the following actions:
   - Downloads and extracts WSO2 API Manager 4.4.0 (if not already present).
   - Starts the WSO2 API Manager.
   - Creates and publishes the Authzen Todo API, which proxies the Authzen Todo backend.

## Verifying the Setup

1. Access the WSO2 API Manager Publisher Portal at:

   [https://localhost:9443/publisher](https://localhost:9443/publisher)

2. Confirm the "Authzen Todo API" is available and published.

3. Access the Authzen Todo API through the following endpoint:

   ```bash
   https://localhost:8243/authzentodo/1.0.0
   ```

## Troubleshooting

- Ensure that Java 21 is correctly set in `JAVA_HOME`.
- Check logs in the WSO2 API Manager runtime for errors.
  
  Example log location:
  ```bash
  wso2am-4.4.0/repository/logs/wso2carbon.log
  ```

