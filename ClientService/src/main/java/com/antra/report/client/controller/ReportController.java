package com.antra.report.client.controller;

import com.antra.report.client.pojo.FileType;
import com.antra.report.client.pojo.reponse.ErrorResponse;
import com.antra.report.client.pojo.reponse.GeneralResponse;
import com.antra.report.client.pojo.request.ReportRequest;
import com.antra.report.client.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileCopyUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Collectors;

/**
 * ReportController is mainly handling the interactions from the web page, and sends the request to the backend to process.
 */
@RestController
public class ReportController {
    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

//    @Autowired
//    private ReportService reportService;

    private final ReportService reportService;
    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Get single report by id
     * @param id report id to get
     * @return report value object
     */
    @GetMapping("/report/content/{id}")
    public ResponseEntity<GeneralResponse> findReportById(@PathVariable String id) {
        log.info("Got Request to find report by id: " + id);
        return ResponseEntity.ok(new GeneralResponse(reportService.getReport(id)));
    }

    /**
     * Get report list using get method when the webpage is loaded
     * @return report list in the body with HTTP.ok status (200)
     */
    @GetMapping("/report")
    public ResponseEntity<GeneralResponse> listReport() {
        log.info("Got Request to list all report");
        return ResponseEntity.ok(new GeneralResponse(reportService.getReportList()));
    }

    /**
     * Delete the report by id
     * @param reqId report id to delete
     * @return success delete message to user
     */
    @DeleteMapping("/report/delete/{reqId}")
    public ResponseEntity<GeneralResponse> deleteReport(@PathVariable String reqId) {
        log.info("Got Request to delete report by id: " + reqId);
        return ResponseEntity.ok(new GeneralResponse(reportService.deleteReport(reqId)));
    }

    /**
     * Update the report by id
     * @param reqId report id to update
     * @param request report data to update
     * @return
     */
    @PutMapping("/report/update/{reqId}")
    public ResponseEntity<GeneralResponse> updateReport(@PathVariable String reqId, @RequestBody ReportRequest request) {
        log.info("Got Request to update report " + reqId);
        return ResponseEntity.ok(new GeneralResponse(reportService.updateReport(reqId, request)));
    }

    /**
     * Generate report synchronously, have to complete generating PDF and Excel report to proceed
     * After the report files are generated, the file location will be updated for future downloading
     * Also specifying the request is for Sync report
     * The ReportRequestEntity object will be stored in the database
     * @param request user input data for reports
     * @return HTTP.ok response with completed ReportRequestEntity
     */
    @PostMapping("/report/sync")
    public ResponseEntity<GeneralResponse> createReportDirectly(@RequestBody @Validated ReportRequest request) {
        log.info("Got Request to generate report - sync: {}", request);
        request.setDescription(String.join(" - ", "Sync", request.getDescription()));

        return ResponseEntity.ok(new GeneralResponse(reportService.generateReportsSync(request)));
    }



    /**
     * Generate report asynchronously, no need to wait for generating report to proceed.
     * The report generating tasks will be assigned to services through sns.
     * A RequestReportEntity will be stored in the database immediately, but without report file location.
     * After the report generating services complete their task, file location and status will be updated in the RequestReportEntity.
     * @param request user input data for reports
     * @return HTTP.ok response with simple ReportRequestEntity
     */
    @PostMapping("/report/async")
    public ResponseEntity<GeneralResponse> createReportAsync(@RequestBody @Validated ReportRequest request) {
        log.info("Got Request to generate report - async: {}", request);
        request.setDescription(String.join(" - ", "Async", request.getDescription()));

        return ResponseEntity.ok(new GeneralResponse(reportService.generateReportsAsync(request)));
    }

    /**
     * Allow the user download the report file based on the file location in the RequestReportEntity
     * @param reqId The requested report id
     * @param type may be PDF or Excel, depends on which user chooses to download
     * @param response
     * @throws IOException
     */
    @GetMapping("/report/content/{reqId}/{type}")
    public void downloadFile(@PathVariable String reqId, @PathVariable FileType type, HttpServletResponse response) throws IOException {
        log.debug("Got Request to Download File - type: {}, reqid: {}", type, reqId);
        InputStream fis = reportService.getFileBodyByReqId(reqId, type);
        String fileType = null;
        String fileName = null;
        if(type == FileType.PDF) {
            fileType = "application/pdf";
            fileName = "report.pdf";
        } else if (type == FileType.EXCEL) {
            fileType = "application/vnd.ms-excel";
            fileName = "report.xls";
        }
        response.setHeader("Content-Type", fileType);
        response.setHeader("fileName", fileName);
        if (fis != null) {
            FileCopyUtils.copy(fis, response.getOutputStream());
        } else{
            response.setStatus(500);
        }
        log.debug("Downloaded File:{}", reqId);
    }

//   @DeleteMapping
//   @PutMapping

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GeneralResponse> handleValidationException(MethodArgumentNotValidException e) {
        log.warn("Input Data invalid: {}", e.getMessage());
        String errorFields = e.getBindingResult().getFieldErrors().stream().map(fe -> String.join(" ",fe.getField(),fe.getDefaultMessage())).collect(Collectors.joining(", "));
        return new ResponseEntity<>(new ErrorResponse(HttpStatus.BAD_REQUEST, errorFields), HttpStatus.BAD_REQUEST);
    }
}
