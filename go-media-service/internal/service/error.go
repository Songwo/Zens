package service

type AppError struct {
	Code       int
	HTTPStatus int
	Message    string
	Cause      error
}

func (e *AppError) Error() string {
	return e.Message
}

func (e *AppError) Unwrap() error {
	return e.Cause
}

func NewAppError(code int, httpStatus int, message string, cause error) *AppError {
	return &AppError{
		Code:       code,
		HTTPStatus: httpStatus,
		Message:    message,
		Cause:      cause,
	}
}
