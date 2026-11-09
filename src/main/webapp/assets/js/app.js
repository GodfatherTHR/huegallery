(() => {
    "use strict";

    // Confirm destructive forms.
    document.addEventListener("submit", (event) => {
        const form = event.target.closest("form[data-confirm]");
        if (form && !window.confirm(form.dataset.confirm)) {
            event.preventDefault();
        }
    });

    // Lightbox on the detail page.
    const trigger = document.querySelector("[data-lightbox-trigger]");
    const lightbox = document.getElementById("lightbox");
    if (trigger && lightbox) {
        const image = lightbox.querySelector("[data-lightbox-image]");
        const closeButton = lightbox.querySelector("[data-lightbox-close]");
        const source = trigger.querySelector("img");

        const open = () => {
            if (!image || !source) {
                return;
            }
            image.src = source.currentSrc || source.src;
            image.alt = source.alt || "";
            lightbox.hidden = false;
            document.body.style.overflow = "hidden";
            closeButton.focus();
        };

        const close = () => {
            lightbox.hidden = true;
            image.removeAttribute("src");
            image.alt = "";
            document.body.style.overflow = "";
            trigger.focus();
        };

        trigger.addEventListener("click", open);
        closeButton.addEventListener("click", close);
        lightbox.addEventListener("click", (event) => {
            if (event.target === lightbox) {
                close();
            }
        });
        document.addEventListener("keydown", (event) => {
            if (event.key === "Escape" && !lightbox.hidden) {
                close();
            }
        });
    }

    // Dismiss successful flash messages after a few seconds.
    document.querySelectorAll(".alert--success").forEach((alert) => {
        window.setTimeout(() => {
            alert.style.transition = "opacity 0.4s ease";
            alert.style.opacity = "0";
            window.setTimeout(() => alert.remove(), 450);
        }, 6000);
    });
})();
