@feign #@disabled
Feature: FeignRetryer logic

  Scenario Outline: Retry attempts within the maximum limit
    Given a FeignRetryer is created with period <period>, maxPeriod <maxPeriod>, and maxAttempts <maxAttempts>
    When continueOrPropagate is called <retries> times with a RetryableException
    Then no exception should be thrown
    Examples:
      | period | maxPeriod | maxAttempts | retries |
      | 100    | 1000      | 3           | 1       |
      | 100    | 1000      | 3           | 2       |
      | 50     | 500       | 5           | 4       |

  Scenario Outline: Exception is propagated when maximum attempts are exceeded
    Given a FeignRetryer is created with period <period>, maxPeriod <maxPeriod>, and maxAttempts <maxAttempts>
    When continueOrPropagate is called <retries> times with a RetryableException
    Then an exception should be thrown
    Examples:
      | period | maxPeriod | maxAttempts | retries | comments                                                     |
      | 100    | 1000      | 3           | 3       |                                                              |
      | 100    | 1000      | 3           | 3       | Call beyond maxAttempts (+1 original attempt before retries) |
      | 50     | 500       | 2           | 2       |                                                              |
      | 50     | 500       | 1           | 1       |                                                              |

  Scenario: Cloning the FeignRetryer
    Given a FeignRetryer is created with period 100, maxPeriod 1000, and maxAttempts 3
    When the FeignRetryer is cloned
    Then the cloned FeignRetryer should be a new instance
    And the cloned FeignRetryer should have the same period, maxPeriod, and maxAttempts
