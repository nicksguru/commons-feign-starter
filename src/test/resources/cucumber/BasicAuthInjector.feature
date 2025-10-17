@feign @security #@disabled
Feature: Basic Auth Injector for Feign Clients
  The BasicAuthInjector should properly encode credentials and provide header values

  Scenario: Creating a BasicAuthInjector with valid credentials
    Given a username "testUser" and password "testPassword"
    When a BasicAuthInjector is created with these credentials
    Then no exception should be thrown
    And the header value prefix should be "Basic "
    And the header value should be the Base64 encoded credentials

  Scenario Outline: Validation of credentials in BasicAuthInjector (don't reveal if username or password is empty)
    Given a username "<username>" and password "<password>"
    When a BasicAuthInjector is created with these credentials
    Then the exception message should contain "<errorMessage>"
    Examples:
      | username  | password  | errorMessage                            |
      |           |           | Username and password must not be blank |
      |           | password1 | Username and password must not be blank |
      | username1 |           | Username and password must not be blank |
