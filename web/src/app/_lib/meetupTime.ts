import type { MeetupTime } from '@/app/_lib/types';

/** English reference formatting, always using the supplied display zone rather than the host zone. */
export function meetupDateLines(time: MeetupTime, timeZone: string): string {
  const formatter = new Intl.DateTimeFormat('en-GB', {
    timeZone, weekday: 'short', day: 'numeric', month: 'short', year: 'numeric',
  });
  const date = (instant: string) => {
    const parts = formatter.formatToParts(new Date(instant));
    const part = (type: Intl.DateTimeFormatPartTypes) => parts.find(value => value.type === type)?.value ?? '';
    return `${part('weekday')} ${part('day')} ${part('month')} ${part('year')}`;
  };
  const start = date(time.startAt);
  const end = date(time.endAt);
  return start === end ? start : `${start} to\n${end}`;
}

export function meetupClockRange(time: MeetupTime, timeZone: string): string {
  const formatter = new Intl.DateTimeFormat('en-GB', { timeZone, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
  return `${formatter.format(new Date(time.startAt))} to ${formatter.format(new Date(time.endAt))}`;
}
