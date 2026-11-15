package io.github.tusharsri.blueprint.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Enables @Cacheable/@CachePut/@CacheEvict. The Caffeine provider (TTL, max size, stats) is configured
 * in application.yml under spring.cache.caffeine.spec. Set spring.cache.type=none to turn caching off.
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
