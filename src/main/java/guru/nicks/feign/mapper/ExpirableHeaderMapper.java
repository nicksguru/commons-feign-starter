package guru.nicks.feign.mapper;

import guru.nicks.feign.domain.ExpirableHeader;
import guru.nicks.mapper.DefaultMapStructConfig;
import guru.nicks.rest.v1.dto.OAuth2AccessTokenDto;
import guru.nicks.utils.AuthUtils;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.Instant;

@Mapper(config = DefaultMapStructConfig.class)
public interface ExpirableHeaderMapper {

    @Mapping(target = "valuePrefix", constant = AuthUtils.BEARER_AUTH_PREFIX)
    @Mapping(target = "value", source = "accessToken")
    @Mapping(target = "issuedDate", expression = "java(java.time.Instant.now())")
    @Mapping(target = "expirationDate", source = "expiresInSeconds", qualifiedByName = "calculateHeaderExpirationDate")
    ExpirableHeader toHeader(OAuth2AccessTokenDto dto);

    /**
     * Calculates the expiration date based on the expires-in seconds value. If the expires-in value is {@code null},
     * returns {@code null}. Otherwise, returns the current time plus the specified number of seconds.
     *
     * @param expiresInSeconds the number of seconds until expiration, or {@code null}
     * @return the calculated expiration date, or {@code null} if expiresInSeconds is {@code null}
     */
    @Named("calculateHeaderExpirationDate")
    default Instant calculateHeaderExpirationDate(Long expiresInSeconds) {
        return (expiresInSeconds == null)
                ? null
                : Instant.now().plusSeconds(expiresInSeconds);
    }


}
