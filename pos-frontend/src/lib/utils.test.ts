import { describe, it, expect } from 'vitest';
import { getApiErrorMessage } from './utils';

describe('getApiErrorMessage', () => {
  it('returns the API message when present', () => {
    const err = { response: { data: { message: 'Tenant not found' } } };
    expect(getApiErrorMessage(err)).toBe('Tenant not found');
  });

  it('prefers API message over an Error.message', () => {
    const err = {
      message: 'Network Error',
      response: { data: { message: 'Invalid credentials' } },
    };
    expect(getApiErrorMessage(err)).toBe('Invalid credentials');
  });

  it('falls back to error.message when there is no API message', () => {
    const err = new Error('Boom');
    expect(getApiErrorMessage(err)).toBe('Boom');
  });

  it('returns the string itself when given a bare string', () => {
    expect(getApiErrorMessage('A plain string')).toBe('A plain string');
  });

  it('uses the provided fallback for null / undefined / unknown shapes', () => {
    expect(getApiErrorMessage(null, 'fallback')).toBe('fallback');
    expect(getApiErrorMessage(undefined, 'fallback')).toBe('fallback');
    expect(getApiErrorMessage(42, 'fallback')).toBe('fallback');
  });

  it('uses the default fallback when no fallback is given', () => {
    expect(getApiErrorMessage(null)).toBe('Something went wrong. Please try again.');
  });

  it('treats empty API messages as missing and falls through', () => {
    const err = { response: { data: { message: '   ' } }, message: 'real reason' };
    expect(getApiErrorMessage(err)).toBe('real reason');
  });
});
