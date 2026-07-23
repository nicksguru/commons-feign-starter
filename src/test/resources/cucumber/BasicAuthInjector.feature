@feign @security #@disabled
Feature: Basic Auth Injector for Feign Clients
  The BasicAuthInjector should properly encode credentials and provide header values

  Scenario: Creation with valid credentials
    Given a username "testUser" and password "testPassword"
    When a BasicAuthInjector is created with these credentials
    Then no exception should be thrown
    And the header value prefix should be "Basic "
    And the header value should be the Base64 encoded credentials

  Scenario Outline: Creation with optional credentials
    Given a username "<username>" and password "<password>"
    When a BasicAuthInjector is created with these credentials
    Then no exception should be thrown
    And the header value prefix should be "Basic "
    And the header value should be the Base64 encoded credentials
    Examples:
      | username  | password  |
      |           |           |
      |           | password1 |
      | username1 |           |
