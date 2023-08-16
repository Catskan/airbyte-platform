/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.config.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.airbyte.commons.json.Jsons;
import io.airbyte.config.ActorDefinitionConfigInjection;
import io.airbyte.config.ActorDefinitionVersion;
import io.airbyte.config.StandardSourceDefinition;
import io.airbyte.protocol.models.ConnectorSpecification;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfigInjectionTest extends BaseConfigDatabaseTest {

  private ConfigRepository configRepository;
  private ConfigInjector configInjector;

  private StandardSourceDefinition sourceDefinition;

  private JsonNode exampleConfig;

  private static final String SAMPLE_CONFIG_KEY = "my_config_key";
  private static final String SAMPLE_INJECTED_KEY = "injected_under";

  ConfigInjectionTest() {}

  @BeforeEach
  void beforeEach() throws Exception {
    truncateAllTables();
    configRepository = new ConfigRepository(database, MockData.MAX_SECONDS_BETWEEN_MESSAGE_SUPPLIER);
    configInjector = new ConfigInjector(configRepository);
    exampleConfig = Jsons.jsonNode(Map.of(SAMPLE_CONFIG_KEY, 123));
  }

  @Test
  void testInject() throws IOException {
    createBaseObjects();

    final JsonNode injected = configInjector.injectConfig(exampleConfig, sourceDefinition.getSourceDefinitionId());
    assertEquals(123, injected.get(SAMPLE_CONFIG_KEY).longValue(), 123);
    assertEquals("a", injected.get("a").get(SAMPLE_INJECTED_KEY).asText());
    assertEquals("b", injected.get("b").get(SAMPLE_INJECTED_KEY).asText());
    assertFalse(injected.has("c"));
  }

  @Test
  void testInjectOverwrite() throws IOException {
    createBaseObjects();

    ((ObjectNode) exampleConfig).set("a", new LongNode(123));
    ((ObjectNode) exampleConfig).remove(SAMPLE_CONFIG_KEY);

    final JsonNode injected = configInjector.injectConfig(exampleConfig, sourceDefinition.getSourceDefinitionId());
    assertEquals("a", injected.get("a").get(SAMPLE_INJECTED_KEY).asText());
    assertEquals("b", injected.get("b").get(SAMPLE_INJECTED_KEY).asText());
    assertFalse(injected.has("c"));
  }

  @Test
  void testUpdate() throws IOException {
    createBaseObjects();

    // write an injection object with the same definition id and the same injection path - will update
    // the existing one
    configRepository.writeActorDefinitionConfigInjectionForPath(new ActorDefinitionConfigInjection()
        .withActorDefinitionId(sourceDefinition.getSourceDefinitionId()).withInjectionPath("a").withJsonToInject(new TextNode("abc")));

    final JsonNode injected = configInjector.injectConfig(exampleConfig, sourceDefinition.getSourceDefinitionId());
    assertEquals(123, injected.get(SAMPLE_CONFIG_KEY).longValue(), 123);
    assertEquals("abc", injected.get("a").asText());
    assertEquals("b", injected.get("b").get(SAMPLE_INJECTED_KEY).asText());
    assertFalse(injected.has("c"));
  }

  @Test
  void testCreate() throws IOException {
    createBaseObjects();

    // write an injection object with the same definition id and a new injection path - will create a
    // new one and leave the others in place
    configRepository.writeActorDefinitionConfigInjectionForPath(new ActorDefinitionConfigInjection()
        .withActorDefinitionId(sourceDefinition.getSourceDefinitionId()).withInjectionPath("c").withJsonToInject(new TextNode("thirdInject")));

    final JsonNode injected = configInjector.injectConfig(exampleConfig, sourceDefinition.getSourceDefinitionId());
    assertEquals(123, injected.get(SAMPLE_CONFIG_KEY).longValue());
    assertEquals("a", injected.get("a").get(SAMPLE_INJECTED_KEY).asText());
    assertEquals("b", injected.get("b").get(SAMPLE_INJECTED_KEY).asText());
    assertEquals("thirdInject", injected.get("c").asText());
  }

  private void createBaseObjects() throws IOException {
    sourceDefinition = createBaseSourceDef();
    final ActorDefinitionVersion actorDefinitionVersion = createBaseActorDefVersion(sourceDefinition.getSourceDefinitionId());
    configRepository.writeSourceDefinitionAndDefaultVersion(sourceDefinition, actorDefinitionVersion);

    createInjection(sourceDefinition, "a");
    createInjection(sourceDefinition, "b");

    // unreachable injection, should not show up
    final StandardSourceDefinition otherSourceDefinition = createBaseSourceDef();
    final ActorDefinitionVersion actorDefinitionVersion2 = createBaseActorDefVersion(otherSourceDefinition.getSourceDefinitionId());
    configRepository.writeSourceDefinitionAndDefaultVersion(otherSourceDefinition, actorDefinitionVersion2);
    createInjection(otherSourceDefinition, "c");
  }

  private ActorDefinitionConfigInjection createInjection(final StandardSourceDefinition definition, final String path)
      throws IOException {
    final ActorDefinitionConfigInjection injection = new ActorDefinitionConfigInjection().withActorDefinitionId(definition.getSourceDefinitionId())
        .withInjectionPath(path).withJsonToInject(Jsons.jsonNode(Map.of(SAMPLE_INJECTED_KEY, path)));

    configRepository.writeActorDefinitionConfigInjectionForPath(injection);
    return injection;
  }

  private static StandardSourceDefinition createBaseSourceDef() {
    final UUID id = UUID.randomUUID();

    return new StandardSourceDefinition()
        .withName("source-def-" + id)
        .withSourceDefinitionId(id)
        .withTombstone(false);
  }

  private static ActorDefinitionVersion createBaseActorDefVersion(final UUID actorDefId) {
    return new ActorDefinitionVersion()
        .withVersionId(UUID.randomUUID())
        .withActorDefinitionId(actorDefId)
        .withDockerRepository("source-image-" + actorDefId)
        .withDockerImageTag("1.0.0")
        .withSpec(new ConnectorSpecification().withProtocolVersion("0.1.0"));
  }

}
