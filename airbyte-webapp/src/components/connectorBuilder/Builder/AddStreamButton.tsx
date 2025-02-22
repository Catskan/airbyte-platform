import { yupResolver } from "@hookform/resolvers/yup";
import classNames from "classnames";
import merge from "lodash/merge";
import React, { useState } from "react";
import { FormProvider, useForm, useFormContext } from "react-hook-form";
import { FormattedMessage, useIntl } from "react-intl";
import { v4 as uuid } from "uuid";
import * as yup from "yup";

import { Button } from "components/ui/Button";
import { Modal, ModalBody, ModalFooter } from "components/ui/Modal";

import { Action, Namespace, useAnalyticsService } from "core/services/analytics";

import styles from "./AddStreamButton.module.scss";
import { BuilderField } from "./BuilderField";
import { BuilderFieldWithInputs } from "./BuilderFieldWithInputs";
import { BuilderStream, DEFAULT_BUILDER_STREAM_VALUES, DEFAULT_SCHEMA, useBuilderWatch } from "../types";

interface AddStreamValues {
  streamName: string;
  urlPath: string;
  copyOtherStream?: boolean;
  streamToCopy?: string;
}

interface AddStreamButtonProps {
  onAddStream: (addedStreamNum: number) => void;
  button?: React.ReactElement;
  initialValues?: Partial<BuilderStream>;
  "data-testid"?: string;
  modalTitle?: string;
  disabled?: boolean;
}

export const AddStreamButton: React.FC<AddStreamButtonProps> = ({
  onAddStream,
  button,
  initialValues,
  "data-testid": testId,
  modalTitle,
  disabled,
}) => {
  const analyticsService = useAnalyticsService();
  const baseUrl = useBuilderWatch("formValues.global.urlBase");
  const [isOpen, setIsOpen] = useState(false);

  const streams = useBuilderWatch("formValues.streams");
  const { setValue } = useFormContext();
  const numStreams = streams.length;

  const buttonClickHandler = () => {
    setIsOpen(true);
  };

  const shouldPulse = numStreams === 0 && baseUrl;

  const handleSubmit = (values: AddStreamValues) => {
    const otherStreamValues = values.copyOtherStream
      ? streams.find((stream) => stream.name === values.streamToCopy)
      : undefined;
    const id = uuid();
    setValue("formValues.streams", [
      ...streams,
      merge({}, DEFAULT_BUILDER_STREAM_VALUES, {
        ...initialValues,
        ...otherStreamValues,
        name: values.streamName,
        urlPath: values.urlPath,
        schema: DEFAULT_SCHEMA,
        id,
        testResults: {
          // indicates that this stream was added by the Builder and needs to be tested
          streamHash: null,
        },
      }),
    ]);
    setIsOpen(false);
    onAddStream(numStreams);
    if (initialValues) {
      analyticsService.track(Namespace.CONNECTOR_BUILDER, Action.STREAM_COPY, {
        actionDescription: "Existing stream copied into a new stream",
        existing_stream_id: initialValues.id,
        existing_stream_name: initialValues.name,
        new_stream_id: id,
        new_stream_name: values.streamName,
        new_stream_url_path: values.urlPath,
      });
    } else {
      analyticsService.track(Namespace.CONNECTOR_BUILDER, Action.STREAM_CREATE, {
        actionDescription: "New stream created from the Add Stream button",
        stream_id: id,
        stream_name: values.streamName,
        url_path: values.urlPath,
      });
    }
  };

  return (
    <>
      {button ? (
        React.cloneElement(button, {
          onClick: buttonClickHandler,
          "data-testid": testId,
          disabled: disabled ?? button.props.disabled, // respect `disabled` from both AddStreamButton and the custom button
          className: classNames(button.props.className, styles.disableable),
        })
      ) : (
        <div className={classNames(styles.buttonContainer, { [styles["buttonContainer--pulse"]]: shouldPulse })}>
          <Button
            type="button"
            className={classNames(styles.addButton, styles.disableable)}
            onClick={buttonClickHandler}
            icon="plus"
            data-testid={testId}
            disabled={disabled}
          />
        </div>
      )}
      {isOpen && (
        <Modal
          size="sm"
          title={modalTitle ?? <FormattedMessage id="connectorBuilder.addStreamModal.title" />}
          onCancel={() => {
            setIsOpen(false);
          }}
        >
          <AddStreamForm
            onSubmit={handleSubmit}
            onCancel={() => setIsOpen(false)}
            showCopyFromStream={!initialValues && numStreams > 0}
            streams={streams}
            initialUrlPath={initialValues?.urlPath}
          />
        </Modal>
      )}
    </>
  );
};

const AddStreamForm = ({
  onSubmit,
  onCancel,
  showCopyFromStream,
  streams,
  initialUrlPath,
}: {
  onSubmit: (values: AddStreamValues) => void;
  onCancel: () => void;
  showCopyFromStream: boolean;
  streams: BuilderStream[];
  initialUrlPath?: string;
}) => {
  const { formatMessage } = useIntl();
  const methods = useForm({
    defaultValues: {
      streamName: "",
      urlPath: initialUrlPath ?? "",
      copyOtherStream: false,
      streamToCopy: streams[0]?.name,
    },
    resolver: yupResolver(
      yup.object().shape({
        streamName: yup
          .string()
          .required("form.empty.error")
          .notOneOf(
            streams.map((stream) => stream.name),
            "connectorBuilder.duplicateStreamName"
          ),
        urlPath: yup.string().required("form.empty.error"),
      })
    ),
    mode: "onChange",
  });

  const useOtherStream = methods.watch("copyOtherStream");

  return (
    <FormProvider {...methods}>
      <form onSubmit={methods.handleSubmit(onSubmit)}>
        <ModalBody className={styles.body}>
          <BuilderField
            path="streamName"
            type="string"
            label={formatMessage({ id: "connectorBuilder.addStreamModal.streamNameLabel" })}
            tooltip={formatMessage({ id: "connectorBuilder.addStreamModal.streamNameTooltip" })}
          />
          <BuilderFieldWithInputs
            path="urlPath"
            type="string"
            label={formatMessage({ id: "connectorBuilder.addStreamModal.urlPathLabel" })}
            tooltip={formatMessage({ id: "connectorBuilder.addStreamModal.urlPathTooltip" })}
          />
          {/* Only allow to copy from another stream within the modal if there aren't initial values set already and there are other streams */}
          {showCopyFromStream && (
            <>
              <BuilderField
                path="copyOtherStream"
                type="boolean"
                label={formatMessage({ id: "connectorBuilder.addStreamModal.copyOtherStreamLabel" })}
              />
              {useOtherStream && (
                <BuilderField
                  label={formatMessage({ id: "connectorBuilder.addStreamModal.streamLabel" })}
                  path="streamToCopy"
                  type="enum"
                  options={streams.map((stream) => stream.name)}
                />
              )}
            </>
          )}
        </ModalBody>
        <ModalFooter>
          <Button
            variant="secondary"
            type="reset"
            onClick={() => {
              onCancel();
            }}
          >
            <FormattedMessage id="form.cancel" />
          </Button>
          <Button type="submit">
            <FormattedMessage id="form.create" />
          </Button>
        </ModalFooter>
      </form>
    </FormProvider>
  );
};
