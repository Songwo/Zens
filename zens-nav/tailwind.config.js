var config = {
    content: ["./index.html", "./src/**/*.{ts,tsx}"],
    theme: {
        extend: {
            colors: {
                canvas: "#F6F7F9",
                surface: "#FFFFFF",
                cream: "#FFF8E4",
                line: "#E4E7EC",
                lineSoft: "#EEF1F4",
                ink: "#1D2228",
                muted: "#667085",
                mutedStrong: "#475467",
                amber: {
                    DEFAULT: "#F5B301",
                    soft: "#FFF4CF",
                    softer: "#FFFAEC",
                    strong: "#B87C00",
                    ink: "#654300",
                },
                success: "#2D7A46",
                caution: "#B86E00",
            },
            fontFamily: {
                sans: [
                    "Inter",
                    "system-ui",
                    "-apple-system",
                    "BlinkMacSystemFont",
                    "Segoe UI",
                    "PingFang SC",
                    "Microsoft YaHei",
                    "sans-serif",
                ],
            },
            borderRadius: {
                nav: "8px",
            },
            boxShadow: {
                none: "none",
            },
        },
    },
    plugins: [],
};
export default config;
