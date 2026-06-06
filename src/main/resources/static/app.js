const instantButton = document.querySelector('#instantButton');
const cacheButton = document.querySelector('#cacheButton');
const compactButton = document.querySelector('#compactButton');
const promptBox = document.querySelector('#promptBox');
const compactPromptBox = document.querySelector('#compactPromptBox');
const instantResult = document.querySelector('#instantResult');
const cacheResult = document.querySelector('#cacheResult');
const compactResult = document.querySelector('#compactResult');

const formatPercent = (value) => `${Number(value).toFixed(2)}%`;
const formatInteger = (value) => new Intl.NumberFormat().format(Number(value ?? 0));
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
        throw new Error(json.message || json.error || `Request failed with ${response.status}`);
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

function renderCompaction(data) {
    const compaction = data.compaction;
    const cost = data.cost;
    const sourceTokens = Number(compaction.sourceTokens) || 0;
    const compactedTokens = Number(compaction.compactedTokens) || 0;
    const tokensSaved = Number(compaction.tokensSaved) || 0;
    const reductionRate = Number(compaction.tokenReductionRate) || 0;
    const keptPercent = sourceTokens > 0 ? Math.max(0, Math.min(100, (compactedTokens / sourceTokens) * 100)) : 0;
    const freedPercent = Math.max(0, 100 - keptPercent);
    const ratio = compactedTokens > 0 ? sourceTokens / compactedTokens : 0;
    const ratioLabel = ratio >= 1.05 ? `${ratio.toFixed(1)}\u00d7 smaller` : 'about the same size';

    // Size the before/after panels so their visible heights echo the token proportion:
    // the compacted panel is roughly as tall as the share of tokens it keeps.
    const beforeMaxHeight = 240;
    const afterMaxHeight = Math.max(72, Math.round(beforeMaxHeight * (keptPercent / 100)));

    compactResult.className = 'result';
    compactResult.innerHTML = `
        <div class="compaction-headline">
            <div class="headline-figure">
                <strong>${Math.round(reductionRate)}%</strong>
                <span>fewer tokens</span>
            </div>
            <div class="headline-meta">
                <span class="headline-ratio">${ratioLabel}</span>
                <span class="headline-saved">${formatInteger(tokensSaved)} tokens reclaimed on every future reuse</span>
            </div>
        </div>
        <div class="budget" aria-label="Of ${formatInteger(sourceTokens)} original tokens, ${formatInteger(compactedTokens)} kept and ${formatInteger(tokensSaved)} reclaimed">
            <div class="budget-track">
                <div class="budget-kept" style="width: ${keptPercent}%"><span>${formatInteger(compactedTokens)} kept</span></div>
                <div class="budget-freed" style="width: ${freedPercent}%"><span>${formatInteger(tokensSaved)} reclaimed</span></div>
            </div>
            <div class="budget-legend">
                <span class="legend-item"><span class="swatch kept"></span>Durable context kept</span>
                <span class="legend-item"><span class="swatch freed"></span>Reclaimed for future turns</span>
            </div>
        </div>
        <div class="compaction-columns">
            <div class="text-col text-before">
                <div class="text-col-head">
                    <span class="text-col-label">Before · working notes</span>
                    <span class="text-col-count">${formatInteger(sourceTokens)} tokens</span>
                </div>
                <div class="text-body" style="max-height: ${beforeMaxHeight}px">${escapeHtml(data.prompt)}</div>
            </div>
            <div class="text-col text-after">
                <div class="text-col-head">
                    <span class="text-col-label">After · compacted context</span>
                    <span class="text-col-count">${formatInteger(compactedTokens)} tokens</span>
                </div>
                <div class="text-body" style="max-height: ${afterMaxHeight}px">${escapeHtml(data.compactedPrompt)}</div>
            </div>
        </div>
        <p class="fine-print">Reduction: ${formatPercent(compaction.tokenReductionRate)} · Source characters: ${formatInteger(data.sourceCharacters)} · Compacted characters: ${formatInteger(data.compactedCharacters)}</p>
        <p class="fine-print">Compaction call usage: input ${formatInteger(data.usage.inputTokens)} tokens · output ${formatInteger(data.usage.outputTokens)} tokens · total ${formatInteger(data.usage.totalTokens)} tokens</p>
        <p class="fine-print">Estimated compaction cost: ${cost.currencyCode} ${cost.total} (input ${cost.currencyCode} ${cost.standardInput}, output ${cost.currencyCode} ${cost.output})</p>
        <p class="fine-print">Token counts come from the model API. Source tokens include the submitted notes and compaction instructions; reclaimed tokens are saved on each future turn that reuses the summary instead of the raw notes.</p>
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

compactButton.addEventListener('click', async () => {
    compactButton.disabled = true;
    compactResult.className = 'result empty';
    compactResult.textContent = 'Compacting working notes...';
    try {
        renderCompaction(await postJson('/api/compact-demo', { prompt: compactPromptBox.value }));
    } catch (error) {
        showError(compactResult, error);
    } finally {
        compactButton.disabled = false;
    }
});

loadConfig().catch((error) => {
    document.querySelector('#configStrip').textContent = error.message;
});