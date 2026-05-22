import { type ClassValue, clsx } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function formatPoints(value: number | null | undefined): string {
  if (value === null || value === undefined) return "—";
  return value.toLocaleString("en-US");
}

export function pad2(n: number): string {
  return n < 10 ? `0${n}` : String(n);
}

export function formatDate(input: Date | string): string {
  const d = typeof input === "string" ? new Date(input) : input;
  const y = d.getFullYear();
  const m = pad2(d.getMonth() + 1);
  const day = pad2(d.getDate());
  return `${y}/${m}/${day}`;
}

export function formatDateTime(input: Date | string): string {
  const d = typeof input === "string" ? new Date(input) : input;
  return `${formatDate(d)} ${pad2(d.getHours())}:${pad2(d.getMinutes())}`;
}
