$ErrorActionPreference = "Stop"

$body = @{
  question = "Spring Boot 登录态频繁失效怎么排查？"
  limit = 5
  include_comments = $true
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri "http://127.0.0.1:7810/v1/community-qa/ask" `
  -ContentType "application/json" `
  -Body $body
