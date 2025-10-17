package guru.nicks.feign.mapper;

import guru.nicks.feign.domain.ExpirableHeader;
import guru.nicks.mapper.DefaultMapStructConfig;
import guru.nicks.rest.v1.dto.OAuth2AccessTokenDto;
import guru.nicks.utils.AuthUtils;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.time.Instant;

@Mapper(config = DefaultMapStructConfig.class)
public interface ExpirableHeaderMapper {

    @Mapping(target = "valuePrefix", constant = AuthUtils.BEARER_AUTH_PREFIX)
    @Mapping(target = "value", source = "accessToken")
    // see @AfterMapping
    @Mapping(target = "expirationDate", ignore = true)
    @Mapping(target = "issuedDate", ignore = true)
    ExpirableHeader toHeader(OAuth2AccessTokenDto dto);

    @AfterMapping
    default void afterMapping(@MappingTarget ExpirableHeader.ExpirableHeaderBuilder builder,
            OAuth2AccessTokenDto source) {
        // strictly speaking, token creation time is unknown; usually it's 'now' minus network latency
        Instant issuedDate = Instant.now();

        Instant expirationDate = (source.getExpiresInSeconds() == null)
                ? null
                : issuedDate.plusSeconds(source.getExpiresInSeconds());

        builder.issuedDate(issuedDate)
                .expirationDate(expirationDate);
    }

}
