(() => {
    "use strict";

    const MAX_TAGS = 10;
    const MAX_LENGTH = 40;
    const contextPath = document.body.dataset.ctx || "";

    const normalise = (raw) => raw
        .toLowerCase()
        .replace(/[^a-z0-9 -]/g, "")
        .replace(/\s+/g, " ")
        .trim()
        .slice(0, MAX_LENGTH)
        .trim();

    const unique = (values) => Array.from(new Set(values));

    document.querySelectorAll("[data-chips]").forEach(setup);

    function setup(root) {
        const list = root.querySelector("[data-chip-list]");
        const input = root.querySelector("[data-chip-input]");
        const hidden = root.querySelector("[data-chip-value]");
        const autocomplete = root.querySelector("[data-autocomplete]");
        const form = root.closest("form");
        if (!list || !input || !hidden) {
            return;
        }

        const initial = root.dataset.initial || hidden.value || "";
        let tags = unique(initial.split(",").map(normalise).filter(Boolean)).slice(0, MAX_TAGS);
        let messageTimer = 0;

        const message = document.createElement("p");
        message.className = "hint chips-input__error";
        message.setAttribute("role", "status");
        root.insertAdjacentElement("afterend", message);

        const render = () => {
            list.textContent = "";
            tags.forEach((tag, index) => {
                const item = document.createElement("li");
                const chip = document.createElement("span");
                chip.className = "chip";
                const label = document.createElement("span");
                label.textContent = tag;
                const remove = document.createElement("button");
                remove.type = "button";
                remove.className = "chip__remove";
                remove.textContent = "\u00d7";
                remove.setAttribute("aria-label", "Remove tag " + tag);
                remove.addEventListener("click", () => {
                    tags.splice(index, 1);
                    render();
                    input.focus();
                });
                chip.append(label, remove);
                item.append(chip);
                list.append(item);
            });
            hidden.value = tags.join(", ");
            input.setAttribute("aria-expanded", "false");
            if (autocomplete) {
                autocomplete.hidden = true;
            }
        };

        const showMessage = (text) => {
            message.textContent = text;
            window.clearTimeout(messageTimer);
            messageTimer = window.setTimeout(() => {
                message.textContent = "";
            }, 3500);
        };

        const addTag = (raw) => {
            const tag = normalise(raw);
            input.value = "";
            hideSuggestions();
            if (!tag) {
                return;
            }
            if (tags.includes(tag)) {
                showMessage("That tag is already added.");
                return;
            }
            if (tags.length >= MAX_TAGS) {
                showMessage("You can add at most " + MAX_TAGS + " tags.");
                return;
            }
            tags.push(tag);
            message.textContent = "";
            render();
        };

        const removeLast = () => {
            if (tags.length > 0 && input.value === "") {
                tags.pop();
                render();
            }
        };

        input.addEventListener("keydown", (event) => {
            if (event.key === "Enter" || event.key === ",") {
                event.preventDefault();
                addTag(input.value);
            } else if (event.key === "Backspace") {
                removeLast();
            } else if (event.key === "Escape") {
                hideSuggestions();
            }
        });

        let debounce = 0;
        input.addEventListener("input", () => {
            const query = input.value.trim();
            window.clearTimeout(debounce);
            if (!query) {
                hideSuggestions();
                return;
            }
            debounce = window.setTimeout(() => fetchSuggestions(query), 180);
        });

        const hideSuggestions = () => {
            if (autocomplete) {
                autocomplete.hidden = true;
                autocomplete.textContent = "";
            }
            input.setAttribute("aria-expanded", "false");
        };

        const fetchSuggestions = async (query) => {
            if (!autocomplete) {
                return;
            }
            try {
                const response = await fetch(
                    contextPath + "/api/tags?q=" + encodeURIComponent(query),
                    { headers: { Accept: "application/json" } }
                );
                if (!response.ok) {
                    return;
                }
                const items = await response.json();
                const usable = items.filter((item) => !tags.includes(item.name));
                if (usable.length === 0) {
                    hideSuggestions();
                    return;
                }
                autocomplete.textContent = "";
                usable.forEach((item) => {
                    const option = document.createElement("button");
                    option.type = "button";
                    option.className = "autocomplete__option";
                    option.setAttribute("aria-label", "Add tag " + item.name);
                    const name = document.createElement("span");
                    name.textContent = item.name;
                    const count = document.createElement("span");
                    count.className = "autocomplete__count";
                    count.textContent = item.count;
                    option.append(name, count);
                    option.addEventListener("mousedown", (event) => {
                        event.preventDefault();
                        addTag(item.name);
                        input.focus();
                    });
                    autocomplete.append(option);
                });
                autocomplete.hidden = false;
                input.setAttribute("aria-expanded", "true");
            } catch (error) {
                hideSuggestions();
            }
        };

        input.addEventListener("blur", () => {
            window.setTimeout(hideSuggestions, 120);
        });

        if (form) {
            form.addEventListener("submit", () => {
                if (input.value.trim()) {
                    addTag(input.value);
                }
            });
        }

        render();
    }
})();
