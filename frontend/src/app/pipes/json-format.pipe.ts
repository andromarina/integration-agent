import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'jsonFormat',
  standalone: true,
})
export class JsonFormatPipe implements PipeTransform {
  transform(value: unknown): string {
    if (value === null || value === undefined) {
      return '';
    }
    if (typeof value === 'string') {
      return value;
    }
    try {
      return JSON.stringify(value, null, 2);
    } catch {
      return String(value);
    }
  }
}
