package com.antra.report.client.handler;

import com.antra.report.client.exception.RequestNotFoundException;
import com.antra.report.client.pojo.FileType;
import com.antra.report.client.pojo.reponse.SqsResponse;
import com.antra.report.client.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.aws.messaging.listener.annotation.SqsListener;
import org.springframework.stereotype.Component;

/**
 * ReportSQSListener handles the responses from both PDFService and ExcelService, which tell the ClientService the asynchronous report request for
 * generating PDF and Excel report files is complete or failed
 */
@Component
public class ReportSQSListener {

    private static final Logger log = LoggerFactory.getLogger(ReportSQSListener.class);

    private ReportService reportService;

    public ReportSQSListener(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Triggered when receiving request response from PDF service, which is responding the async report request sent earlier
     * This will lead to updating tne target report with the PDF file location and report status.
     * @param response response from PDF service
     */
    @SqsListener("PDF_Response_Queue")
    public void responseQueueListenerPdf(SqsResponse response) {
        log.info("Get response from sqs : {}", response);
        //queueListener(request.getPdfRequest());
        reportService.updateReportFromResponse(response, FileType.PDF);
    }


    /**
     * Triggered when receiving request response from Excel service, which is responding the async report request sent earlier
     * This will lead to updating tne target report with the Excel file location and report status.
     * @param response response from Excel service
     */
    @SqsListener("Excel_Response_Queue")
    public void responseQueueListenerExcel(SqsResponse response) {
        log.info("Get response from sqs : {}", response);
        //queueListener(request.getPdfRequest());
        reportService.updateReportFromResponse(response, FileType.EXCEL);

    }

//    @SqsListener(value = "Excel_Response_Queue", deletionPolicy = SqsMessageDeletionPolicy.NEVER)
//    public void responseQueueListenerExcelManualAcknowledge(SqsResponse response, Acknowledgment ack) {
//        log.info("Get response from sqs : {}", response);
//        log.info("Manually Acknowledge");
//        //queueListener(request.getPdfRequest());
//        reportService.updateAsyncExcelReport(response);
//        ack.acknowledge();
//    }
}
