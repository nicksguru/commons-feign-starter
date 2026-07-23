@feign @security @oauth2
Feature: OAuth2 Authorization Code Injector for Feign Clients
  The OAuth2AuthorizationCodeInjector should properly obtain access tokens using OAuth2 Authorization Code Flow

  Scenario: Complete authorization code flow with auth code from Location header
    Given an authorization code token URL "https://auth.example.com/oauth/token"
    And an authorization URL "https://auth.example.com/oauth/authorize"
    And a redirect URL "https://client.example.com/callback"
    And authorization code client credentials with client ID "test-client" and client secret "test-secret"
    And user credentials with username "testuser" and password "testpass"
    And a RestOperations mock that returns authorization code "auth-code-123" in Location header
    And an authorization code RestOperations mock that returns a successful token response with access token "test-access-token" and expires in 3600 seconds
    When an OAuth2AuthorizationCodeInjector is created with these parameters
    Then no exception should be thrown
    And the authorization code header name should be "Authorization"
    And the authorization code header value should start with "Bearer "
    And the authorization code header value should contain the access token

  Scenario: Authorization code extraction from response body
    Given an authorization code token URL "https://auth.example.com/oauth/token"
    And an authorization URL "https://auth.example.com/oauth/authorize"
    And a redirect URL "https://client.example.com/callback"
    And authorization code client credentials with client ID "test-client" and client secret "test-secret"
    And user credentials with username "testuser" and password "testpass"
    And a RestOperations mock that returns authorization code "auth-code-456" in response body
    And an authorization code RestOperations mock that returns a successful token response with access token "test-access-token" and expires in 3600 seconds
    When an OAuth2AuthorizationCodeInjector is created with these parameters
    Then no exception should be thrown
    And the authorization code header name should be "Authorization"
    And the authorization code header value should start with "Bearer "

  Scenario: Token exchange with scope
    Given an authorization code token URL "https://auth.example.com/oauth/token"
    And an authorization URL "https://auth.example.com/oauth/authorize"
    And a redirect URL "https://client.example.com/callback"
    And authorization code client credentials with client ID "test-client" and client secret "test-secret"
    And user credentials with username "testuser" and password "testpass"
    And an authorization code scope "read write"
    And a RestOperations mock that returns authorization code "auth-code-789" in Location header
    And an authorization code RestOperations mock that returns a successful token response with access token "test-access-token" and expires in 3600 seconds
    When an OAuth2AuthorizationCodeInjector is created with these parameters
    Then no exception should be thrown
    And the authorization code header name should be "Authorization"
    And the authorization code header value should start with "Bearer "

  Scenario: Token exchange without scope
    Given an authorization code token URL "https://auth.example.com/oauth/token"
    And an authorization URL "https://auth.example.com/oauth/authorize"
    And a redirect URL "https://client.example.com/callback"
    And authorization code client credentials with client ID "test-client" and client secret "test-secret"
    And user credentials with username "testuser" and password "testpass"
    And a RestOperations mock that returns authorization code "auth-code-101" in Location header
    And an authorization code RestOperations mock that returns a successful token response with access token "test-access-token" and expires in 3600 seconds
    When an OAuth2AuthorizationCodeInjector is created with these parameters
    Then no exception should be thrown
    And the authorization code header name should be "Authorization"
    And the authorization code header value should start with "Bearer "

  Scenario: Failed authorization code request with RestClientException
    Given an authorization code token URL "https://auth.example.com/oauth/token"
    And an authorization URL "https://auth.example.com/oauth/authorize"
    And a redirect URL "https://client.example.com/callback"
    And authorization code client credentials with client ID "test-client" and client secret "test-secret"
    And user credentials with username "testuser" and password "testpass"
    And a RestOperations mock that throws RestClientException when requesting authorization code
    When an OAuth2AuthorizationCodeInjector is created with these parameters
    Then no exception should be thrown
    And obtaining an authorization code fresh header should throw RestClientException

  Scenario: Failed token exchange with non-2xx response
    Given an authorization code token URL "https://auth.example.com/oauth/token"
    And an authorization URL "https://auth.example.com/oauth/authorize"
    And a redirect URL "https://client.example.com/callback"
    And authorization code client credentials with client ID "test-client" and client secret "test-secret"
    And user credentials with username "testuser" and password "testpass"
    And a RestOperations mock that returns authorization code "auth-code-202" in Location header
    And an authorization code RestOperations mock that returns a 401 response when exchanging code for token
    When an OAuth2AuthorizationCodeInjector is created with these parameters
    Then no exception should be thrown
    And obtaining an authorization code fresh header should throw RestClientException

  Scenario: Failed token exchange with RestClientException
    Given an authorization code token URL "https://auth.example.com/oauth/token"
    And an authorization URL "https://auth.example.com/oauth/authorize"
    And a redirect URL "https://client.example.com/callback"
    And authorization code client credentials with client ID "test-client" and client secret "test-secret"
    And user credentials with username "testuser" and password "testpass"
    And a RestOperations mock that returns authorization code "auth-code-303" in Location header
    And an authorization code RestOperations mock that throws RestClientException when exchanging code for token
    When an OAuth2AuthorizationCodeInjector is created with these parameters
    Then no exception should be thrown
    And obtaining an authorization code fresh header should throw RestClientException

  Scenario: No authorization code found in response
    Given an authorization code token URL "https://auth.example.com/oauth/token"
    And an authorization URL "https://auth.example.com/oauth/authorize"
    And a redirect URL "https://client.example.com/callback"
    And authorization code client credentials with client ID "test-client" and client secret "test-secret"
    And user credentials with username "testuser" and password "testpass"
    And a RestOperations mock that returns an authorization response without authorization code
    When an OAuth2AuthorizationCodeInjector is created with these parameters
    Then no exception should be thrown
    And obtaining an authorization code fresh header should throw RestClientException

  Scenario: Token expiration and refresh behavior
    Given an authorization code token URL "https://auth.example.com/oauth/token"
    And an authorization URL "https://auth.example.com/oauth/authorize"
    And a redirect URL "https://client.example.com/callback"
    And authorization code client credentials with client ID "test-client" and client secret "test-secret"
    And user credentials with username "testuser" and password "testpass"
    And a RestOperations mock that returns authorization code "auth-code-404" in Location header
    And an authorization code RestOperations mock that returns a successful token response with access token "test-access-token" and expires in 3600 seconds
    When an OAuth2AuthorizationCodeInjector is created with these parameters
    Then no exception should be thrown
    And the authorization code header should have an expiration date

  Scenario: Validation error for blank token URL
    Given an authorization code token URL ""
    And an authorization URL "https://auth.example.com/oauth/authorize"
    And a redirect URL "https://client.example.com/callback"
    And authorization code client credentials with client ID "test-client" and client secret "test-secret"
    And user credentials with username "testuser" and password "testpass"
    And a RestOperations mock that returns authorization code in Location header
    And an authorization code RestOperations mock that returns a successful token response
    When an OAuth2AuthorizationCodeInjector is created with these parameters
    Then IllegalArgumentException should be thrown

  Scenario: Validation error for null token URL
    Given an authorization code token URL null
    And an authorization URL "https://auth.example.com/oauth/authorize"
    And a redirect URL "https://client.example.com/callback"
    And authorization code client credentials with client ID "test-client" and client secret "test-secret"
    And user credentials with username "testuser" and password "testpass"
    And a RestOperations mock that returns authorization code in Location header
    And an authorization code RestOperations mock that returns a successful token response
    When an OAuth2AuthorizationCodeInjector is created with these parameters
    Then IllegalArgumentException should be thrown

  Scenario: Validation error for blank authorization URL
    Given an authorization code token URL "https://auth.example.com/oauth/token"
    And an authorization URL ""
    And a redirect URL "https://client.example.com/callback"
    And authorization code client credentials with client ID "test-client" and client secret "test-secret"
    And user credentials with username "testuser" and password "testpass"
    And a RestOperations mock that returns authorization code in Location header
    And an authorization code RestOperations mock that returns a successful token response
    When an OAuth2AuthorizationCodeInjector is created with these parameters
    Then IllegalArgumentException should be thrown

  Scenario: Validation error for blank redirect URL
    Given an authorization code token URL "https://auth.example.com/oauth/token"
    And an authorization URL "https://auth.example.com/oauth/authorize"
    And a redirect URL ""
    And authorization code client credentials with client ID "test-client" and client secret "test-secret"
    And user credentials with username "testuser" and password "testpass"
    And a RestOperations mock that returns authorization code in Location header
    And an authorization code RestOperations mock that returns a successful token response
    When an OAuth2AuthorizationCodeInjector is created with these parameters
    Then IllegalArgumentException should be thrown

  Scenario: Validation error for blank client ID
    Given an authorization code token URL "https://auth.example.com/oauth/token"
    And an authorization URL "https://auth.example.com/oauth/authorize"
    And a redirect URL "https://client.example.com/callback"
    And authorization code client credentials with client ID "" and client secret "test-secret"
    And user credentials with username "testuser" and password "testpass"
    And a RestOperations mock that returns authorization code in Location header
    And an authorization code RestOperations mock that returns a successful token response
    When an OAuth2AuthorizationCodeInjector is created with these parameters
    Then IllegalArgumentException should be thrown

  Scenario: Validation error for null client credentials
    Given an authorization code token URL "https://auth.example.com/oauth/token"
    And an authorization URL "https://auth.example.com/oauth/authorize"
    And a redirect URL "https://client.example.com/callback"
    And authorization code client credentials null
    And user credentials with username "testuser" and password "testpass"
    And a RestOperations mock that returns authorization code in Location header
    And an authorization code RestOperations mock that returns a successful token response
    When an OAuth2AuthorizationCodeInjector is created with these parameters
    Then IllegalArgumentException should be thrown

  Scenario: Validation error for blank username in user credentials
    Given an authorization code token URL "https://auth.example.com/oauth/token"
    And an authorization URL "https://auth.example.com/oauth/authorize"
    And a redirect URL "https://client.example.com/callback"
    And authorization code client credentials with client ID "test-client" and client secret "test-secret"
    And user credentials with username "" and password "testpass"
    And a RestOperations mock that returns authorization code in Location header
    And an authorization code RestOperations mock that returns a successful token response
    When an OAuth2AuthorizationCodeInjector is created with these parameters
    Then IllegalArgumentException should be thrown

  Scenario: Validation error for blank password in user credentials
    Given an authorization code token URL "https://auth.example.com/oauth/token"
    And an authorization URL "https://auth.example.com/oauth/authorize"
    And a redirect URL "https://client.example.com/callback"
    And authorization code client credentials with client ID "test-client" and client secret "test-secret"
    And user credentials with username "testuser" and password ""
    And a RestOperations mock that returns authorization code in Location header
    And an authorization code RestOperations mock that returns a successful token response
    When an OAuth2AuthorizationCodeInjector is created with these parameters
    Then IllegalArgumentException should be thrown

  Scenario: Validation error for null user credentials
    Given an authorization code token URL "https://auth.example.com/oauth/token"
    And an authorization URL "https://auth.example.com/oauth/authorize"
    And a redirect URL "https://client.example.com/callback"
    And authorization code client credentials with client ID "test-client" and client secret "test-secret"
    And user credentials null
    And a RestOperations mock that returns authorization code in Location header
    And an authorization code RestOperations mock that returns a successful token response
    When an OAuth2AuthorizationCodeInjector is created with these parameters
    Then IllegalArgumentException should be thrown

  Scenario: Validation error for null RestOperations
    Given an authorization code token URL "https://auth.example.com/oauth/token"
    And an authorization URL "https://auth.example.com/oauth/authorize"
    And a redirect URL "https://client.example.com/callback"
    And authorization code client credentials with client ID "test-client" and client secret "test-secret"
    And user credentials with username "testuser" and password "testpass"
    And an authorization code RestOperations mock null
    When an OAuth2AuthorizationCodeInjector is created with these parameters
    Then IllegalArgumentException should be thrown

  Scenario: Validation error for null ExpirableHeaderMapper
    Given an authorization code token URL "https://auth.example.com/oauth/token"
    And an authorization URL "https://auth.example.com/oauth/authorize"
    And a redirect URL "https://client.example.com/callback"
    And authorization code client credentials with client ID "test-client" and client secret "test-secret"
    And user credentials with username "testuser" and password "testpass"
    And a RestOperations mock that returns authorization code in Location header
    And an authorization code RestOperations mock that returns a successful token response
    And an authorization code ExpirableHeaderMapper null
    When an OAuth2AuthorizationCodeInjector is created with these parameters
    Then IllegalArgumentException should be thrown

  Scenario: Client credentials without secret
    Given an authorization code token URL "https://auth.example.com/oauth/token"
    And an authorization URL "https://auth.example.com/oauth/authorize"
    And a redirect URL "https://client.example.com/callback"
    And authorization code client credentials with client ID "public-client" and client secret null
    And user credentials with username "testuser" and password "testpass"
    And a RestOperations mock that returns authorization code "auth-code-505" in Location header
    And an authorization code RestOperations mock that returns a successful token response with access token "test-access-token" and expires in 3600 seconds
    When an OAuth2AuthorizationCodeInjector is created with these parameters
    Then no exception should be thrown
    And the authorization code header name should be "Authorization"
