package main

import (
	"fmt"
	"os"

	"github.com/golang-jwt/jwt/v5"
)

func main() {
	// 调试脚本：密钥与待验 token 从环境变量读取，不再硬编码明文。
	//   CDK_COMMUNITY_JWT_SECRET=... CDK_DEBUG_SSO_TOKEN=... go run test_jwt.go
	secretStr := os.Getenv("CDK_COMMUNITY_JWT_SECRET")
	if secretStr == "" {
		fmt.Println("请设置 CDK_COMMUNITY_JWT_SECRET")
		return
	}
	secret := []byte(secretStr)
	ssoToken := os.Getenv("CDK_DEBUG_SSO_TOKEN")
	if ssoToken == "" {
		fmt.Println("请设置 CDK_DEBUG_SSO_TOKEN")
		return
	}
	token, err := jwt.Parse(ssoToken, func(token *jwt.Token) (interface{}, error) {
		if _, ok := token.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("unexpected signing method")
		}
		return secret, nil
	})
	if err != nil {
		fmt.Println("Error:", err)
		return
	}
	fmt.Println("Valid:", token.Valid)
	fmt.Println("Claims:", token.Claims)
}
