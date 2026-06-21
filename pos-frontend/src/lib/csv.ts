const CSV_INJECTION_PREFIX = /^[=+\-@\t\r]/;
const UTF8_BOM = "﻿";

export function escapeCsvCell(v: unknown): string {
  let str = String(v ?? "");
  if (CSV_INJECTION_PREFIX.test(str)) str = "'" + str;
  return `"${str.replace(/"/g, '""')}"`;
}

export function downloadCsv(filename: string, headers: string[], rows: unknown[][]): void {
  const lines = [
    headers.map(escapeCsvCell).join(","),
    ...rows.map((r) => r.map(escapeCsvCell).join(",")),
  ];
  const csv = UTF8_BOM + lines.join("\r\n");
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

const EXPORT_PAGE_SIZE = 200;

export async function fetchAllPages<T>(
  fetcher: (page: number, size: number) => Promise<{ content: T[]; totalPages: number }>,
): Promise<T[]> {
  const first = await fetcher(0, EXPORT_PAGE_SIZE);
  if (first.totalPages <= 1) return first.content;
  const rest = await Promise.all(
    Array.from({ length: first.totalPages - 1 }, (_, i) =>
      fetcher(i + 1, EXPORT_PAGE_SIZE),
    ),
  );
  return [...first.content, ...rest.flatMap((p) => p.content)];
}
