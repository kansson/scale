import ArrowBack from '@expo/material-symbols/arrow_back.xml'
import Bluetooth from '@expo/material-symbols/bluetooth.xml'
import BluetoothDisabled from '@expo/material-symbols/bluetooth_disabled.xml'
import Check from '@expo/material-symbols/check.xml'
import CheckCircle from '@expo/material-symbols/check_circle.xml'
import ChevronRight from '@expo/material-symbols/chevron_right.xml'
import Contrast from '@expo/material-symbols/contrast.xml'
import ConversionPath from '@expo/material-symbols/conversion_path.xml'
import EcgHeart from '@expo/material-symbols/ecg_heart.xml'
import ErrorIcon from '@expo/material-symbols/error.xml'
import Info from '@expo/material-symbols/info.xml'
import Language from '@expo/material-symbols/language.xml'
import License from '@expo/material-symbols/license.xml'
import LinkOff from '@expo/material-symbols/link_off.xml'
import ScaleIcon from '@expo/material-symbols/scale.xml'
import Settings from '@expo/material-symbols/settings.xml'
import Undo from '@expo/material-symbols/undo.xml'
import Update from '@expo/material-symbols/update.xml'

/** Every icon the app draws, named for what it means rather than how it looks. */
export const glyphs = {
  autoSave: CheckCircle,
  back: ArrowBack,
  bluetooth: Bluetooth,
  bluetoothOff: BluetoothDisabled,
  drillIn: ChevronRight,
  forget: LinkOff,
  health: EcgHeart,
  info: Info,
  language: Language,
  licenses: License,
  saved: Check,
  scale: ScaleIcon,
  settings: Settings,
  theme: Contrast,
  undo: Undo,
  update: Update,
  updateChannel: ConversionPath,
  warning: ErrorIcon,
} satisfies Record<string, number>

export type Glyph = keyof typeof glyphs
