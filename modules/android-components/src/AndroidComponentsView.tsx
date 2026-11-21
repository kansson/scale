import { requireNativeView } from 'expo'
import type * as React from 'react'
import type { ReactNode } from 'react'
import type { NativeSyntheticEvent } from 'react-native'
import type { AppBarProps, ScaffoldProps } from './AndroidComponents.types'

type SlotProps = {
  slotName: string
  children: ReactNode
}

const SlotNativeView: React.ComponentType<SlotProps> = requireNativeView('ExpoUI', 'SlotView')

const NativeScaffoldView: React.ComponentType<
  Omit<ScaffoldProps, 'topBar' | 'bottomBar' | 'floatingActionButton' | 'onSnackbarDismiss'> & {
    onSnackbarDismiss?: (event: NativeSyntheticEvent<Record<string, never>>) => void
  }
> = requireNativeView('AndroidComponents', 'ScaffoldView')

type NativeAppBarProps = Omit<AppBarProps, 'navigationIcon' | 'actions' | 'titleContent'> & {
  children?: ReactNode
}

const NativeTopAppBarView: React.ComponentType<NativeAppBarProps> = requireNativeView(
  'AndroidComponents',
  'TopAppBarView',
)

const NativeLargeTopAppBarView: React.ComponentType<NativeAppBarProps> = requireNativeView(
  'AndroidComponents',
  'LargeTopAppBarView',
)

export type WeightReadoutProps = {
  amount: string
  unit: string
  /** A placeholder before there is a weight, still moving, or settled. */
  state: 'waiting' | 'settling' | 'settled'
}

const NativeWeightReadoutView: React.ComponentType<WeightReadoutProps> = requireNativeView(
  'AndroidComponents',
  'WeightReadoutView',
)

/**
 * A weight in large tabular figures with its unit beside it, as large as the width allows. Light
 * and muted while the scale moves, heavy once it settles, faint as a placeholder.
 */
export function WeightReadout(props: WeightReadoutProps) {
  return <NativeWeightReadoutView {...props} />
}

const NativeNavigationBarSpacerView: React.ComponentType<Record<string, never>> = requireNativeView(
  'AndroidComponents',
  'NavigationBarSpacerView',
)

/** As tall as the system navigation bar, for a surface drawn to the bottom edge of the screen. */
export function NavigationBarSpacer() {
  return <NativeNavigationBarSpacerView />
}

function Slot(props: SlotProps) {
  return <SlotNativeView {...props} />
}

export function Scaffold(props: ScaffoldProps) {
  const { topBar, bottomBar, floatingActionButton, children, onSnackbarDismiss, ...restProps } =
    props

  return (
    <NativeScaffoldView
      {...restProps}
      onSnackbarDismiss={onSnackbarDismiss ? () => onSnackbarDismiss() : undefined}
    >
      {/* The native Scaffold resolves slots once, so every slot stays mounted. */}
      <Slot slotName='topBar'>{topBar}</Slot>
      <Slot slotName='bottomBar'>{bottomBar}</Slot>
      <Slot slotName='floatingActionButton'>{floatingActionButton}</Slot>
      {children}
    </NativeScaffoldView>
  )
}

function appBarSlots({ navigationIcon, actions, titleContent }: AppBarProps) {
  return (
    <>
      {titleContent ? <Slot slotName='title'>{titleContent}</Slot> : null}
      {navigationIcon ? <Slot slotName='navigationIcon'>{navigationIcon}</Slot> : null}
      {/* Native app bars resolve slots once; actions that appear after loading need it mounted. */}
      <Slot slotName='actions'>{actions}</Slot>
    </>
  )
}

export function TopAppBar(props: AppBarProps) {
  const {
    navigationIcon: _navigationIcon,
    actions: _actions,
    titleContent: _title,
    ...rest
  } = props

  return <NativeTopAppBarView {...rest}>{appBarSlots(props)}</NativeTopAppBarView>
}

export function LargeTopAppBar(props: AppBarProps) {
  const {
    navigationIcon: _navigationIcon,
    actions: _actions,
    titleContent: _title,
    ...rest
  } = props

  return <NativeLargeTopAppBarView {...rest}>{appBarSlots(props)}</NativeLargeTopAppBarView>
}
