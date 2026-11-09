(() => {
    "use strict";

    const MAX_BYTES = 8 * 1024 * 1024;
    const ALLOWED_TYPES = ["image/jpeg", "image/png", "image/gif", "image/webp"];

    const validate = (file) => {
        if (!ALLOWED_TYPES.includes(file.type)) {
            return "Choose a JPEG, PNG, GIF or WebP image.";
        }
        if (file.size > MAX_BYTES) {
            return "Images must be 8 MB or smaller.";
        }
        return null;
    };

    const readableSize = (bytes) => {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return Math.round(bytes / 1024) + " KB";
        }
        return (bytes / (1024 * 1024)).toFixed(1) + " MB";
    };

    document.querySelectorAll("[data-dropzone]").forEach((dropzone) => {
        const form = dropzone.closest("form");
        const input = dropzone.querySelector("[data-file-input]");
        const preview = dropzone.querySelector("[data-preview]");
        const message = form ? form.querySelector("[data-file-message]") : null;
        if (!input) {
            return;
        }
        let objectUrl = null;

        const setMessage = (text, error) => {
            if (!message) {
                return;
            }
            message.textContent = text;
            message.classList.toggle("hint--error", Boolean(error));
        };

        const clearPreview = () => {
            dropzone.classList.remove("dropzone--has-file");
            if (preview) {
                preview.hidden = true;
                preview.removeAttribute("src");
            }
            if (objectUrl) {
                URL.revokeObjectURL(objectUrl);
                objectUrl = null;
            }
        };

        const showFile = (file) => {
            const problem = validate(file);
            if (problem) {
                input.value = "";
                clearPreview();
                setMessage(problem, true);
                return false;
            }
            clearPreview();
            if (preview) {
                objectUrl = URL.createObjectURL(file);
                preview.src = objectUrl;
                preview.hidden = false;
            }
            dropzone.classList.add("dropzone--has-file");
            setMessage(file.name + " \u00b7 " + readableSize(file.size), false);
            return true;
        };

        input.addEventListener("change", () => {
            const file = input.files && input.files[0];
            if (!file) {
                clearPreview();
                setMessage("", false);
                return;
            }
            showFile(file);
        });

        ["dragenter", "dragover"].forEach((type) => {
            dropzone.addEventListener(type, (event) => {
                event.preventDefault();
                dropzone.classList.add("dropzone--over");
            });
        });

        ["dragleave", "dragend", "drop"].forEach((type) => {
            dropzone.addEventListener(type, (event) => {
                event.preventDefault();
                dropzone.classList.remove("dropzone--over");
            });
        });

        dropzone.addEventListener("drop", (event) => {
            const files = event.dataTransfer && event.dataTransfer.files;
            if (!files || files.length === 0) {
                return;
            }
            const file = files[0];
            if (!validate(file)) {
                const transfer = new DataTransfer();
                transfer.items.add(file);
                input.files = transfer.files;
                showFile(file);
            } else {
                showFile(file);
            }
        });

        if (form) {
            form.addEventListener("submit", (event) => {
                const file = input.files && input.files[0];
                if (!file) {
                    event.preventDefault();
                    setMessage("Choose an image first.", true);
                    input.focus();
                    return;
                }
                const problem = validate(file);
                if (problem) {
                    event.preventDefault();
                    setMessage(problem, true);
                }
            });
        }
    });
})();
