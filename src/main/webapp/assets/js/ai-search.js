(() => {
    "use strict";

    const form = document.querySelector("[data-ai-form]");
    if (!form) {
        return;
    }

    const textarea = form.querySelector("textarea[name='q']");
    const submit = form.querySelector("[data-ai-submit]");
    const apiUrl = form.dataset.apiUrl;
    const csrf = form.dataset.csrf || "";
    const status = document.querySelector("[data-ai-status]");
    const statusText = status ? status.querySelector("[data-ai-status-text]") : null;
    const errorBox = document.querySelector("[data-ai-error]");
    const results = document.querySelector("[data-ai-results]");
    const warning = document.querySelector("[data-ai-warning]");
    const modelBadge = document.querySelector("[data-ai-model]");
    const modeBadge = document.querySelector("[data-ai-mode]");
    const toolsLine = document.querySelector("[data-ai-tools]");
    const answer = document.querySelector("[data-ai-answer]");
    const answerSr = document.querySelector("[data-ai-answer-sr]");
    const grid = document.querySelector("[data-ai-grid]");
    const empty = document.querySelector("[data-ai-empty]");
    const meta = document.querySelector("[data-ai-meta]");
    const contextPath = document.body.dataset.ctx || "";
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const preferences = window.matchMedia("(prefers-reduced-motion: reduce)");

    let typingTimer = 0;
    let statusTimer = 0;

    const showError = (message) => {
        if (!errorBox) {
            return;
        }
        errorBox.textContent = message || "";
        errorBox.hidden = !message;
        if (message) {
            errorBox.scrollIntoView({ behavior: reduceMotion ? "auto" : "smooth", block: "center" });
        }
    };

    const setStatus = () => {
        if (!status || !statusText) {
            return;
        }
        const messages = [
            "Reading your prompt\u2026",
            "Searching the gallery\u2026",
            "Comparing colours, tags and titles\u2026",
            "Still working \u2014 the first model load can take a minute\u2026"
        ];
        let index = 0;
        window.clearInterval(statusTimer);
        status.hidden = false;
        status.classList.remove("is-done");
        statusText.textContent = messages[0];
        statusTimer = window.setInterval(() => {
            index = Math.min(index + 1, messages.length - 1);
            statusText.textContent = messages[index];
        }, 4500);
    };

    const stopStatus = (message) => {
        if (statusTimer) {
            window.clearInterval(statusTimer);
            statusTimer = 0;
        }
        if (status) {
            if (message) {
                status.hidden = false;
                status.classList.add("is-done");
                if (statusText) {
                    statusText.textContent = message;
                }
            } else {
                status.hidden = true;
                status.classList.remove("is-done");
            }
        }
        if (!message && statusText) {
            statusText.textContent = "";
        }
    };

    const modeLabel = (mode) => {
        if (mode === "tools") {
            return "tool calling";
        }
        if (mode === "prompt") {
            return "prompt mode";
        }
        return "local fallback";
    };

    const renderAnswer = (text) => {
        if (!answer) {
            return;
        }
        window.clearInterval(typingTimer);
        typingTimer = 0;
        answer.parentElement.classList.remove("is-typing");
        answer.removeAttribute("aria-hidden");
        if (answerSr) {
            answerSr.textContent = "";
        }
        answer.textContent = text || "";

        if (reduceMotion || !text) {
            return;
        }

        answer.setAttribute("aria-hidden", "true");
        if (answerSr) {
            answerSr.textContent = text;
        }
        answer.textContent = "";
        answer.parentElement.classList.add("is-typing");
        const step = Math.max(1, Math.ceil(text.length / 220));
        let index = 0;
        typingTimer = window.setInterval(() => {
            index = Math.min(text.length, index + step);
            answer.textContent = text.slice(0, index);
            if (index >= text.length) {
                window.clearInterval(typingTimer);
                typingTimer = 0;
                answer.parentElement.classList.remove("is-typing");
                answer.removeAttribute("aria-hidden");
                if (answerSr) {
                    answerSr.textContent = "";
                }
            }
        }, 16);
    };

    const renderCards = (images) => {
        if (!grid || !empty) {
            return;
        }
        grid.textContent = "";
        if (!images || images.length === 0) {
            grid.hidden = true;
            empty.hidden = false;
            return;
        }
        grid.hidden = false;
        empty.hidden = true;

        images.forEach((image, index) => {
            const item = document.createElement("li");
            item.className = "card ai-card";
            item.style.setProperty("--i", String(index));

            const link = document.createElement("a");
            link.className = "card__link";
            link.href = contextPath + "/photo/" + encodeURIComponent(image.imageId);

            const media = document.createElement("span");
            media.className = "card__media";
            const img = document.createElement("img");
            img.className = "card__image";
            img.src = contextPath + "/thumb/" + encodeURIComponent(image.imageId);
            img.alt = image.title || "Gallery image";
            img.loading = "lazy";
            img.decoding = "async";
            media.append(img);

            if (image.scorePercent !== null && image.scorePercent !== undefined) {
                const score = document.createElement("span");
                score.className = "score-badge";
                score.textContent = image.scorePercent + "%";
                media.append(score);
            }

            const body = document.createElement("span");
            body.className = "card__body";
            const title = document.createElement("span");
            title.className = "card__title";
            title.textContent = image.title || "Untitled";
            const byline = document.createElement("span");
            byline.className = "card__meta";
            byline.textContent = "by " + (image.username || "unknown");
            body.append(title, byline);

            if (image.reason) {
                const reason = document.createElement("span");
                reason.className = "ai-card__reason";
                reason.textContent = image.reason;
                body.append(reason);
            }

            link.append(media, body);
            item.append(link);
            grid.append(item);
        });
    };

    const renderResult = (result) => {
        if (!results) {
            return;
        }
        showError("");
        results.hidden = false;

        if (warning) {
            warning.textContent = result.warning || "";
            warning.hidden = !result.warning;
        }
        if (modelBadge) {
            modelBadge.textContent = result.model || "local";
        }
        if (modeBadge) {
            modeBadge.textContent = modeLabel(result.mode);
        }
        if (toolsLine) {
            toolsLine.textContent = result.toolsUsed && result.toolsUsed.length
                ? "Tools: " + result.toolsUsed.join(", ")
                : "No tool calls";
        }
        renderAnswer(result.answer || "");
        renderCards(result.images || []);
        if (meta) {
            const count = (result.images || []).length;
            const seconds = ((Number(result.elapsedMs) || 0) / 1000).toFixed(1);
            meta.textContent = count + (count === 1 ? " image" : " images") + " \u00b7 " + seconds + "s";
        }

        try {
            const params = new URLSearchParams();
            params.set("q", textarea ? textarea.value.trim() : "");
            window.history.replaceState(null, "", contextPath + "/ai-search?" + params.toString());
        } catch (error) {
            // History is a convenience; the result is already visible.
        }

        results.scrollIntoView({
            behavior: reduceMotion ? "auto" : "smooth",
            block: "start"
        });
    };

    if (!window.fetch || !window.URLSearchParams || !apiUrl) {
        return;
    }

    form.addEventListener("submit", async (event) => {
        const prompt = textarea ? textarea.value.trim() : "";
        if (!prompt) {
            event.preventDefault();
            if (textarea) {
                textarea.focus();
            }
            showError("Describe the image you are looking for.");
            return;
        }

        event.preventDefault();
        showError("");
        if (results) {
            results.hidden = true;
        }
        if (submit) {
            submit.disabled = true;
        }
        setStatus();

        const controller = new AbortController();
        const timeout = window.setTimeout(() => controller.abort(), 190000);
        try {
            const body = new URLSearchParams();
            body.set("prompt", prompt);
            body.set("_csrf", csrf);
            const response = await window.fetch(apiUrl, {
                method: "POST",
                headers: {
                    "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
                    "Accept": "application/json"
                },
                body: body.toString(),
                signal: controller.signal
            });
            const payload = await response.json().catch(() => null);
            if (!response.ok || !payload || payload.ok !== true) {
                const message = payload && payload.error
                    ? payload.error
                    : "The AI search failed. Please try again.";
                throw new Error(message);
            }
            const result = payload.result || {};
            const images = result.images || [];
            stopStatus("Search complete. " + images.length
                + (images.length === 1 ? " image" : " images") + " found.");
            renderResult(result);
        } catch (error) {
            stopStatus();
            if (error && error.name === "AbortError") {
                showError("The local model took too long. Try a shorter prompt, or use tag search.");
            } else {
                showError(error && error.message ? error.message : "The AI search failed. Please try again.");
            }
        } finally {
            window.clearTimeout(timeout);
            if (submit) {
                submit.disabled = false;
            }
        }
    });

    document.querySelectorAll("[data-ai-example]").forEach((button) => {
        button.addEventListener("click", () => {
            if (!textarea) {
                return;
            }
            textarea.value = button.dataset.aiExample || "";
            textarea.focus();
            if (typeof form.requestSubmit === "function") {
                form.requestSubmit(submit || undefined);
            } else if (submit) {
                submit.click();
            }
        });
    });

    if (preferences.addEventListener) {
        preferences.addEventListener("change", () => {
            // Re-render the answer without replaying a long typing animation
            // when the preference is switched at runtime.
            if (answer && answer.textContent && !typingTimer) {
                answer.parentElement.classList.remove("is-typing");
            }
        });
    }
})();
