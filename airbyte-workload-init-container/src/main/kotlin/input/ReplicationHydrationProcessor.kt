package io.airbyte.initContainer.input

import io.airbyte.commons.protocol.ProtocolSerializer
import io.airbyte.initContainer.system.FileClient
import io.airbyte.persistence.job.models.ReplicationInput
import io.airbyte.workers.ReplicationInputHydrator
import io.airbyte.workers.internal.NamespacingMapper
import io.airbyte.workers.models.ReplicationActivityInput
import io.airbyte.workers.pod.FileConstants
import io.airbyte.workers.serde.ObjectSerializer
import io.airbyte.workers.serde.PayloadDeserializer
import io.airbyte.workload.api.client.model.generated.Workload
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton

private val logger = KotlinLogging.logger {}

/**
 * Parses, hydrates and writes input files for the replication pod.
 */
@Requires(property = "airbyte.init.operation", pattern = "sync")
@Requires(property = "airbyte.init.monopod", pattern = "true")
@Singleton
class ReplicationHydrationProcessor(
  private val replicationInputHydrator: ReplicationInputHydrator,
  private val deserializer: PayloadDeserializer,
  private val serializer: ObjectSerializer,
  private val protocolSerializer: ProtocolSerializer,
  private val fileClient: FileClient,
) : InputHydrationProcessor {
  override fun process(workload: Workload) {
    logger.info { "Deserializing replication input..." }
    val rawPayload = workload.inputPayload
    val parsed: ReplicationActivityInput = deserializer.toReplicationActivityInput(rawPayload)

    logger.info { "Hydrating replication input..." }
    val hydrated: ReplicationInput = replicationInputHydrator.getHydratedReplicationInput(parsed)

    // orchestrator input
    logger.info { "Writing orchestrator inputs..." }
    fileClient.writeInputFile(
      FileConstants.INIT_INPUT_FILE,
      serializer.serialize(hydrated),
    )

    // source inputs
    logger.info { "Writing source inputs..." }
    fileClient.writeInputFile(
      FileConstants.SOURCE_CATALOG_FILE,
      protocolSerializer.serialize(hydrated.catalog, false),
    )

    fileClient.writeInputFile(
      FileConstants.SOURCE_CONFIG_FILE,
      serializer.serialize(hydrated.sourceConfiguration),
    )

    // dest inputs
    logger.info { "Writing destination inputs..." }
    val mapper =
      NamespacingMapper(
        hydrated.namespaceDefinition,
        hydrated.namespaceFormat,
        hydrated.prefix,
      )

    val destinationCatalog = mapper.mapCatalog(hydrated.catalog)

    fileClient.writeInputFile(
      FileConstants.DESTINATION_CATALOG_FILE,
      protocolSerializer.serialize(destinationCatalog, hydrated.destinationSupportsRefreshes),
    )

    fileClient.writeInputFile(
      FileConstants.DESTINATION_CONFIG_FILE,
      serializer.serialize(hydrated.destinationConfiguration),
    )

    // shared state input
    fileClient.writeInputFile(
      FileConstants.INPUT_STATE_FILE,
      serializer.serialize(hydrated.state),
    )

    // pipes for passing messages between all three
    logger.info { "Making named pipes..." }
    fileClient.makeNamedPipes()
  }
}
