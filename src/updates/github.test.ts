import { afterEach, describe, expect, it, vi } from 'vitest'
import { listReleases, readUpdateManifest } from './github'

const checksum = 'a'.repeat(64)

/** Answers every request with `json`, the body GitHub would send. */
function answer(json: string, status = 200) {
  vi.stubGlobal('fetch', async () => new Response(json, { status }))
}

function asset(
  name: string,
  url = `https://github.com/kansson/scale/releases/download/v1/${name}`,
) {
  return { name, size: 10, browser_download_url: url, digest: `sha256:${checksum.toUpperCase()}` }
}

function release(tag: string, publishedAt: string | null, assets = [asset('update.json')]) {
  return { tag_name: tag, draft: false, prerelease: false, published_at: publishedAt, assets }
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('listReleases', () => {
  it('lists published releases newest first', async () => {
    answer(
      JSON.stringify([
        release('v1.0.0', '2026-10-01T03:00:00Z'),
        release('v1.1.0', '2026-10-02T03:00:00Z'),
        release('v1.2.0', null),
        { ...release('v1.3.0', '2026-10-03T03:00:00Z'), draft: true },
      ]),
    )

    expect((await listReleases()).map((item) => item.tag)).toEqual(['v1.1.0', 'v1.0.0'])
  })

  it('keeps only files the repository publishes, with their checksum', async () => {
    answer(
      JSON.stringify([
        release('v1.0.0', '2026-10-01T03:00:00Z', [
          asset('scale-1.0.0-arm64-v8a.apk'),
          asset('scale-1.0.0-x86_64.apk', 'https://example.com/scale-1.0.0-x86_64.apk'),
          asset('update.json', 'https://github.com/someone/else/releases/download/v1/update.json'),
        ]),
      ]),
    )

    const [found] = await listReleases()

    expect(found?.assets).toEqual([
      {
        name: 'scale-1.0.0-arm64-v8a.apk',
        size: 10,
        url: 'https://github.com/kansson/scale/releases/download/v1/scale-1.0.0-arm64-v8a.apk',
        sha256: checksum,
      },
    ])
  })

  it('has no checksum for a digest that is not SHA-256', async () => {
    answer(
      JSON.stringify([
        release('v1.0.0', '2026-10-01T03:00:00Z', [
          { ...asset('scale-1.0.0-arm64-v8a.apk'), digest: 'md5:abc' },
        ]),
      ]),
    )

    expect((await listReleases())[0]?.assets[0]?.sha256).toBeNull()
  })

  it('fails when GitHub does not answer with releases', async () => {
    answer(JSON.stringify({ message: 'API rate limit exceeded' }), 403)

    await expect(listReleases()).rejects.toThrow('GitHub answered 403')
  })
})

describe('readUpdateManifest', () => {
  it('reads the version a release holds', async () => {
    answer(JSON.stringify({ versionName: '1.1.0', versionCode: 420_000, channel: 'stable' }))

    await expect(readUpdateManifest('https://github.com/update.json')).resolves.toEqual({
      versionName: '1.1.0',
      versionCode: 420_000,
      channel: 'stable',
    })
  })

  it('rejects a manifest without a whole version code', async () => {
    answer(JSON.stringify({ versionName: '1.1.0', versionCode: '420000', channel: 'stable' }))

    await expect(readUpdateManifest('https://github.com/update.json')).rejects.toThrow()
  })
})
