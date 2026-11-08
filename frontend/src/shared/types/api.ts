export type ApiSuccess<T> = {
  success: true;
  message: string | null;
  data: T;
  timestamp: string;
};

export type ApiErrorBody = {
  success: false;
  code: string;
  message: string;
  fieldErrors?: Array<{ field: string; message: string }> | null;
  timestamp?: string;
  path?: string;
};

export type FieldErrorDetail = {
  field: string;
  message: string;
};
