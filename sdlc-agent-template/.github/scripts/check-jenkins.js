#!/usr/bin/env node
/**
 * check-jenkins.js
 *
 * Checks the status of a Jenkins job, waiting for it to finish if it is still running.
 * Exits 0 on SUCCESS, exits 1 on FAILURE / UNSTABLE / ABORTED / timeout.
 *
 * Environment variables (all required):
 *   JENKINS_URL       e.g. https://jenkins.your-org.com
 *   JENKINS_JOB_NAME  e.g. my-service/main  (folder/job or plain job name)
 *   JENKINS_USER      Jenkins username for API token auth
 *   JENKINS_TOKEN     Jenkins API token
 *
 * Optional:
 *   JENKINS_POLL_INTERVAL_MS   How often to poll while build is running (default: 30000)
 *   JENKINS_TIMEOUT_MS         Max wait time in ms (default: 600000 = 10 minutes)
 *
 * Usage (called by AGENTS.md Phase 6):
 *   node .github/scripts/check-jenkins.js
 *
 * Playwright fallback (when Jenkins has no API access):
 *   Set JENKINS_USE_BROWSER=true to use headless Chromium instead of the API.
 *   Requires: npx playwright install chromium
 */

'use strict';

const https = require('https');
const http = require('http');
const url = require('url');

const JENKINS_URL = requireEnv('JENKINS_URL').replace(/\/$/, '');
const JOB_NAME = requireEnv('JENKINS_JOB_NAME');
const USER = requireEnv('JENKINS_USER');
const TOKEN = requireEnv('JENKINS_TOKEN');
const USE_BROWSER = process.env.JENKINS_USE_BROWSER === 'true';
const POLL_INTERVAL = parseInt(process.env.JENKINS_POLL_INTERVAL_MS || '30000', 10);
const TIMEOUT_MS = parseInt(process.env.JENKINS_TIMEOUT_MS || '600000', 10);

function requireEnv(name) {
  const val = process.env[name];
  if (!val) {
    console.error(`ERROR: Environment variable ${name} is required but not set.`);
    process.exit(1);
  }
  return val;
}

function jenkinsGet(path) {
  return new Promise((resolve, reject) => {
    const apiUrl = `${JENKINS_URL}/job/${JOB_NAME}${path}`;
    const parsed = url.parse(apiUrl);
    const auth = Buffer.from(`${USER}:${TOKEN}`).toString('base64');

    const options = {
      hostname: parsed.hostname,
      port: parsed.port || (parsed.protocol === 'https:' ? 443 : 80),
      path: parsed.path,
      method: 'GET',
      headers: {
        Authorization: `Basic ${auth}`,
        Accept: 'application/json',
      },
      rejectUnauthorized: process.env.NODE_TLS_REJECT_UNAUTHORIZED !== '0',
    };

    const transport = parsed.protocol === 'https:' ? https : http;
    const req = transport.request(options, (res) => {
      let data = '';
      res.on('data', (chunk) => { data += chunk; });
      res.on('end', () => {
        if (res.statusCode === 200) {
          try { resolve(JSON.parse(data)); }
          catch (e) { reject(new Error(`JSON parse error: ${e.message}`)); }
        } else {
          reject(new Error(`HTTP ${res.statusCode} from Jenkins API: ${data.slice(0, 200)}`));
        }
      });
    });
    req.on('error', reject);
    req.end();
  });
}

function sleep(ms) {
  return new Promise((res) => setTimeout(res, ms));
}

async function checkViaApi() {
  const startTime = Date.now();
  let attempt = 0;

  while (true) {
    attempt++;
    console.log(`[attempt ${attempt}] Fetching Jenkins build status...`);

    let build;
    try {
      build = await jenkinsGet('/lastBuild/api/json?tree=number,result,building,url');
    } catch (err) {
      console.error(`Jenkins API error: ${err.message}`);
      process.exit(1);
    }

    const { number, result, building, url: buildUrl } = build;
    console.log(`Build #${number} — building: ${building}, result: ${result}`);
    console.log(`Build URL: ${buildUrl}`);

    if (!building) {
      // Build has finished
      if (result === 'SUCCESS') {
        console.log(`✅ Jenkins build #${number} SUCCESS`);
        process.exit(0);
      } else {
        console.error(`❌ Jenkins build #${number} result: ${result}`);
        // Print last 50 lines of console output for diagnosis
        try {
          const log = await jenkinsGet('/lastBuild/logText/progressiveText?start=0');
          const lines = String(log).split('\n');
          const tail = lines.slice(-50).join('\n');
          console.error('--- Last 50 lines of build log ---');
          console.error(tail);
          console.error('---');
        } catch (_) { /* non-fatal */ }
        process.exit(1);
      }
    }

    // Still building — check timeout
    if (Date.now() - startTime > TIMEOUT_MS) {
      console.error(`❌ Timed out after ${TIMEOUT_MS / 1000}s waiting for Jenkins build #${number}.`);
      process.exit(1);
    }

    console.log(`Build still running. Waiting ${POLL_INTERVAL / 1000}s...`);
    await sleep(POLL_INTERVAL);
  }
}

async function checkViaBrowser() {
  let chromium, playwright;
  try {
    ({ chromium } = require('playwright'));
  } catch (_) {
    console.error('ERROR: Playwright is not installed. Run: npx playwright install chromium');
    process.exit(1);
  }

  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  const buildUrl = `${JENKINS_URL}/job/${JOB_NAME}/lastBuild/`;
  console.log(`[browser] Navigating to ${buildUrl}`);

  const startTime = Date.now();

  try {
    while (true) {
      await page.goto(buildUrl, { waitUntil: 'domcontentloaded', timeout: 30000 });

      // Check the page title or build-result badge
      const title = await page.title();
      console.log(`Page title: ${title}`);

      // Jenkins build result is shown in a span with class "build-status-icon__outer"
      // or as text in the breadcrumb. Try both.
      const resultText = await page.evaluate(() => {
        const badge = document.querySelector('[class*="build-status"]');
        if (badge) return badge.getAttribute('tooltip') || badge.textContent || '';
        return document.title;
      });

      console.log(`Build status indicator: ${resultText}`);

      const upperResult = resultText.toUpperCase();
      if (upperResult.includes('SUCCESS')) {
        console.log('✅ Jenkins build SUCCESS (via browser)');
        await browser.close();
        process.exit(0);
      }
      if (upperResult.includes('FAILURE') || upperResult.includes('FAILED') ||
          upperResult.includes('UNSTABLE') || upperResult.includes('ABORTED')) {
        console.error(`❌ Jenkins build failed (via browser): ${resultText}`);
        await browser.close();
        process.exit(1);
      }

      // Still in progress
      if (Date.now() - startTime > TIMEOUT_MS) {
        console.error(`❌ Timed out after ${TIMEOUT_MS / 1000}s.`);
        await browser.close();
        process.exit(1);
      }

      console.log(`Build still running. Waiting ${POLL_INTERVAL / 1000}s...`);
      await sleep(POLL_INTERVAL);
    }
  } catch (err) {
    console.error(`Browser error: ${err.message}`);
    await browser.close();
    process.exit(1);
  }
}

// Entry point
(async () => {
  if (USE_BROWSER) {
    console.log('Using browser automation (JENKINS_USE_BROWSER=true)');
    await checkViaBrowser();
  } else {
    console.log('Using Jenkins REST API');
    await checkViaApi();
  }
})();
