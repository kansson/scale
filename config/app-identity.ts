export function appIdentity(value: string | undefined) {
  const variant = value ?? 'development'

  if (variant !== 'development' && variant !== 'preview' && variant !== 'production') {
    throw new Error(`Unknown APP_VARIANT: ${variant}`)
  }

  const suffix = variant === 'production' ? '' : variant === 'development' ? '.dev' : '.preview'

  return {
    variant,
    name:
      variant === 'production'
        ? 'Scale'
        : variant === 'development'
          ? 'Scale Dev'
          : 'Scale Preview',
    androidPackage: `app.scale.android${suffix}`,
    scheme:
      variant === 'production'
        ? 'scale'
        : variant === 'development'
          ? 'scale-dev'
          : 'scale-preview',
  }
}
