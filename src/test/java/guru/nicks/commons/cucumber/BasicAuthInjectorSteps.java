package guru.nicks.commons.cucumber;

import guru.nicks.commons.cucumber.world.TextWorld;
import guru.nicks.commons.auth.domain.BasicAuthCredentials;
import guru.nicks.commons.feign.injector.BasicAuthInjector;

import io.cucumber.java.DataTableType;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.Strings;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for testing {@link BasicAuthInjector}.
 */
@RequiredArgsConstructor
public class BasicAuthInjectorSteps {

    // DI
    private final TextWorld textWorld;

    private String username;
    private String password;
    private BasicAuthInjector basicAuthInjector;

    @DataTableType
    public Credentials createCredentials(Map<String, String> entry) {
        return Credentials.builder()
                .username(entry.get("username"))
                .password(entry.get("password"))
                .build();
    }

    @Given("a username {string} and password {string}")
    public void givenUsernameAndPassword(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @When("a BasicAuthInjector is created with these credentials")
    public void whenBasicAuthInjectorIsCreated() {
        var credentials = BasicAuthCredentials.builder()
                .username(username)
                .password(password)
                .build();

        try {
            basicAuthInjector = new BasicAuthInjector(credentials);
        } catch (Exception e) {
            textWorld.setLastException(e);
        }
    }

    @Then("the header value prefix should be {string}")
    public void thenHeaderValuePrefixShouldBe(String expectedPrefix) {
        assertThat(basicAuthInjector.getHeaderValue())
                .as("headerValue prefix")
                .startsWith(expectedPrefix);
    }

    @Then("the header value should be the Base64 encoded credentials")
    public void thenHeaderValueShouldBeBase64EncodedCredentials() {
        String credentials = username + ":" + password;
        String expectedEncodedValue = Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8));

        assertThat(Strings.CS.removeStart(basicAuthInjector.getHeaderValue(), BasicAuthCredentials.BASIC_AUTH_PREFIX))
                .as("headerValue without prefix")
                .isEqualTo(expectedEncodedValue);
    }

    @Value
    @Builder
    public static class Credentials {

        String username;
        String password;

    }
}
