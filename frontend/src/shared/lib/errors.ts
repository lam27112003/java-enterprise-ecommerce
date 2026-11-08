import type { FieldErrorDetail } from '../types/api';

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: FieldErrorDetail[];
  readonly path?: string;

  constructor(
    status: number,
    code: string,
    message: string,
    fieldErrors: FieldErrorDetail[] = [],
    path?: string,
  ) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.path = path;
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

/** Prefer backend message for 429 / RATE_LIMIT_EXCEEDED; clear fallback otherwise. */
export function rateLimitOrApiMessage(error: unknown, fallback: string): string {
  if (isApiError(error)) {
    if (error.status === 429 || error.code === 'RATE_LIMIT_EXCEEDED') {
      return error.message || 'Too many requests. Please wait a moment and try again.';
    }
    return error.message;
  }
  return fallback;
}
