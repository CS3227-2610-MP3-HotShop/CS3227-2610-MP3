'use client';

import { useState } from 'react';
import type { ImageRef } from '@/app/_lib/types';

export function ImageFrame({ image, resolveImageUrl, detail = false }: {
  readonly image: ImageRef | null;
  // BACKEND INTEGRATION: resolve only authorized references using the chosen image storage adapter.
  readonly resolveImageUrl: (image: ImageRef) => string | null;
  readonly detail?: boolean;
}) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null);
  const url = image === null ? null : resolveImageUrl(image);
  return <span className={`image-frame${detail ? ' detail-image' : ''}`}>
    {image !== null && url !== null && url !== '' && url !== failedUrl
      ? <img src={url} alt={image.alt} onError={() => { setFailedUrl(url); }} />
      : <span className="muted">{image === null ? 'No image' : 'Image unavailable'}</span>}
  </span>;
}
