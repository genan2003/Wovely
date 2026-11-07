import { Directive, input } from '@angular/core';

/** Swaps a broken image for a placeholder, so a dead seller URL never shows a torn icon. */
@Directive({
  selector: 'img[appFallback]',
  host: { '(error)': 'useFallback($event)' },
})
export class ImageFallbackDirective {
  readonly appFallback = input<string>('');

  useFallback(event: Event): void {
    const image = event.target as HTMLImageElement;
    const fallback = this.appFallback() || 'placeholder-product.svg';
    if (!image.src.endsWith(fallback)) {
      image.src = fallback;
    }
  }
}
