package model

type Response struct {
	Code      int    `json:"code"`
	Message   string `json:"message"`
	RequestID string `json:"requestId,omitempty"`
	Data      any    `json:"data,omitempty"`
}

type Pagination struct {
	Page      int   `json:"page"`
	PageSize  int   `json:"pageSize"`
	Total     int64 `json:"total"`
	TotalPage int64 `json:"totalPage"`
}

type PagedData struct {
	Items      any        `json:"items"`
	Pagination Pagination `json:"pagination"`
}

type ListQuery struct {
	Page      int
	PageSize  int
	Keyword   string
	Status    string
	MediaType string
	SortBy    string
	SortOrder string
	StartAt   string
	EndAt     string
}
