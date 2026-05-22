package model

import "time"

type MediaFile struct {
	ID              string     `json:"id"`
	FileName        string     `json:"fileName"`
	OriginalName    string     `json:"originalName"`
	Ext             string     `json:"ext"`
	MediaType       string     `json:"mediaType"`
	MIMEType        string     `json:"mimeType"`
	StorageDriver   string     `json:"storageDriver"`
	StoragePath     string     `json:"storagePath"`
	AccessURL       string     `json:"accessUrl"`
	CoverURL        string     `json:"coverUrl,omitempty"`
	SizeBytes       int64      `json:"sizeBytes"`
	SHA256          string     `json:"sha256,omitempty"`
	Width           int        `json:"width,omitempty"`
	Height          int        `json:"height,omitempty"`
	DurationSeconds int        `json:"durationSeconds,omitempty"`
	Status          string     `json:"status"`
	Source          string     `json:"source"`
	UploaderID      string     `json:"uploaderId,omitempty"`
	UploaderIP      string     `json:"uploaderIp,omitempty"`
	BizType         string     `json:"bizType,omitempty"`
	BizID           string     `json:"bizId,omitempty"`
	SortOrder       int        `json:"sortOrder,omitempty"`
	CreatedAt       time.Time  `json:"createdAt"`
	UpdatedAt       time.Time  `json:"updatedAt"`
	DeletedAt       *time.Time `json:"deletedAt,omitempty"`
}

type UploadResult struct {
	TaskID        string     `json:"taskId"`
	FileID        string     `json:"fileId"`
	MediaType     string     `json:"mediaType"`
	AccessURL     string     `json:"accessUrl"`
	CoverURL      string     `json:"coverUrl,omitempty"`
	FileName      string     `json:"fileName"`
	OriginalName  string     `json:"originalName"`
	MIMEType      string     `json:"mimeType"`
	SizeBytes     int64      `json:"sizeBytes"`
	SHA256        string     `json:"sha256,omitempty"`
	Width         int        `json:"width,omitempty"`
	Height        int        `json:"height,omitempty"`
	DurationSec   int        `json:"durationSeconds,omitempty"`
	InstantUpload bool       `json:"instantUpload"`
	CreatedAt     *time.Time `json:"createdAt,omitempty"`
}

type FileListFilter struct {
	ListQuery
	BizType string
	BizID   string
}
