package com.erg.ergspringboot.platform.cache;

import java.util.List;
import tools.jackson.databind.ObjectMapper;

/** Module extension point for cache metadata; platform code only aggregates the descriptors. */
public interface CacheDescriptorContributor {
    List<CacheDescriptor<?>> descriptors(ObjectMapper mapper);
}
