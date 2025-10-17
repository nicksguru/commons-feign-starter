@feign #@disabled
Feature: Feign Logger
  FeignLogger should log requests and responses appropriately

  Background:
    Given a Feign Logger is created

  Scenario Outline: Logging requests with different body content
    When a request with method "<Method>" to URL "<URL>" with body "<Request Body>" is logged
    Then the request should be logged at info level
    And the log should contain the method and URL
    And the log should contain the request body: <Log Should Contain Request Body?>
    Examples:
      | Method | URL                 | Request Body         | Log Should Contain Request Body? |
      | GET    | https://example.com | null                 | false                            |
      | GET    | https://example.com |                      | false                            |
      | POST   | https://example.com | {\"id\": \"testId\"} | true                             |

  Scenario Outline: Logging responses with different status codes
    When a response with status "<Status>" and reason "<Reason>" from request "<Method>" to "<URL>" with body "<Request Body>" is logged
    Then the response should be logged at <Log Level> level
    And the log should contain the status code and reason
    And the log should contain the request body: <Log Should Contain Request Body?>
    Examples:
      | Status | Reason       | Method | URL                 | Request Body         | Log Level | Log Should Contain Request Body? |
      | 200    | OK           | PUT    | https://example.com | {\"id\": \"testId\"} | info      | true                             |
      | 400    | OK           | GET    | https://example.com | null                 | error     | false                            |
      | 404    | Not Found    | GET    | https://example.com | null                 | error     | false                            |
      | 500    | Server Error | POST   | https://example.com | {\"error\":\"Oops\"} | error     | true                             |

  Scenario: Logging IO exceptions
    When an IOException occurs
    Then the exception should be logged at error level
    And the log should contain the stack trace
