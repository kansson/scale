import {
  Column,
  Icon,
  LazyColumn,
  ListItem,
  Switch,
  Text,
  useMaterialColors,
} from '@expo/ui/jetpack-compose'
import type { ReactNode } from 'react'
import {
  animateContentSize,
  clickable,
  clip,
  composeGeometry,
  fillMaxSize,
  fillMaxWidth,
  padding,
  toggleable,
} from '@/lib/compose-modifiers'
import { type Glyph, glyphs } from './glyphs'

export type ListProps = {
  children: ReactNode
  bottomPadding?: number
  /** Card groups inset from the screen edges, as in settings. */
  grouped?: boolean
}

/**
 * The space between a grouped list's cards, and above the first: a collapsed app bar sits right on
 * the list, so the first card keeps the same distance from it as from the next card.
 */
const groupedGap = 20

/** A vertically scrolling list owning the screen body; every child is one lazy item. */
export function List({ children, bottomPadding = 16, grouped = false }: ListProps) {
  return (
    <LazyColumn
      modifiers={[fillMaxSize()]}
      contentPadding={
        grouped
          ? { start: 16, end: 16, top: groupedGap, bottom: Math.max(bottomPadding, 32) }
          : { top: 4, bottom: bottomPadding }
      }
      verticalArrangement={grouped ? { spacedBy: groupedGap } : undefined}
    >
      {children}
    </LazyColumn>
  )
}

export type GroupRow = {
  key: string
  label: string
  /** One short line; rows without it stay single-line with the glyph centred. */
  value?: string
  glyph?: Glyph
  /** A trailing chevron: the row opens a screen where its value can be changed. */
  drillIn?: boolean
  /** A trailing switch for a setting that is on or off; the whole row flips it. */
  checked?: boolean
  onPress?: () => void
}

/** Where a row sits in a run of card rows: the run's outer corners are round, its joins tight. */
function cardCorners(index: number, count: number) {
  const top = index === 0 ? 20 : 4
  const bottom = index === count - 1 ? 20 : 4

  return composeGeometry.RoundedCorner({
    topStart: top,
    topEnd: top,
    bottomStart: bottom,
    bottomEnd: bottom,
  })
}

/** The title above a group, in the primary colour like Material settings headers. */
function GroupTitle({ title }: { title: string }) {
  const colors = useMaterialColors()

  return (
    <Text
      color={colors.primary}
      modifiers={[padding(4, 0, 4, 0)]}
      style={{ typography: 'titleSmall' }}
    >
      {title}
    </Text>
  )
}

/**
 * What tapping a row does. A switch row is one toggle for TalkBack, announced with its state; its
 * switch takes no input of its own.
 */
function rowPress({ checked, onPress }: GroupRow) {
  if (!onPress) return []

  return checked === undefined
    ? [clickable(onPress)]
    : [toggleable(checked, onPress, { role: 'switch' })]
}

/**
 * A titled group of connected card rows: the group's outer corners are round, the joins between
 * rows are tight, like Material settings lists.
 */
export function Group({ title, rows }: { title?: string; rows: GroupRow[] }) {
  const colors = useMaterialColors()

  return (
    <Column modifiers={[fillMaxWidth()]} verticalArrangement={{ spacedBy: 8 }}>
      {title ? <GroupTitle title={title} /> : null}
      <Column
        modifiers={[fillMaxWidth(), animateContentSize()]}
        verticalArrangement={{ spacedBy: 2 }}
      >
        {rows.map((row, index) => (
          <ListItem
            // Compose reads a ListItem's slots once, so a row that gains a slot is drawn afresh.
            key={`${row.key}:${row.glyph ? 'g' : ''}${row.value ? 'v' : ''}${row.drillIn ? 'd' : ''}${row.checked === undefined ? '' : 's'}`}
            colors={{ containerColor: colors.surfaceContainerHigh }}
            modifiers={[fillMaxWidth(), clip(cardCorners(index, rows.length)), ...rowPress(row)]}
          >
            {row.glyph ? (
              <ListItem.LeadingContent>
                <Icon source={glyphs[row.glyph]} size={24} tint={colors.onSurfaceVariant} />
              </ListItem.LeadingContent>
            ) : null}
            <ListItem.HeadlineContent>
              <Text>{row.label}</Text>
            </ListItem.HeadlineContent>
            {row.value ? (
              <ListItem.SupportingContent>
                <Text color={colors.onSurfaceVariant} maxLines={1} overflow='ellipsis'>
                  {row.value}
                </Text>
              </ListItem.SupportingContent>
            ) : null}
            {row.drillIn ? (
              <ListItem.TrailingContent>
                <Icon source={glyphs.drillIn} size={24} tint={colors.onSurfaceVariant} />
              </ListItem.TrailingContent>
            ) : row.checked === undefined ? null : (
              <ListItem.TrailingContent>
                <Switch value={row.checked} />
              </ListItem.TrailingContent>
            )}
          </ListItem>
        ))}
      </Column>
    </Column>
  )
}

/**
 * A paragraph of running text in a plain list, such as a licence, inset from the screen edges and
 * set apart from the next one.
 */
export function Paragraph({ children }: { children: string }) {
  const colors = useMaterialColors()

  return (
    <Text
      color={colors.onSurfaceVariant}
      style={{ typography: 'bodyMedium' }}
      modifiers={[fillMaxWidth(), padding(16, 0, 16, 12)]}
    >
      {children}
    </Text>
  )
}
