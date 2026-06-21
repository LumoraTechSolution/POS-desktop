/**
 * Minimal ambient types for the `qz-tray` v2 connector (the package ships no .d.ts).
 * Only the surface we actually use is declared.
 */
declare module 'qz-tray' {
  /** A single element of a print job. Plain strings are interpreted by QZ as
   *  `{ type: 'raw', format: 'command', flavor: 'plain' }` — i.e. raw ESC/POS. */
  export type PrintData =
    | string
    | {
        type?: 'raw' | 'pixel';
        format?: 'command' | 'image' | 'html' | 'pdf';
        flavor?: 'plain' | 'base64' | 'file' | 'hex' | 'xml';
        data: string;
        options?: Record<string, unknown>;
      };

  export interface QzConfig {
    reconfigure(options: Record<string, unknown>): void;
  }

  type Resolver = (resolve: (value?: unknown) => void, reject: (err?: unknown) => void) => void;

  interface Qz {
    api: {
      setPromiseType(promiser: (resolver: Resolver) => Promise<unknown>): void;
      setSha256Type(hasher: (data: string) => string | Promise<string>): void;
      showDebug(show: boolean): boolean;
      getVersion(): Promise<string>;
    };
    websocket: {
      connect(options?: Record<string, unknown>): Promise<void>;
      disconnect(): Promise<void>;
      isActive(): boolean;
    };
    printers: {
      find(query?: string): Promise<string | string[]>;
      getDefault(): Promise<string>;
    };
    configs: {
      create(printer: string | null, options?: Record<string, unknown>): QzConfig;
    };
    print(config: QzConfig, data: PrintData[], signature?: string, signingTimestamp?: number): Promise<void>;
    security: {
      setCertificatePromise(handler: (resolve: (cert: string) => void, reject: (err?: unknown) => void) => void): void;
      setSignaturePromise(
        factory: (toSign: string) => (resolve: (sig: string) => void, reject: (err?: unknown) => void) => void,
      ): void;
      setSignatureAlgorithm(algorithm: string): void;
    };
    version: string;
  }

  const qz: Qz;
  export default qz;
}
