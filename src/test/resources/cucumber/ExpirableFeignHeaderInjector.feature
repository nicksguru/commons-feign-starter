@feign
Feature: Expirable Feign Header Injector
  Negative caching, last-known-good stale serving, and fail-fast behavior when all header refresh attempts fail.
  The stub uses short real durations (100-300 ms TTLs) and disables preemptive async refresh for determinism;
  one refresh cycle of the default retrier equals 3 obtainFreshHeader() attempts.

  Scenario: Cold start with provider down fails fast
    Given an expirable header injector stub with header TTL 300 ms, failure cache TTL 10000 ms, and stale window 60000 ms
    And the stub provider goes down
    When the expirable header value is obtained
    Then FeignHeaderRefreshException should be thrown
    And the exception message should contain "X-Test-Header"
    And the exception cause should not be null

  Scenario: Failure is negatively cached within failure TTL
    Given an expirable header injector stub with header TTL 300 ms, failure cache TTL 10000 ms, and stale window 60000 ms
    And the stub provider goes down
    When the expirable header value is obtained
    And the expirable header value is obtained
    And the expirable header value is obtained
    Then the expirable fresh header attempt count should be 3

  Scenario: Stale header served within stale window
    Given an expirable header injector stub with header TTL 300 ms, failure cache TTL 10000 ms, and stale window 5000 ms
    And an expirable header value was already obtained
    And sleep 400 milliseconds
    And the stub provider goes down
    When the expirable header value is obtained
    Then the expirable header value should be "Bearer token-1"

  Scenario: Concurrent stale-served calls trigger a single background refresh
    Given an expirable header injector stub with header TTL 300 ms, failure cache TTL 20000 ms, and stale window 60000 ms
    And an expirable header value was already obtained
    And sleep 400 milliseconds
    And the stub provider goes down
    And an expirable header value was already obtained
    And the expirable background refresh settles with 7 fresh header attempts
    Given the expirable fresh header attempt counter is reset
    When 4 concurrent expirable header values are obtained
    Then all 4 concurrent expirable header values should be "Bearer token-1"
    And the expirable background refresh settles with 3 fresh header attempts

  Scenario: Fail fast after stale window elapses
    Given an expirable header injector stub with header TTL 300 ms, failure cache TTL 10000 ms, and stale window 300 ms
    And an expirable header value was already obtained
    And sleep 1500 milliseconds
    And the stub provider goes down
    When the expirable header value is obtained
    Then FeignHeaderRefreshException should be thrown

  Scenario: SEND_EMPTY policy returns empty header value
    Given an expirable header injector stub with header TTL 300 ms, failure cache TTL 10000 ms, and stale window 60000 ms
    And the stub failure policy is SEND_EMPTY
    And the stub provider goes down
    When the expirable header value is obtained
    Then the expirable header value should be empty

  Scenario: Recovery after failure TTL elapses
    Given an expirable header injector stub with header TTL 300 ms, failure cache TTL 150 ms, and stale window 60000 ms
    And the stub provider goes down
    When the expirable header value is obtained
    And sleep 300 milliseconds
    And the stub provider comes back up
    When the expirable header value is obtained
    Then the expirable header value should be "Bearer token-4"
