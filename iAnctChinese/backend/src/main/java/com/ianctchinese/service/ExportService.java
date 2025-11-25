package com.ianctchinese.service;

import com.ianctchinese.dto.TextExportBundle;

public interface ExportService {

  TextExportBundle buildExport(Long textId);
}
