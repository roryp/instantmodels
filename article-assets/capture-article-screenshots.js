const { chromium } = require('playwright');
const path = require('path');

const outputDir = __dirname;
const baseUrl = process.env.ARTICLE_CAPTURE_URL || 'http://localhost:8080/';

function outputPath(name) {
  return path.join(outputDir, name);
}

async function applyDesktopCaptureStyles(page) {
  await page.addStyleTag({
    content: `
      html, body {
        margin: 0 !important;
        min-width: 1280px !important;
        overflow-x: hidden !important;
        scrollbar-width: none !important;
      }

      body::-webkit-scrollbar,
      *::-webkit-scrollbar {
        display: none !important;
      }

      .shell {
        width: 1180px !important;
        padding: 28px 0 !important;
      }

      .intro {
        grid-template-columns: minmax(0, 1.2fr) minmax(360px, 0.8fr) !important;
      }

      .config-strip {
        grid-template-columns: repeat(3, minmax(0, 1fr)) !important;
      }

      .workspace {
        grid-template-columns: repeat(3, minmax(0, 1fr)) !important;
      }

      .metrics {
        grid-template-columns: repeat(3, minmax(0, 1fr)) !important;
      }
    `,
  });
}

async function loadDashboard(page) {
  await page.goto(baseUrl, { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(() => document.querySelector('#modelValue')?.textContent?.includes('gpt-chat-latest'));
  await applyDesktopCaptureStyles(page);
}

async function setResultContent(page) {
  await page.evaluate(() => {
    const summary = ({ input, cached, standard, output, cost, hit, standardCost, cachedCost, outputCost }) => `
      <div class="metrics">
        <div class="metric"><strong>${input}</strong><span>Input tokens</span></div>
        <div class="metric"><strong>${cached}</strong><span>Cached input</span></div>
        <div class="metric"><strong class="money"><em>USD</em>${cost}</strong><span>Estimated cost</span></div>
      </div>
      <div class="cache-bar" aria-label="Cache hit rate ${hit}%"><div class="cache-fill" style="width: ${hit}%"></div></div>
      <p class="fine-print">Standard input: ${standard} tokens · Output: ${output} tokens · Cache hit rate: ${hit}%</p>
      <p class="fine-print">Cost: standard USD ${standardCost}, cached USD ${cachedCost}, output USD ${outputCost}</p>`;

    const instant = document.querySelector('#instantResult');
    instant.className = 'result';
    instant.innerHTML = `<p class="answer">Instant models in Microsoft Foundry are preconfigured models you can call immediately by name, making early prototypes fast while still keeping usage and cost measurable.</p>${summary({ input: '19', cached: '0', standard: '19', output: '106', cost: '0.00327500', hit: '0.00', standardCost: '0.00009500', cachedCost: '0.00000000', outputCost: '0.00318000' })}`;

    const cache = document.querySelector('#cacheResult');
    cache.className = 'result';
    cache.innerHTML = `
      <p class="fine-print cache-key">Cache key: im-cache-demo · Prompt characters: 60,935</p>
      <section class="result-card warm-up"><h3>Warm-up call</h3><p class="answer">The first call pays for the full long prefix.</p>${summary({ input: '10,045', cached: '0', standard: '10,045', output: '33', cost: '0.05121500', hit: '0.00', standardCost: '0.05022500', cachedCost: '0.00000000', outputCost: '0.00099000' })}<p class="fine-print">Cache savings: USD 0.00000000</p></section>
      <section class="result-card repeated"><h3>Repeated call</h3><p class="answer">The repeated call reuses the stable prefix, shifting most input into the cached meter.</p>${summary({ input: '10,045', cached: '9,728', standard: '317', output: '34', cost: '0.00746900', hit: '96.84', standardCost: '0.00158500', cachedCost: '0.00486400', outputCost: '0.00102000' })}<p class="fine-print">Cache savings: USD 0.04377600</p></section>`;

    const compact = document.querySelector('#compactResult');
    compact.className = 'result';
    compact.innerHTML = `
      <p class="answer">Build a Java Spring Boot dashboard for Microsoft Foundry instant models that keeps token usage, cache behavior, live retail pricing, and compaction tradeoffs visible. Use DefaultAzureCredential, avoid secrets in docs, validate Java changes with mvn test, and deploy with azd when needed.</p>
      <div class="metrics">
        <div class="metric"><strong>1,164</strong><span>Source tokens</span></div>
        <div class="metric"><strong>73</strong><span>Compacted tokens</span></div>
        <div class="metric"><strong>1,091</strong><span>Tokens saved</span></div>
      </div>
      <div class="cache-bar" aria-label="Token reduction 93.73%"><div class="compact-fill" style="width: 93.73%"></div></div>
      <p class="fine-print">Reduction: 93.73% · Source characters: 1,692 · Compacted characters: 319</p>
      <p class="fine-print">Compaction call usage: input 1,164 tokens · output 73 tokens · total 1,237 tokens</p>
      <p class="fine-print">Estimated compaction cost: USD 0.00799000</p>`;
  });
}

async function applyArticleCaptureStyles(page) {
  await page.addStyleTag({
    content: `
      body.article-results .intro,
      body.article-cache .intro,
      body.article-compact .intro {
        display: none !important;
      }

      body.article-results .shell,
      body.article-cache .shell,
      body.article-compact .shell {
        padding: 20px 0 !important;
      }

      body.article-results label,
      body.article-results textarea,
      body.article-results button,
      body.article-results .panel-copy {
        display: none !important;
      }

      body.article-results .panel {
        padding: 18px !important;
      }

      body.article-results .panel-heading {
        margin-bottom: 12px !important;
      }

      body.article-results h2 {
        font-size: 1.25rem !important;
      }

      body.article-results .result {
        min-height: 0 !important;
        padding: 12px !important;
      }

      body.article-results .answer {
        margin-bottom: 10px !important;
        padding: 10px !important;
        font-size: 0.84rem !important;
      }

      body.article-results .metric {
        padding: 8px !important;
      }

      body.article-results .metric strong {
        font-size: 0.92rem !important;
      }

      body.article-results .metric span {
        font-size: 0.58rem !important;
      }

      body.article-results .fine-print {
        font-size: 0.66rem !important;
        line-height: 1.25 !important;
      }

      body.article-results .cache-panel .warm-up {
        display: none !important;
      }

      body.article-results .result-card {
        padding: 10px !important;
        margin-bottom: 0 !important;
      }

      body.article-results .result-card h3 {
        margin-bottom: 8px !important;
        font-size: 1rem !important;
      }

      body.article-cache .instant-panel,
      body.article-cache .compact-panel,
      body.article-compact .instant-panel,
      body.article-compact .cache-panel {
        display: none !important;
      }

      body.article-cache .workspace,
      body.article-compact .workspace {
        grid-template-columns: 1fr !important;
      }

      body.article-cache .cache-panel,
      body.article-compact .compact-panel {
        padding: 22px !important;
      }

      body.article-cache .cache-panel .panel-copy,
      body.article-cache .cache-panel button,
      body.article-compact .compact-panel label,
      body.article-compact .compact-panel textarea,
      body.article-compact .compact-panel button {
        display: none !important;
      }

      body.article-cache #cacheResult {
        min-height: 0 !important;
        display: grid !important;
        grid-template-columns: repeat(2, minmax(0, 1fr)) !important;
        gap: 16px !important;
      }

      body.article-cache #cacheResult .cache-key {
        grid-column: 1 / -1 !important;
        margin: 0 !important;
        font-size: 0.9rem !important;
      }

      body.article-cache #cacheResult .result-card {
        margin: 0 !important;
      }

      body.article-cache #cacheResult .fine-print,
      body.article-compact #compactResult .fine-print {
        font-size: 0.82rem !important;
      }

      body.article-compact #compactResult {
        min-height: 0 !important;
      }

      body.article-compact #compactResult .answer {
        font-size: 0.98rem !important;
      }
    `,
  });
}

async function screenshotLocator(page, selector, name) {
  const locator = page.locator(selector);
  await locator.scrollIntoViewIfNeeded();
  await locator.screenshot({ path: outputPath(name), animations: 'disabled' });
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1200 }, deviceScaleFactor: 1 });

  await loadDashboard(page);
  await page.screenshot({
    path: outputPath('instant-models-dashboard.png'),
    clip: { x: 100, y: 20, width: 1240, height: 760 },
    animations: 'disabled',
  });

  await setResultContent(page);
  await applyArticleCaptureStyles(page);

  await page.evaluate(() => {
    document.body.className = 'article-results';
    window.scrollTo(0, 0);
  });
  await screenshotLocator(page, '.workspace', 'instant-models-results.png');

  await page.evaluate(() => {
    document.body.className = 'article-cache';
    window.scrollTo(0, 0);
  });
  await screenshotLocator(page, '.cache-panel', 'prompt-cache-results.png');

  await page.evaluate(() => {
    document.body.className = 'article-compact';
    window.scrollTo(0, 0);
  });
  await screenshotLocator(page, '.compact-panel', 'compaction-results.png');

  await browser.close();
  console.log('Recreated clean wide screenshots in article-assets.');
})().catch(async (error) => {
  console.error(error);
  process.exit(1);
});
