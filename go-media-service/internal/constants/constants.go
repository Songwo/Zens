package constants

const (
	MediaTypeImage = "image"
	MediaTypeVideo = "video"

	FileStatusActive  = "active"
	FileStatusDeleted = "deleted"

	TaskSourceSingle = "single"
	TaskSourceBatch  = "batch"
	TaskSourceChunk  = "chunk"

	TaskStatusPending   = "pending"
	TaskStatusUploading = "uploading"
	TaskStatusMerging   = "merging"
	TaskStatusSuccess   = "success"
	TaskStatusFailed    = "failed"
	TaskStatusCanceled  = "canceled"
)

const (
	CodeOK                   = 0
	CodeBadRequest           = 40000
	CodeUnauthorized         = 40100
	CodeForbidden            = 40300
	CodeNotFound             = 40400
	CodeValidation           = 42200
	CodeRateLimited          = 42900
	CodeInternal             = 50000
	CodeServiceBusy          = 50300
	CodeUploadDisabled       = 50310
	CodeChunkIncomplete      = 40910
	CodeFileIntegrityInvalid = 42210
)
