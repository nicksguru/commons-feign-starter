package guru.nicks.commons.feign;

import feign.CollectionFormat;
import feign.RequestTemplate;
import feign.codec.Encoder;
import org.springframework.boot.autoconfigure.data.web.SpringDataWebProperties;
import org.springframework.cloud.openfeign.support.PageableSpringEncoder;

import java.lang.reflect.Type;

/**
 * Bugfix (needed to pass {@code sort=id,DESC} as a single query parameter instead of {@code sort=id&sort=DESC})
 * according to <a href="https://github.com/spring-cloud/spring-cloud-openfeign/issues/146#issuecomment-630917279">this
 * article</a>.
 */
public class BugfixSortPageableEncoder extends PageableSpringEncoder {

    public BugfixSortPageableEncoder(Encoder delegate, SpringDataWebProperties springDataWebProperties) {
        super(delegate);
        setPageParameter(springDataWebProperties.getPageable().getPageParameter());
        setSizeParameter(springDataWebProperties.getPageable().getSizeParameter());
        setSortParameter(springDataWebProperties.getSort().getSortParameter());
    }

    @Override
    public void encode(Object object, Type bodyType, RequestTemplate template) {
        CollectionFormat origCollectionFormat = template.collectionFormat();
        template.collectionFormat(CollectionFormat.CSV);
        super.encode(object, bodyType, template);
        template.collectionFormat(origCollectionFormat);
    }

}
