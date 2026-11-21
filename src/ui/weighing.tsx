import {
  Box,
  Button,
  Column,
  Icon,
  LinearWavyProgressIndicator,
  Row,
  Text,
  useMaterialColors,
} from '@expo/ui/jetpack-compose'
import { useWindowDimensions } from 'react-native'
import {
  animateContentSize,
  background,
  clip,
  composeGeometry,
  fillMaxSize,
  fillMaxWidth,
  height,
  padding,
  size,
  weight,
} from '@/lib/compose-modifiers'
import {
  NavigationBarSpacer,
  WeightReadout,
  type WeightReadoutProps,
} from '../../modules/android-components'
import { type Glyph, glyphs } from './glyphs'

export type DockAction = {
  label: string
  onPress: () => void
}

export type DockProps = {
  glyph: Glyph
  title: string
  message?: string
  /** `done` once the weight is kept, `error` when something needs fixing. */
  tone?: 'neutral' | 'done' | 'error'
  /** Something is under way, such as the scale settling: a wave runs along the dock's top edge. */
  moving?: boolean
  action?: DockAction
}

export type WeighingProps = {
  figure: WeightReadoutProps
  dock: DockProps
}

/**
 * The weighing screen's body: the weight as large as the width allows in all the room above, and
 * a dock on the bottom edge saying what became of it and the one thing to do next.
 *
 * The figure is centred above room kept for the dock at its tallest, not above the dock itself,
 * so it stays put while the dock grows a button just as the weight settles and is being read.
 */
export function Weighing({ figure, dock }: WeighingProps) {
  const { fontScale } = useWindowDimensions()

  return (
    <Box contentAlignment='bottomCenter' modifiers={[fillMaxSize()]}>
      <Column modifiers={[fillMaxSize()]}>
        <Box
          contentAlignment='center'
          modifiers={[weight(1), fillMaxWidth(), padding(24, 0, 24, 0)]}
        >
          <WeightReadout {...figure} />
        </Box>
        <Box modifiers={[height(dockReserve(fontScale))]} />
        <NavigationBarSpacer />
      </Column>
      <Dock {...dock} />
    </Box>
  )
}

const cornerRadius = 28

const edgeTop = 16

/** Material's container height for a linear wavy indicator. */
const edgeHeight = 10

const rowTop = 20

const badgeSize = 56

const buttonTop = 20

const buttonHeight = 56

const bottomPadding = 24

/**
 * The dock's height above the navigation bar with a two-line message and a button, its tallest
 * usual form. Text heights follow the font scale; Android scales large text by less, so this errs
 * tall.
 */
function dockReserve(fontScale: number) {
  const text = (28 + 2 + 2 * 20) * fontScale

  return (
    edgeTop +
    edgeHeight +
    rowTop +
    Math.max(badgeSize, text) +
    buttonTop +
    buttonHeight +
    bottomPadding
  )
}

function Dock({ glyph, title, message, tone = 'neutral', moving = false, action }: DockProps) {
  const colors = useMaterialColors()

  const badge =
    tone === 'done'
      ? {
          container: colors.primary,
          content: colors.onPrimary,
          outline: composeGeometry.Material.Cookie9Sided,
        }
      : tone === 'error'
        ? {
            container: colors.errorContainer,
            content: colors.onErrorContainer,
            outline: composeGeometry.RoundedCorner(18),
          }
        : {
            container: colors.secondaryContainer,
            content: colors.onSecondaryContainer,
            outline: composeGeometry.RoundedCorner(18),
          }

  return (
    <Column
      modifiers={[
        fillMaxWidth(),
        clip(composeGeometry.RoundedCorner({ topStart: cornerRadius, topEnd: cornerRadius })),
        background(colors.surfaceContainerHigh),
        animateContentSize(),
      ]}
    >
      <LinearWavyProgressIndicator
        // Always laid out, so the dock keeps its height; it only shows while something moves.
        progress={moving ? undefined : 0}
        amplitude={moving ? undefined : 0}
        color={colors.primary}
        trackColor={moving ? colors.surfaceContainerHighest : 'transparent'}
        stopSize={0}
        modifiers={[fillMaxWidth(), padding(24, edgeTop, 24, 0)]}
      />
      <Row
        verticalAlignment='center'
        horizontalArrangement={{ spacedBy: 16 }}
        modifiers={[fillMaxWidth(), padding(24, rowTop, 24, 0)]}
      >
        <Box
          contentAlignment='center'
          modifiers={[size(badgeSize, badgeSize), clip(badge.outline), background(badge.container)]}
        >
          <Icon source={glyphs[glyph]} size={28} tint={badge.content} />
        </Box>
        <Column modifiers={[weight(1)]} verticalArrangement={{ spacedBy: 2 }}>
          <Text color={colors.onSurface} style={{ typography: 'titleLarge' }}>
            {title}
          </Text>
          {message ? (
            <Text color={colors.onSurfaceVariant} style={{ typography: 'bodyMedium' }}>
              {message}
            </Text>
          ) : null}
        </Column>
      </Row>
      {action ? <DockButton action={action} /> : null}
      <Box modifiers={[height(bottomPadding)]} />
      <NavigationBarSpacer />
    </Column>
  )
}

function DockButton({ action }: { action: DockAction }) {
  const colors = useMaterialColors()

  return (
    <Box modifiers={[fillMaxWidth(), padding(24, buttonTop, 24, 0)]}>
      <Button onClick={action.onPress} modifiers={[fillMaxWidth(), height(buttonHeight)]}>
        <Text color={colors.onPrimary} style={{ typography: 'labelLarge' }}>
          {action.label}
        </Text>
      </Button>
    </Box>
  )
}
