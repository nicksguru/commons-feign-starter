@feign @exception
Feature: Feign Exception Converter
  The FeignExceptionConverter should properly convert FeignException to BusinessException,
  mapping HTTP status codes correctly and handling the special case of IOException (status = -1)

  Scenario: Convert FeignException with normal HTTP status code
    Given a FeignException with HTTP status 400
    And an HTTP status code mapper that creates TestBusinessException
    When the FeignExceptionConverter converts the exception
    Then the converter should call the mapper with status code 400
    And the result should be a TestBusinessException
    And the result cause should be the original FeignException

  Scenario: Convert FeignException with HTTP status 401
    Given a FeignException with HTTP status 401
    And an HTTP status code mapper that creates TestBusinessException
    When the FeignExceptionConverter converts the exception
    Then the converter should call the mapper with status code 401
    And the result should be a TestBusinessException

  Scenario: Convert FeignException with HTTP status 403
    Given a FeignException with HTTP status 403
    And an HTTP status code mapper that creates TestBusinessException
    When the FeignExceptionConverter converts the exception
    Then the converter should call the mapper with status code 403
    And the result should be a TestBusinessException

  Scenario: Convert FeignException with HTTP status 404
    Given a FeignException with HTTP status 404
    And an HTTP status code mapper that creates TestBusinessException
    When the FeignExceptionConverter converts the exception
    Then the converter should call the mapper with status code 404
    And the result should be a TestBusinessException

  Scenario: Convert FeignException with HTTP status 500
    Given a FeignException with HTTP status 500
    And an HTTP status code mapper that creates TestBusinessException
    When the FeignExceptionConverter converts the exception
    Then the converter should call the mapper with status code 500
    And the result should be a TestBusinessException

  Scenario: Convert FeignException with IOException status -1
    Given a FeignException with HTTP status -1 indicating IOException
    And an HTTP status code mapper that creates TestBusinessException
    When the FeignExceptionConverter converts the exception
    Then the converter should call the mapper with status code 504
    And the result should be a TestBusinessException

  Scenario Outline: Convert FeignException with various HTTP status codes
    Given a FeignException with HTTP status <statusCode>
    And an HTTP status code mapper that creates TestBusinessException
    When the FeignExceptionConverter converts the exception
    Then the converter should call the mapper with status code <expectedCode>
    And the result should be a TestBusinessException
    Examples:
      | statusCode | expectedCode |
      | 200        | 200          |
      | 201        | 201          |
      | 400        | 400          |
      | 401        | 401          |
      | 403        | 403          |
      | 404        | 404          |
      | 500        | 500          |
      | 502        | 502          |
      | 503        | 503          |
      | -1         | 504          |

  Scenario: Verify mapper receives correct parameters for normal status code
    Given a FeignException with HTTP status 400
    And an HTTP status code mapper that captures parameters
    When the FeignExceptionConverter converts the exception
    Then the mapper should receive status code 400
    And the mapper should receive the original FeignException as cause

  Scenario: Verify mapper receives correct parameters for IOException status
    Given a FeignException with HTTP status -1 indicating IOException
    And an HTTP status code mapper that captures parameters
    When the FeignExceptionConverter converts the exception
    Then the mapper should receive status code 504
    And the mapper should receive the original FeignException as cause
