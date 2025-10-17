@feign #@disabled
Feature: Feign Header Injector

  Scenario: Header is injected when header value is not blank
    Given a Feign header injector with header name "X-Test-Header1"
    And header value "Bearer token123"
    When the injector is applied to a request template
    Then the request template should have header "X-Test-Header1" with value "Bearer token123"

  Scenario: Header is not injected when header value is blank
    Given a Feign header injector with header name "X-Test-Header2"
    And header value " "
    When the injector is applied to a request template
    Then the request template should not have header "X-Test-Header2"

  Scenario Outline: Different header configurations
    Given a Feign header injector with header name "<headerName>"
    And header value "<value>"
    When the injector is applied to a request template
    Then the request template should <hasHeader> header "<headerName>"
    Examples:
      | headerName    | value           | hasHeader |
      | Authorization | Bearer token123 | have      |
      | X-API-Key     | api-key         | have      |
      | Custom-Header | Custom: data    | have      |
      | Authorization |                 | not have  |
      | X-API-Key     |                 | not have  |
