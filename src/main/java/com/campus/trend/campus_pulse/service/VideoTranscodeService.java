package com.campus.trend.campus_pulse.service;

import java.nio.file.Path;

public interface VideoTranscodeService {

    void submitTranscodeIfNeeded(Path filePath, String extension);
}

