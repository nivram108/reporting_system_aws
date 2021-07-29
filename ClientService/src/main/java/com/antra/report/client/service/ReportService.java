package com.antra.report.client.service;

import com.antra.report.client.pojo.FileType;
import com.antra.report.client.pojo.reponse.ReportVO;
import com.antra.report.client.pojo.reponse.SqsResponse;
import com.antra.report.client.pojo.request.ReportRequest;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;

public interface ReportService {
    ReportVO generateReportsSync(ReportRequest request);

    ReportVO generateReportsAsync(ReportRequest request);

    void updateReportFromResponse(SqsResponse response, FileType type);

    void updateReportFileData(SqsResponse response, FileType type);

    List<ReportVO> getReportList();

    ReportVO getReport(String reqId);

    String deleteReport(String reqId);

    ReportVO updateReport(String reqId, ReportRequest request);

    InputStream getFileBodyByReqId(String reqId, FileType type);


}
