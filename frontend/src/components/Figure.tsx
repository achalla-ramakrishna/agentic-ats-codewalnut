/**
 * A question picture: an SVG drawn by the app or an uploaded PNG/JPEG. Always shown through
 * <img>, so nothing inside a picture can run in the page.
 */
export function figureSrc(figure: string): string {
  return figure.startsWith('<svg') ? `data:image/svg+xml;charset=utf-8,${encodeURIComponent(figure)}` : figure
}

export function Figure({ figure, alt = 'Question picture', small = false }: { figure: string; alt?: string; small?: boolean }) {
  return <img className={small ? 'figure figure-small' : 'figure'} src={figureSrc(figure)} alt={alt} />
}

/** Reads an image file as a data URI (PNG/JPEG, up to 1 MB) for a question picture. */
export function readPicture(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    if (!['image/png', 'image/jpeg'].includes(file.type)) {
      reject(new Error('Only PNG or JPG pictures can be used.'))
      return
    }
    if (file.size > 1024 * 1024) {
      reject(new Error('Pictures must be 1 MB or smaller.'))
      return
    }
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(new Error('The picture could not be read.'))
    reader.readAsDataURL(file)
  })
}
