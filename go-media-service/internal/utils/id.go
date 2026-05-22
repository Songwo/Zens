package utils

import (
	"crypto/rand"
	"encoding/hex"
	"strings"
	"time"
)

func NewID(prefix string) string {
	now := time.Now().UnixNano()
	buf := make([]byte, 6)
	_, _ = rand.Read(buf)
	var builder strings.Builder
	if prefix != "" {
		builder.WriteString(prefix)
		builder.WriteByte('_')
	}
	builder.WriteString(strings.ToLower(time.Unix(0, now).Format("20060102150405")))
	builder.WriteByte('_')
	builder.WriteString(hex.EncodeToString(buf))
	return builder.String()
}
