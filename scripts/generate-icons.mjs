import { mkdir, readFile } from 'node:fs/promises'
import sharp from 'sharp'

const directory = new URL('../assets/images/', import.meta.url)

await mkdir(directory, { recursive: true })

const mark = await readFile(new URL('../assets/icon.svg', import.meta.url), 'utf8')

// The rounded background square gives the launcher colour; the rest is the symbol.
const background = /<rect[^>]*fill="([^"]+)"/u.exec(mark)?.[1] ?? '#000000'

const symbol = mark.replace(/<svg[^>]*>|<\/svg>|<rect[^>]*\/>/gu, '').trim()

const foreground = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"><g transform="translate(22 22)">${symbol}</g></svg>`

const monochrome = foreground.replaceAll(/#[0-9a-f]{3,8}\b/giu, '#ffffff')

for (const [name, svg] of [
  ['icon', mark],
  ['android-icon-foreground', foreground],
  ['android-icon-monochrome', monochrome],
]) {
  const asset = sharp(Buffer.from(svg)).resize(1024, 1024)

  if (name === 'icon') asset.flatten({ background })
  await asset.png().toFile(new URL(`${name}.png`, directory).pathname)
}
