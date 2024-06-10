package io.airbyte.workers.helper

import io.airbyte.commons.json.Jsons
import io.airbyte.config.StandardSyncOutput
import io.airbyte.config.StandardSyncSummary
import io.airbyte.config.State
import io.airbyte.config.StateType
import io.airbyte.config.StreamSyncStats
import io.airbyte.config.SyncStats
import io.airbyte.persistence.job.models.ReplicationInput
import io.airbyte.protocol.models.AirbyteGlobalState
import io.airbyte.protocol.models.AirbyteStateMessage
import io.airbyte.protocol.models.AirbyteStreamState
import io.airbyte.protocol.models.StreamDescriptor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ResumableFullRefreshStatsHelperTest {
  @ParameterizedTest
  @ValueSource(strings = ["STREAM", "GLOBAL"])
  fun `test we mark streams as resumed if they had a state in the input`(stateType: String) {
    val input =
      replicationInputWithStates(
        StateType.valueOf(stateType),
        Stream(namespace = null, name = "s0"),
        Stream(namespace = "ns", name = "s1"),
      )
    val output =
      syncOutputWithStats(
        streamSyncStats(namespace = null, name = "s0"),
        streamSyncStats(namespace = null, name = "s1"),
        streamSyncStats(namespace = "ns", name = "s0"),
        streamSyncStats(namespace = "ns", name = "s1"),
      )

    val expected =
      syncOutputWithStats(
        streamSyncStats(namespace = null, name = "s0", resumed = true),
        streamSyncStats(namespace = null, name = "s1"),
        streamSyncStats(namespace = "ns", name = "s0"),
        streamSyncStats(namespace = "ns", name = "s1", resumed = true),
      )

    ResumableFullRefreshStatsHelper().markResumedStreams(input, output)
    assertEquals(expected, output)
  }

  @Test
  fun `test we do not fail if there are no states`() {
    val input = ReplicationInput()
    val output =
      syncOutputWithStats(
        streamSyncStats(namespace = null, name = "s0"),
        streamSyncStats(namespace = null, name = "s1"),
        streamSyncStats(namespace = "ns", name = "s0"),
        streamSyncStats(namespace = "ns", name = "s1"),
      )

    // We create a new object because markResumedStreams mutates output
    val expected =
      syncOutputWithStats(
        streamSyncStats(namespace = null, name = "s0"),
        streamSyncStats(namespace = null, name = "s1"),
        streamSyncStats(namespace = "ns", name = "s0"),
        streamSyncStats(namespace = "ns", name = "s1"),
      )

    ResumableFullRefreshStatsHelper().markResumedStreams(input, output)
    assertEquals(expected, output)
  }

  private fun streamSyncStats(
    namespace: String?,
    name: String,
    resumed: Boolean = false,
  ): StreamSyncStats =
    StreamSyncStats()
      .withStreamNamespace(namespace)
      .withStreamName(name)
      .withStats(SyncStats())
      .withWasResumed(resumed)

  private fun syncOutputWithStats(vararg streamStats: StreamSyncStats): StandardSyncOutput =
    StandardSyncOutput()
      .withStandardSyncSummary(
        StandardSyncSummary()
          .withStreamStats(streamStats.toList()),
      )

  private fun replicationInputWithStates(
    stateType: StateType,
    vararg streams: Stream,
  ): ReplicationInput {
    val state: State =
      when (stateType) {
        StateType.STREAM ->
          State().withState(
            Jsons.jsonNode(
              streams.map { s ->
                AirbyteStateMessage()
                  .withType(AirbyteStateMessage.AirbyteStateType.STREAM)
                  .withStream(
                    AirbyteStreamState()
                      .withStreamState(Jsons.jsonNode("some state"))
                      .withStreamDescriptor(s.streamDescriptor),
                  )
              }.toList(),
            ),
          )
        StateType.GLOBAL ->
          State().withState(
            Jsons.jsonNode(
              listOf(
                AirbyteStateMessage()
                  .withType(AirbyteStateMessage.AirbyteStateType.GLOBAL)
                  .withGlobal(
                    AirbyteGlobalState()
                      .withSharedState(Jsons.jsonNode("shared state"))
                      .withStreamStates(
                        streams.map { s ->
                          AirbyteStreamState()
                            .withStreamState(Jsons.jsonNode("some state"))
                            .withStreamDescriptor(s.streamDescriptor)
                        }.toList(),
                      ),
                  ),
              ),
            ),
          )
        else -> throw NotImplementedError("Unsupported state type: $stateType")
      }
    return ReplicationInput().withState(state)
  }

  class Stream(val name: String, val namespace: String?) {
    val streamDescriptor: StreamDescriptor
      get() = StreamDescriptor().withName(name).withNamespace(namespace)
  }
}
