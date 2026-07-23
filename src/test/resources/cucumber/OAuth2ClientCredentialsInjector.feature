@feign @security @oauth2
Feature: OAuth2 Client Credentials Injector for Feign Clients
  The OAuth2ClientCredentialsInjector should properly obtain access tokens using OAuth2 Client Credentials Flow

  Scenario: Successful token acquisition using client credentials flow
    Given a client credentials token URL "https://auth.example.com/oauth/token"
    And client credentials with client ID "test-client" and client secret "test-secret"
    And a client credentials RestOperations mock that returns a successful token response with access token "test-access-token" and expires in 3600 seconds
    When an OAuth2ClientCredentialsInjector is created with these parameters
    Then no exception should be thrown
    And the client credentials header name should be "Authorization"
    And the client credentials header value should start with "Bearer "
    And the client credentials header value should contain the access token

  Scenario: Token request with scope
    Given a client credentials token URL "https://auth.example.com/oauth/token"
    And client credentials with client ID "test-client" and client secret "test-secret"
    And a client credentials scope "read write"
    And a client credentials RestOperations mock that returns a successful token response with access token "test-access-token" and expires in 3600 seconds
    When an OAuth2ClientCredentialsInjector is created with these parameters
    Then no exception should be thrown
    And the client credentials header name should be "Authorization"
    And the client credentials header value should start with "Bearer "

  Scenario: Token request without scope
    Given a client credentials token URL "https://auth.example.com/oauth/token"
    And client credentials with client ID "test-client" and client secret "test-secret"
    And a client credentials RestOperations mock that returns a successful token response with access token "test-access-token" and expires in 3600 seconds
    When an OAuth2ClientCredentialsInjector is created with these parameters
    Then no exception should be thrown
    And the client credentials header name should be "Authorization"
    And the client credentials header value should start with "Bearer "

  Scenario: Failed token request with non-2xx response
    Given a client credentials token URL "https://auth.example.com/oauth/token"
    And client credentials with client ID "test-client" and client secret "test-secret"
    And a client credentials RestOperations mock that returns a 401 response
    When an OAuth2ClientCredentialsInjector is created with these parameters
    Then no exception should be thrown
    And obtaining a client credentials fresh header should throw RestClientException

  Scenario: Failed token request with RestClientException
    Given a client credentials token URL "https://auth.example.com/oauth/token"
    And client credentials with client ID "test-client" and client secret "test-secret"
    And a client credentials RestOperations mock that throws RestClientException
    When an OAuth2ClientCredentialsInjector is created with these parameters
    Then no exception should be thrown
    And obtaining a client credentials fresh header should throw RestClientException

  Scenario: Token expiration and refresh behavior
    Given a client credentials token URL "https://auth.example.com/oauth/token"
    And client credentials with client ID "test-client" and client secret "test-secret"
    And a client credentials RestOperations mock that returns a successful token response with access token "test-access-token" and expires in 3600 seconds
    When an OAuth2ClientCredentialsInjector is created with these parameters
    Then no exception should be thrown
    And the client credentials header should have an expiration date

  Scenario: Validation error for blank token URL
    Given a client credentials token URL ""
    And client credentials with client ID "test-client" and client secret "test-secret"
    And a client credentials RestOperations mock that returns a successful token response
    When an OAuth2ClientCredentialsInjector is created with these parameters
    Then IllegalArgumentException should be thrown

  Scenario: Validation error for null token URL
    Given a client credentials token URL null
    And client credentials with client ID "test-client" and client secret "test-secret"
    And a client credentials RestOperations mock that returns a successful token response
    When an OAuth2ClientCredentialsInjector is created with these parameters
    Then IllegalArgumentException should be thrown

  Scenario: Validation error for blank client ID
    Given a client credentials token URL "https://auth.example.com/oauth/token"
    And client credentials with client ID "" and client secret "test-secret"
    And a client credentials RestOperations mock that returns a successful token response
    When an OAuth2ClientCredentialsInjector is created with these parameters
    Then IllegalArgumentException should be thrown

  Scenario: Validation error for null client credentials
    Given a client credentials token URL "https://auth.example.com/oauth/token"
    And client credentials null
    And a client credentials RestOperations mock that returns a successful token response
    When an OAuth2ClientCredentialsInjector is created with these parameters
    Then IllegalArgumentException should be thrown

  Scenario: Validation error for null RestOperations
    Given a client credentials token URL "https://auth.example.com/oauth/token"
    And client credentials with client ID "test-client" and client secret "test-secret"
    And a client credentials RestOperations mock null
    When an OAuth2ClientCredentialsInjector is created with these parameters
    Then IllegalArgumentException should be thrown

  Scenario: Validation error for null ExpirableHeaderMapper
    Given a client credentials token URL "https://auth.example.com/oauth/token"
    And client credentials with client ID "test-client" and client secret "test-secret"
    And a client credentials RestOperations mock that returns a successful token response
    And a client credentials ExpirableHeaderMapper null
    When an OAuth2ClientCredentialsInjector is created with these parameters
    Then IllegalArgumentException should be thrown

  Scenario: Client credentials without secret
    Given a client credentials token URL "https://auth.example.com/oauth/token"
    And client credentials with client ID "public-client" and client secret null
    And a client credentials RestOperations mock that returns a successful token response with access token "test-access-token" and expires in 3600 seconds
    When an OAuth2ClientCredentialsInjector is created with these parameters
    Then no exception should be thrown
    And the client credentials header name should be "Authorization"
