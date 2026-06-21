import { useEffect, useRef, useCallback } from 'react';

interface UseBarcodeScannerProps {
  onScan: (barcode: string) => void;
  /** Maximum time (ms) allowed between keystrokes to be considered a scanner */
  bufferTimeoutMs?: number;
  /** Minimum length of a valid barcode */
  minLength?: number;
  /** Optionally disable the scanner listener */
  disabled?: boolean;
}

/**
 * Global hook to intercept physical Barcode Scanner input.
 * Scanners act as high-speed keyboards that terminate with an 'Enter' key.
 * This hook filters out human typing by enforcing strict timing thresholds.
 */
export function useBarcodeScanner({
  onScan,
  bufferTimeoutMs = 50,
  minLength = 4,
  disabled = false,
}: UseBarcodeScannerProps) {
  const buffer = useRef<string>('');
  const lastKeyTime = useRef<number>(0);
  const timeoutId = useRef<NodeJS.Timeout | null>(null);

  const handleKeyDown = useCallback(
    (e: KeyboardEvent) => {
      if (disabled) return;

      // Ignore inputs if the user is explicitly typing into a text field or textarea
      // (unless the text field is specifically designed to receive barcode scans, 
      // but usually we want global capture to work even if nothing is focused)
      const target = e.target as HTMLElement;
      const isInputFocused = target.tagName === 'INPUT' || target.tagName === 'TEXTAREA';
      
      // Some events (browser autofill, IME composition, dead keys) fire keydown
      // with no `key` property. Bail early before any .length / equality checks
      // touch undefined.
      if (typeof e.key !== 'string') return;

      const currentTime = new Date().getTime();

      if (e.key === 'Enter') {
        // If 'Enter' is pressed and we have a buffer accumulated rapidly
        if (buffer.current.length >= minLength) {
          // It's a valid scan!
          onScan(buffer.current);
          
          // Prevent the default 'Enter' behavior (like submitting a focused form mistakenly)
          if (!isInputFocused || (target as HTMLInputElement).type !== 'submit') {
             e.preventDefault();
          }
        }
        buffer.current = '';
        return;
      }

      // Ignore modifier keys and other non-character inputs
      if (e.key.length !== 1 || e.ctrlKey || e.metaKey || e.altKey) {
          return;
      }

      // Calculate time since last keystroke
      const timeDiff = currentTime - lastKeyTime.current;

      // If the time diff is too large, it's likely human typing. Clear buffer.
      if (timeDiff > bufferTimeoutMs && buffer.current.length > 0) {
        buffer.current = '';
      }

      // Append character to buffer
      buffer.current += e.key;
      lastKeyTime.current = currentTime;

      // Set a delayed cleanup just in case the scanner fails to send an 'Enter'
      if (timeoutId.current) clearTimeout(timeoutId.current);
      timeoutId.current = setTimeout(() => {
        buffer.current = '';
      }, bufferTimeoutMs + 10);
    },
    [onScan, bufferTimeoutMs, minLength, disabled]
  );

  useEffect(() => {
    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
      if (timeoutId.current) clearTimeout(timeoutId.current);
    };
  }, [handleKeyDown]);
}
