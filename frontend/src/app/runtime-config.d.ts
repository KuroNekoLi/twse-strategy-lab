export {};

declare global {
  interface Window {
    APP_CONFIG?: {
      apiBaseUrl?: string;
    };
  }
}
