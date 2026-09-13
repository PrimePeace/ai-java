package com.ai.aijava.agent.config;

import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SpringAIVectorStoreTypes;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.autoconfigure.RedisVectorStoreProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TagField;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 覆盖 Spring AI 2.0.0 Redis 向量库自动配置：登记 kbId/docId 为 TAG，否则过滤表达式会抛
 * {@code Not allowed filter identifier name: kbId}。
 */
@Slf4j
@Configuration
@ConditionalOnClass({RedisClient.class, JedisConnectionFactory.class, RedisVectorStore.class, EmbeddingModel.class})
@EnableConfigurationProperties(RedisVectorStoreProperties.class)
@ConditionalOnProperty(name = SpringAIVectorStoreTypes.TYPE, havingValue = SpringAIVectorStoreTypes.REDIS,
        matchIfMissing = true)
public class RedisVectorStoreConfig {

    @Bean
    public RedisVectorStore vectorStore(EmbeddingModel embeddingModel,
                                        RedisVectorStoreProperties properties,
                                        JedisConnectionFactory jedisConnectionFactory,
                                        ObjectProvider<ObservationRegistry> observationRegistry,
                                        ObjectProvider<VectorStoreObservationConvention> convention,
                                        BatchingStrategy batchingStrategy) {
        RedisClient jedisClient = jedisClient(jedisConnectionFactory);
        ensureFilterableMetadata(jedisClient, properties.getIndexName());

        RedisVectorStore.Builder builder = RedisVectorStore.builder(jedisClient, embeddingModel)
                .initializeSchema(properties.isInitializeSchema())
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
                .customObservationConvention(convention.getIfAvailable())
                .batchingStrategy(batchingStrategy)
                .indexName(properties.getIndexName())
                .prefix(properties.getPrefix())
                .metadataFields(
                        RedisVectorStore.MetadataField.tag("kbId"),
                        RedisVectorStore.MetadataField.tag("docId"));
        builder.hnswM(properties.getHnsw().getM())
                .hnswEfConstruction(properties.getHnsw().getEfConstruction())
                .hnswEfRuntime(properties.getHnsw().getEfRuntime());
        return builder.build();
    }

    /**
     * initialize-schema 不会改已有索引。缺 TAG 时先 FT.ALTER，失败再 DROPINDEX（不删文档）。
     */
    private void ensureFilterableMetadata(RedisClient client, String indexName) {
        try {
            if (!client.ftList().contains(indexName)) {
                return;
            }
            String attrs = flatten(client.ftInfo(indexName).get("attributes"));
            List<SchemaField> missing = new ArrayList<>();
            if (!attrs.contains("kbId")) {
                missing.add(TagField.of("$.kbId").as("kbId"));
            }
            if (!attrs.contains("docId")) {
                missing.add(TagField.of("$.docId").as("docId"));
            }
            if (missing.isEmpty()) {
                return;
            }
            log.warn("Redis 向量索引缺少过滤字段，尝试补充 TAG index={} missing={}", indexName, missing);
            try {
                client.ftAlter(indexName, missing);
                log.info("已为 Redis 向量索引补充 TAG 字段 index={}", indexName);
            } catch (Exception alterEx) {
                log.warn("FT.ALTER 失败，删除索引后由 initialize-schema 重建（保留 kb:vector: 文档） index={}",
                        indexName, alterEx);
                client.ftDropIndex(indexName);
            }
        } catch (Exception e) {
            log.warn("检查 Redis 向量索引 schema 失败 index={}", indexName, e);
        }
    }

    private static String flatten(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Collection<?> items) {
            StringBuilder sb = new StringBuilder();
            for (Object item : items) {
                sb.append(flatten(item)).append(' ');
            }
            return sb.toString();
        }
        if (value instanceof Map<?, ?> map) {
            return flatten(map.values());
        }
        return String.valueOf(value);
    }

    private RedisClient jedisClient(JedisConnectionFactory jedisConnectionFactory) {
        JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
                .ssl(jedisConnectionFactory.isUseSsl())
                .clientName(jedisConnectionFactory.getClientName())
                .timeoutMillis(jedisConnectionFactory.getTimeout())
                .password(jedisConnectionFactory.getPassword())
                .build();
        return RedisClient.builder()
                .hostAndPort(jedisConnectionFactory.getHostName(), jedisConnectionFactory.getPort())
                .clientConfig(clientConfig)
                .build();
    }
}
