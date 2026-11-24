export interface FieldError {
  field: string;
  message: string;
}

export interface ProblemDetails {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  code?: string;
  requestId?: string;
  timestamp?: string;
  errors?: FieldError[];
  [key: string]: unknown;
}

export class ApiError extends Error {
  readonly type: string;
  readonly status: number;
  readonly detail: string;
  readonly instance?: string;
  readonly code: string;
  readonly requestId: string;
  readonly timestamp?: string;
  readonly errors: FieldError[];

  constructor(problem: ProblemDetails) {
    super(problem.detail || problem.title || 'An API error occurred');
    this.name = 'ApiError';
    this.type = problem.type || 'urn:minicard:problem:internal';
    this.status = problem.status || 500;
    this.detail = problem.detail || '';
    this.instance = problem.instance;
    this.code = problem.code || 'INTERNAL_ERROR';
    this.requestId = problem.requestId || '';
    this.timestamp = problem.timestamp;
    this.errors = problem.errors || [];
  }

  hasFieldError(field: string): boolean {
    return this.errors.some((e) => e.field === field);
  }

  getFieldError(field: string): string | undefined {
    return this.errors.find((e) => e.field === field)?.message;
  }
}
