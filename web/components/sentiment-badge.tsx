// 情感分析徽章组件
export function SentimentBadge({ sentimentLabel }: { sentimentLabel?: string }) {
    if (!sentimentLabel) return null

    const config = {
        positive: {
            label: "积极",
            className: "bg-green-100 text-green-700 border-green-200",
            emoji: "😊"
        },
        neutral: {
            label: "中性",
            className: "bg-gray-100 text-gray-700 border-gray-200",
            emoji: "😐"
        },
        negative: {
            label: "消极",
            className: "bg-orange-100 text-orange-700 border-orange-200",
            emoji: "😔"
        }
    }

    const sentiment = config[sentimentLabel as keyof typeof config]
    if (!sentiment) return null

    return (
        <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium border ${sentiment.className}`}>
            <span>{sentiment.emoji}</span>
            <span>{sentiment.label}</span>
        </span>
    )
}
