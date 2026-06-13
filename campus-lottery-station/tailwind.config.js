var config = {
    content: ["./index.html", "./src/**/*.{ts,tsx}"],
    theme: {
        extend: {
            colors: {
                canvas: "#F6F7F9",
                surface: "#FFFFFF",
                cream: "#FFF8E4",
                line: "#E4E7EC",
                ink: "#1D2228",
                muted: "#667085",
                amber: {
                    DEFAULT: "#F5B301",
                    soft: "#FFF1BE",
                    strong: "#B87C00",
                    ink: "#6F4D00",
                },
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
        },
    },
    plugins: [],
};
export default config;
