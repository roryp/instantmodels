const instantButton = document.querySelector('#instantButton');
const cacheButton = document.querySelector('#cacheButton');
const promptBox = document.querySelector('#promptBox');
const instantResult = document.querySelector('#instantResult');
const cacheResult = document.querySelector('#cacheResult');

const formatPercent = (value) => `${Number(value).toFixed(2)}%`;
const escapeHtml = (value) => String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');

async function postJson(url, body) {
    const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: body ? JSON.stringify(body) : undefined,
    });
    const json = await response.json();
    if (!response.ok) {
        throw new Error(json.error || `Request failed with ${response.status}`);
    }
    return json;
}

function renderSummary(summary, pricing) {
    const usage = summary.usage;
    const cache = summary.cache;
    const cost = summary.cost;
    const cacheWidth = Math.max(0, Math.min(100, cache.cacheHitRate));

    return `
        <div class="metrics">
            <div class="metric"><strong>${usage.inputTokens}</strong><span>Input tokens</span></div>
            <div class="metric"><strong>${cache.cachedInputTokens}</strong><span>Cached input</span></div>
            <div class="metric"><strong class="money"><em>${cost.currencyCode}</em>${cost.total}</strong><span>Estimated cost</span></div>
        </div>
        <div class="cache-bar" aria-label="Cache hit rate ${formatPercent(cache.cacheHitRate)}">
            <div class="cache-fill" style="width: ${cacheWidth}%"></div>
        </div>
        <p class="fine-print">Standard input: ${cache.standardInputTokens} tokens · Output: ${usage.outputTokens} tokens · Cache hit rate: ${formatPercent(cache.cacheHitRate)}</p>
        <p class="fine-print">Cost: standard ${cost.currencyCode} ${cost.standardInput}, cached ${cost.currencyCode} ${cost.cachedInput}, output ${cost.currencyCode} ${cost.output}</p>
        <p class="fine-print">Meters: ${escapeHtml(pricing.inputMeter)} · ${escapeHtml(pricing.cachedInputMeter)} · ${escapeHtml(pricing.outputMeter)}</p>
    `;
}

function renderInstant(data) {
    instantResult.className = 'result';
    instantResult.innerHTML = `
        <p class="answer">${escapeHtml(data.response)}</p>
        ${renderSummary(data, data.pricing)}
    `;
}

function renderCacheDemo(data) {
    cacheResult.className = 'result';
    cacheResult.innerHTML = `
        <p class="fine-print">Cache key: ${escapeHtml(data.cacheKey)} · Prompt characters: ${data.promptCharacters}</p>
        ${data.calls.map((run) => renderCacheRun(run, data.pricing)).join('')}
    `;
}

function renderCacheRun(run, pricing) {
    return `
        <section class="result-card">
            <h3>${escapeHtml(run.label)}</h3>
            <p class="answer">${escapeHtml(run.response)}</p>
            ${renderSummary(run, pricing)}
            <p class="fine-print">Cache savings versus uncached input: ${run.cost.currencyCode} ${run.cost.cacheSavings}</p>
        </section>
    `;
}

function showError(target, error) {
    target.className = 'result error';
    target.textContent = error.message;
}

async function loadConfig() {
    const response = await fetch('/api/config');
    const config = await response.json();
    document.querySelector('#modelValue').textContent = config.model;
    document.querySelector('#meterValue').textContent = config.pricingMeterPrefix;
    document.querySelector('#regionValue').textContent = `${config.pricingRegion} / ${config.pricingScope}`;
}

instantButton.addEventListener('click', async () => {
    instantButton.disabled = true;
    instantResult.className = 'result empty';
    instantResult.textContent = 'Calling the instant model...';
    try {
        renderInstant(await postJson('/api/instant', { prompt: promptBox.value }));
    } catch (error) {
        showError(instantResult, error);
    } finally {
        instantButton.disabled = false;
    }
});

cacheButton.addEventListener('click', async () => {
    cacheButton.disabled = true;
    cacheResult.className = 'result empty';
    cacheResult.textContent = 'Running warm-up and repeated prompt calls...';
    try {
        renderCacheDemo(await postJson('/api/cache-demo'));
    } catch (error) {
        showError(cacheResult, error);
    } finally {
        cacheButton.disabled = false;
    }
});

loadConfig().catch((error) => {
    document.querySelector('#configStrip').textContent = error.message;
});