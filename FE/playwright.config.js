// E2E (Giai doan 2B.7C). Chi chay voi Backend tro vao CLONE E2E (xem FE/e2e/README.md).
// Dung Chrome da cai tren may (channel 'chrome') -> khong tai trinh duyet.
import { defineConfig } from '@playwright/test';

const out = process.env.E2E_OUT || 'e2e-results';

export default defineConfig({
  testDir: './e2e',
  testMatch: /.*\.e2e\.js$/,
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  outputDir: `${out}/artifacts`,
  reporter: [['list'], ['json', { outputFile: `${out}/results.json` }]],
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:5173',
    channel: process.env.E2E_CHANNEL || 'chrome',
    headless: true,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    locale: 'vi-VN',
  },
});
