@feign @exception
Feature: Feign Exception Parser
  The FeignExceptionParser should restore a remote exception out of a FeignException whose response body is a
  BusinessExceptionDto-shaped JSON (with the help of the stateful FailedRemoteCallParserVisitor), degrading
  gracefully when mapping is impossible. The stateless HttpStatusRetrieverVisitor should retrieve HTTP statuses out
  of known exception classes only.

  Background:
    Given a FeignExceptionParser with test mappers

  Scenario: Parse FeignException with valid FailedRemoteCall JSON body
    Given a FeignException with HTTP status 404 and JSON body:
      """
      {"errorCode":"USER_NOT_FOUND","message":"User not found","path":"/api/users/42","traceId":"trace-42","fieldErrors":[{"fieldName":"username","errorCode":"NotBlank","errorMessage":"must not be blank"}]}
      """
    When the FeignExceptionParser parses the exception
    Then no exception should be thrown
    And the parse result should not be null
    And the original exception should be the FeignException
    And the local exception should be mapped from HTTP status 404
    And the remote exception should be a RemoteBusinessException
    And the remote exception should have error code USER_NOT_FOUND
    And the remote exception should have message "User not found"
    And the remote exception should have path "/api/users/42"
    And the remote exception should have field error username:NotBlank
    And the deep cause should be the remote exception

  Scenario: Parse FeignException with non-JSON body
    Given a FeignException with HTTP status 400 and body "<html>Bad Request</html>"
    When the FeignExceptionParser parses the exception
    Then no exception should be thrown
    And the parse result should not be null
    And the original exception should be the FeignException
    And the local exception should be mapped from HTTP status 400
    And the remote exception should be null
    And the deep cause should be the local exception

  Scenario: Parse FeignException with unrelated JSON body
    Given a FeignException with HTTP status 400 and JSON body:
      """
      {"errorCode":"UNRELATED_ERROR","message":"unrelated"}
      """
    When the FeignExceptionParser parses the exception
    Then no exception should be thrown
    And the parse result should not be null
    And the local exception should be mapped from HTTP status 400
    And the remote exception should be null
    And the deep cause should be the local exception

  Scenario: Parse FeignException with empty body
    Given a FeignException with HTTP status 503 and body ""
    When the FeignExceptionParser parses the exception
    Then no exception should be thrown
    And the parse result should not be null
    And the local exception should be mapped from HTTP status 503
    And the remote exception should be null
    And the deep cause should be the local exception

  Scenario: Parse exception which is not a FeignException
    Given a plain IllegalStateException with message "boom"
    When the FeignExceptionParser parses the exception
    Then no exception should be thrown
    And the parse result should not be null
    And the original exception should be the parsed exception
    And the local exception should be null
    And the remote exception should be null
    And the deep cause should be the original exception

  Scenario: Parse a BusinessException as-is
    Given a TestBusinessException with HTTP status code 409
    When the FeignExceptionParser parses the exception
    Then no exception should be thrown
    And the parse result should not be null
    And the original exception should be the parsed exception
    And the local exception should be the parsed exception itself
    And the remote exception should be null
    And the deep cause should be the local exception

  Scenario: Parse ConnectException into ServiceTimeoutException
    Given a ConnectException with message "connection refused"
    When the FeignExceptionParser parses the exception
    Then no exception should be thrown
    And the parse result should not be null
    And the original exception should be the parsed exception
    And the local exception should be a ServiceTimeoutException
    And the remote exception should be null
    And the deep cause should be the local exception

  Scenario: Parse FeignException wrapped in a RuntimeException
    Given a FeignException with HTTP status 404 and JSON body:
      """
      {"errorCode":"USER_NOT_FOUND","message":"User not found"}
      """
    And the FeignException wrapped in a RuntimeException
    When the FeignExceptionParser parses the exception
    Then no exception should be thrown
    And the parse result should not be null
    And the original exception should be the FeignException
    And the local exception should be mapped from HTTP status 404
    And the remote exception should be a RemoteBusinessException
    And the deep cause should be the remote exception

  Scenario: Parsing null fails fast
    When the FeignExceptionParser parses null
    Then IllegalArgumentException should be thrown

  Scenario Outline: Retrieve HTTP status from a FeignException
    Given a FeignException with HTTP status <statusCode> and body ""
    When the HttpStatusRetrieverVisitor is applied to the exception
    Then the retrieved HTTP status should be <expectedStatus>
    Examples:
      | statusCode | expectedStatus     |
      | 400        | BAD_REQUEST        |
      | 404        | NOT_FOUND          |
      | 503        | SERVICE_UNAVAILABLE |
      | -1         | GATEWAY_TIMEOUT    |

  Scenario: No HTTP status for unresolvable FeignException status
    Given a FeignException with HTTP status 599 and body ""
    When the HttpStatusRetrieverVisitor is applied to the exception
    Then no HTTP status should be retrieved

  Scenario: Retrieve HTTP status from a ResponseStatusException
    Given a ResponseStatusException with HTTP status 418
    When the HttpStatusRetrieverVisitor is applied to the exception
    Then the retrieved HTTP status should be I_AM_A_TEAPOT

  Scenario: Retrieve HTTP status from a BusinessException via mapper
    Given a TestBusinessException with HTTP status code 409
    When the HttpStatusRetrieverVisitor is applied to the exception
    Then the retrieved HTTP status should be CONFLICT

  Scenario: No HTTP status for exception classes without a visitor
    Given a plain IllegalStateException with message "boom"
    When the HttpStatusRetrieverVisitor is applied to the exception
    Then no HTTP status should be retrieved
