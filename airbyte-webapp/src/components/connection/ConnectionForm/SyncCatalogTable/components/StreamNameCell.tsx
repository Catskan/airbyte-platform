import { Row } from "@tanstack/react-table";
import React from "react";

import { Button } from "components/ui/Button";
import { CheckBox } from "components/ui/CheckBox";
import { FlexContainer } from "components/ui/Flex";
import { TextWithOverflowTooltip } from "components/ui/Text";

import { AirbyteStreamConfiguration } from "core/api/types/AirbyteClient";

import { TextHighlighter } from "./TextHighlighter";
import { SyncStreamFieldWithId } from "../../formConfig";
import { SyncCatalogUIModel } from "../SyncCatalogTable";

interface StreamNameCellProps {
  value: string;
  row: Row<SyncCatalogUIModel>;
  updateStreamField: (streamNode: SyncStreamFieldWithId, updatedConfig: Partial<AirbyteStreamConfiguration>) => void;
  globalFilterValue?: string;
}

export const StreamNameCell: React.FC<StreamNameCellProps> = ({
  value,
  row,
  updateStreamField,
  globalFilterValue = "",
}) => {
  const { config } = row.original.streamNode;

  // expand stream and field rows
  const onToggleExpand = () => {
    row.getToggleExpandedHandler()();
    if (!row.subRows) {
      return;
    }
    row.subRows.forEach((field) => {
      if (!field.getCanExpand()) {
        return;
      }
      field.getToggleExpandedHandler()();
    });
  };

  return (
    <FlexContainer gap="none" alignItems="center">
      <CheckBox
        checkboxSize="sm"
        checked={config?.selected}
        onChange={({ target: { checked } }) => updateStreamField(row.original.streamNode, { selected: checked })}
      />
      <Button
        type="button"
        icon={row.getIsExpanded() ? "chevronDown" : "chevronRight"}
        variant="clear"
        onClick={onToggleExpand}
        disabled={!row.getCanExpand()}
      />
      <TextWithOverflowTooltip>
        <TextHighlighter searchWords={[globalFilterValue]} textToHighlight={value} />
      </TextWithOverflowTooltip>
    </FlexContainer>
  );
};
