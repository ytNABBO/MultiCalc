"use strict";

const HISTORY_KEY = "multicalc.calculator.history.v1";
const HISTORY_LIMIT = 20;
const REQUEST_TIMEOUT_MS = 16_000;

const form = document.getElementById("calculator-form");
const expression = document.getElementById("expression");
const result = document.getElementById("result");
const statusDisplay = document.getElementById("calculator-status");
const keypad = document.getElementById("keypad");
const formButtons = form.querySelectorAll("button");
const historyList = document.getElementById("history-list");
const historyEmpty = document.getElementById("history-empty");
const clearHistory = document.getElementById("clear-history");

let pending = false;
let historyWritable = true;
let recentCalculations = readHistory() ?? [];

function setStatus(message, isError = false) {
    statusDisplay.dataset.state = isError ? "error" : "";
    statusDisplay.textContent = message;
}

function resetDisplay() {
    result.textContent = "—";
    setStatus("");
}

function readHistory() {
    try {
        const saved = JSON.parse(window.localStorage.getItem(HISTORY_KEY) ?? "[]");
        return Array.isArray(saved) ? saved.filter((entry) => (
            typeof entry?.expression === "string" && typeof entry?.result === "string"
        )).slice(0, HISTORY_LIMIT) : [];
    } catch {
        return null;
    }
}

function saveHistory(entries) {
    recentCalculations = entries;
    if (historyWritable) {
        try {
            window.localStorage.setItem(HISTORY_KEY, JSON.stringify(recentCalculations));
        } catch {
            historyWritable = false;
        }
    }
    renderHistory();
}

function renderHistory() {
    const items = recentCalculations.map((entry) => {
        const item = document.createElement("li");
        const button = document.createElement("button");
        button.type = "button";
        button.className = "history-entry";
        button.disabled = pending;

        const previousExpression = document.createElement("span");
        previousExpression.className = "history-expression";
        previousExpression.textContent = entry.expression;
        const previousResult = document.createElement("span");
        previousResult.className = "history-result";
        previousResult.textContent = entry.result;

        button.append(previousExpression, previousResult);
        button.addEventListener("click", () => {
            expression.value = entry.expression;
            expression.focus();
            expression.setSelectionRange(entry.expression.length, entry.expression.length);
            resetDisplay();
        });
        item.append(button);
        return item;
    });
    historyList.replaceChildren(...items);
    historyEmpty.hidden = items.length > 0;
    clearHistory.disabled = pending || items.length === 0;
}

function setPending(value) {
    pending = value;
    expression.readOnly = value;
    for (const button of formButtons) {
        button.disabled = value;
    }
    for (const button of historyList.querySelectorAll("button")) {
        button.disabled = value;
    }
    clearHistory.disabled = value || recentCalculations.length === 0;
}

async function requestCalculation(text, signal) {
    let response;
    try {
        response = await fetch("api/calculate.php", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ expression: text }),
            signal,
        });
    } catch (error) {
        if (signal.aborted) {
            throw error;
        }
        throw new Error("Could not reach the calculator API.");
    }

    let payload;
    try {
        payload = await response.json();
    } catch (error) {
        if (signal.aborted) {
            throw error;
        }
        throw new Error("The calculator API returned an invalid response.");
    }

    if (!response.ok) {
        const message = typeof payload?.error === "string" ? payload.error.trim() : "";
        throw new Error(message || "Could not calculate this expression.");
    }
    if (typeof payload?.result !== "string" || payload.result.trim() === "") {
        throw new Error("The calculator API returned an invalid result.");
    }
    return payload.result;
}

keypad.addEventListener("click", (event) => {
    const button = event.target.closest("button[data-key]");
    if (!button || pending) {
        return;
    }

    const key = button.dataset.key;
    const start = expression.selectionStart;
    const end = expression.selectionEnd;
    switch (key) {
        case "clear":
            expression.value = "";
            break;
        case "backspace": {
            const before = expression.value.slice(0, start);
            const previousLength = Array.from(before).pop()?.length ?? 0;
            expression.setRangeText("", start === end ? start - previousLength : start, end, "end");
            break;
        }
        default:
            expression.setRangeText(key, start, end, "end");
    }
    resetDisplay();
    if (event.detail > 0 && window.matchMedia("(pointer: fine)").matches) {
        expression.focus();
    }
});

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (pending) {
        return;
    }

    const text = expression.value;
    resetDisplay();
    if (!text.trim()) {
        setStatus("Enter an expression.", true);
        return;
    }

    setPending(true);
    setStatus("Calculating…");
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
    try {
        const answer = await requestCalculation(text, controller.signal);
        result.textContent = answer;
        setStatus("");
        const previous = historyWritable ? readHistory() ?? recentCalculations : recentCalculations;
        saveHistory([{ expression: text, result: answer }, ...previous].slice(0, HISTORY_LIMIT));
    } catch (error) {
        const message = controller.signal.aborted ? "The calculator API timed out." : error.message;
        setStatus(message, true);
    } finally {
        window.clearTimeout(timeout);
        setPending(false);
    }
});

expression.addEventListener("input", resetDisplay);
clearHistory.addEventListener("click", () => saveHistory([]));
window.addEventListener("storage", (event) => {
    if (historyWritable && (event.key === HISTORY_KEY || event.key === null)) {
        recentCalculations = readHistory() ?? recentCalculations;
        renderHistory();
    }
});
renderHistory();
