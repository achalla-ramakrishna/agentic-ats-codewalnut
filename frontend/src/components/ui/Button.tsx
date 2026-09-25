import type { ButtonHTMLAttributes } from 'react'

type Variant = 'primary' | 'secondary' | 'ghost'

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  size?: 'md' | 'sm'
}

export function Button({ variant = 'primary', size = 'md', className = '', type = 'button', ...rest }: Props) {
  const classes = ['btn', `btn-${variant}`, size === 'sm' ? 'btn-sm' : '', className].filter(Boolean).join(' ')
  return <button type={type} className={classes} {...rest} />
}
