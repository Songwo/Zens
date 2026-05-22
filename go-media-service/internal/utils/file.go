package utils

import (
	"bufio"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"io"
	"net/http"
	"path/filepath"
	"strings"
	"sync"
	"unicode"
)

var copyBufferPool = sync.Pool{
	New: func() any {
		buf := make([]byte, 1024*1024)
		return &buf
	},
}

func CopyBuffer() *[]byte {
	return copyBufferPool.Get().(*[]byte)
}

func PutCopyBuffer(buffer *[]byte) {
	if buffer == nil {
		return
	}
	copyBufferPool.Put(buffer)
}

func SafeFileName(name string) string {
	base := filepath.Base(strings.TrimSpace(name))
	if base == "." || base == "" {
		return "unnamed"
	}
	builder := strings.Builder{}
	for _, r := range base {
		switch {
		case unicode.IsLetter(r), unicode.IsNumber(r):
			builder.WriteRune(r)
		case r == '.', r == '-', r == '_':
			builder.WriteRune(r)
		default:
			builder.WriteRune('_')
		}
	}
	clean := strings.Trim(builder.String(), "._ ")
	if clean == "" {
		return "unnamed"
	}
	return clean
}

func NormalizedExt(name string) string {
	return strings.ToLower(filepath.Ext(name))
}

func DetectContentType(reader io.Reader) (string, io.Reader, error) {
	buffered := bufio.NewReader(reader)
	peek, err := buffered.Peek(512)
	if err != nil && !errors.Is(err, io.EOF) {
		return "", nil, err
	}
	return http.DetectContentType(peek), buffered, nil
}

func CopyWithHash(ctx context.Context, dst io.Writer, src io.Reader) (int64, string, error) {
	hasher := sha256.New()
	bufferPtr := CopyBuffer()
	defer PutCopyBuffer(bufferPtr)

	buffer := *bufferPtr
	var total int64
	for {
		select {
		case <-ctx.Done():
			return total, "", ctx.Err()
		default:
		}

		n, err := src.Read(buffer)
		if n > 0 {
			chunk := buffer[:n]
			if _, writeErr := dst.Write(chunk); writeErr != nil {
				return total, "", writeErr
			}
			if _, hashErr := hasher.Write(chunk); hashErr != nil {
				return total, "", hashErr
			}
			total += int64(n)
		}
		if errors.Is(err, io.EOF) {
			return total, hex.EncodeToString(hasher.Sum(nil)), nil
		}
		if err != nil {
			return total, "", err
		}
	}
}
