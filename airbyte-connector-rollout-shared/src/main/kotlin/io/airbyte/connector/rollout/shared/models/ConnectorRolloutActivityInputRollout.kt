package io.airbyte.connector.rollout.shared.models

import io.airbyte.config.ConnectorEnumRolloutStrategy
import java.util.UUID

data class ConnectorRolloutActivityInputRollout(
  var dockerRepository: String,
  var dockerImageTag: String,
  var actorDefinitionId: UUID,
  var rolloutId: UUID,
  var actorIds: List<UUID>?,
  var targetPercentage: Int?,
  var updatedBy: UUID? = null,
  var rolloutStrategy: ConnectorEnumRolloutStrategy? = null,
)
