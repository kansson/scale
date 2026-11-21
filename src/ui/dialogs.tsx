import {
  AlertDialog,
  Box,
  Column,
  LinearProgressIndicator,
  ListItem,
  LoadingIndicator,
  RadioButton,
  Row,
  Text,
  TextButton,
  useMaterialColors,
} from '@expo/ui/jetpack-compose'
import { useLingui } from '@lingui/react/macro'
import {
  clip,
  composeGeometry,
  fillMaxWidth,
  selectable,
  selectableGroup,
} from '@/lib/compose-modifiers'

export type ChoiceDialogProps<Value extends string> = {
  title: string
  options: { value: Value; label: string }[]
  selected: Value
  onSelect: (value: Value) => void
  onDismiss: () => void
}

/** One choice among a few, as radio rows; choosing one closes the dialog. */
export function ChoiceDialog<Value extends string>({
  title,
  options,
  selected,
  onSelect,
  onDismiss,
}: ChoiceDialogProps<Value>) {
  const { t } = useLingui()
  const colors = useMaterialColors()

  return (
    <AlertDialog onDismissRequest={onDismiss}>
      <AlertDialog.Title>
        <Text style={{ typography: 'headlineSmall' }}>{title}</Text>
      </AlertDialog.Title>
      <AlertDialog.Text>
        <Column
          modifiers={[fillMaxWidth(), selectableGroup()]}
          verticalArrangement={{ spacedBy: 2 }}
        >
          {options.map((option) => (
            <ListItem
              key={option.value}
              colors={{ containerColor: colors.surfaceContainerHigh }}
              modifiers={[
                fillMaxWidth(),
                clip(composeGeometry.RoundedCorner(16)),
                selectable(
                  option.value === selected,
                  () => {
                    onSelect(option.value)
                    onDismiss()
                  },
                  'radioButton',
                ),
              ]}
            >
              <ListItem.LeadingContent>
                <RadioButton selected={option.value === selected} />
              </ListItem.LeadingContent>
              <ListItem.HeadlineContent>
                <Text>{option.label}</Text>
              </ListItem.HeadlineContent>
            </ListItem>
          ))}
        </Column>
      </AlertDialog.Text>
      <AlertDialog.DismissButton>
        <TextButton onClick={onDismiss}>
          <Text>{t({ message: 'Cancel', comment: 'Closes a dialog without changes' })}</Text>
        </TextButton>
      </AlertDialog.DismissButton>
    </AlertDialog>
  )
}

export type StatusDialogProps = {
  title: string
  message: string
  /** Something is under way with no measure of how far: a spinner beside the message. */
  busy?: boolean
  /** How much is done, from 0 to 1. */
  progress?: number | null
  confirmLabel?: string | null
  dismissLabel: string
  onConfirm?: () => void
  onDismiss: () => void
}

/** A dialog saying how something is going, such as an update downloading. */
export function StatusDialog({
  title,
  message,
  busy = false,
  progress = null,
  confirmLabel = null,
  dismissLabel,
  onConfirm,
  onDismiss,
}: StatusDialogProps) {
  return (
    <AlertDialog onDismissRequest={onDismiss}>
      <AlertDialog.Title>
        <Text style={{ typography: 'headlineSmall' }}>{title}</Text>
      </AlertDialog.Title>
      <AlertDialog.Text>
        <Column verticalArrangement={{ spacedBy: 16 }}>
          {busy ? (
            <Row verticalAlignment='center' horizontalArrangement={{ spacedBy: 16 }}>
              <LoadingIndicator />
              <Text>{message}</Text>
            </Row>
          ) : (
            <Text>{message}</Text>
          )}
          {progress === null ? null : (
            <LinearProgressIndicator
              progress={Math.min(1, Math.max(0, progress))}
              modifiers={[fillMaxWidth()]}
            />
          )}
        </Column>
      </AlertDialog.Text>
      <AlertDialog.ConfirmButton>
        {confirmLabel && onConfirm ? (
          <TextButton onClick={onConfirm}>
            <Text>{confirmLabel}</Text>
          </TextButton>
        ) : (
          <Box />
        )}
      </AlertDialog.ConfirmButton>
      <AlertDialog.DismissButton>
        <TextButton onClick={onDismiss}>
          <Text>{dismissLabel}</Text>
        </TextButton>
      </AlertDialog.DismissButton>
    </AlertDialog>
  )
}
