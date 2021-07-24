package com.antra.report.client.service;

import com.amazonaws.services.s3.AmazonS3;
import com.antra.report.client.entity.ExcelReportEntity;
import com.antra.report.client.entity.PDFReportEntity;
import com.antra.report.client.entity.ReportRequestEntity;
import com.antra.report.client.entity.ReportStatus;
import com.antra.report.client.exception.RequestNotFoundException;
import com.antra.report.client.pojo.EmailType;
import com.antra.report.client.pojo.FileType;
import com.antra.report.client.pojo.reponse.ExcelResponse;
import com.antra.report.client.pojo.reponse.PDFResponse;
import com.antra.report.client.pojo.reponse.ReportVO;
import com.antra.report.client.pojo.reponse.SqsResponse;
import com.antra.report.client.pojo.request.ReportRequest;
import com.antra.report.client.repository.ReportRequestRepo;
//import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

@Service
public class ReportServiceImpl implements ReportService {
    private static final Logger log = LoggerFactory.getLogger(ReportServiceImpl.class);

    private final ReportRequestRepo reportRequestRepo;
    private final SNSService snsService;
    private final AmazonS3 s3Client;
    private final EmailService emailService;

    @Autowired
    private EurekaClient pdfClient;

    @Autowired
    private EurekaClient excelClient;

    @Autowired
    private RestTemplate restTemplate;

    public ReportServiceImpl(ReportRequestRepo reportRequestRepo, SNSService snsService, AmazonS3 s3Client, EmailService emailService) {
        this.reportRequestRepo = reportRequestRepo;
        this.snsService = snsService;
        this.s3Client = s3Client;
        this.emailService = emailService;
    }

    /**
     * Update the ReportRequestEntity with provided id
     * @param reqId
     * @param request
     * @return
     */
    @Transactional
    public ReportVO updateReport(String reqId, ReportRequest request) {
        ReportRequestEntity reportRequestEntity = reportRequestRepo.findById(reqId).orElse(null);
        reportRequestEntity.getExcelReport().setStatus(ReportStatus.PENDING);
        reportRequestEntity.getPdfReport().setStatus(ReportStatus.PENDING);
        reportRequestEntity.setSubmitter(request.getSubmitter());
        reportRequestEntity.getPdfReport().setCreatedTime(LocalDateTime.now());
        reportRequestEntity.getExcelReport().setCreatedTime(LocalDateTime.now());
        reportRequestEntity.setCreatedTime(LocalDateTime.now());

        reportRequestRepo.save(reportRequestEntity);

        request.setReqId(reqId);
        snsService.sendReportNotification(request);
        log.info("Send SNS the message: {}",request);
        return new ReportVO(reportRequestEntity);
    }

    /**
     * Create a ReportRequestEntity including PDFReportEntity and ExcelReportEntity
     * Save the ReportRequestEntity to local
     * @param request user's report request
     * @return completed ReportRequestEntity
     */
    private ReportRequestEntity persistToLocal(ReportRequest request) {
        request.setReqId("Req-"+ UUID.randomUUID().toString());

        ReportRequestEntity entity = new ReportRequestEntity();
        entity.setReqId(request.getReqId());
        entity.setSubmitter(request.getSubmitter());
        entity.setDescription(request.getDescription());
        entity.setCreatedTime(LocalDateTime.now());

        PDFReportEntity pdfReport = new PDFReportEntity();
        pdfReport.setRequest(entity);
        pdfReport.setStatus(ReportStatus.PENDING);
        pdfReport.setCreatedTime(LocalDateTime.now());
        entity.setPdfReport(pdfReport);

        ExcelReportEntity excelReport = new ExcelReportEntity();
        BeanUtils.copyProperties(pdfReport, excelReport);
        entity.setExcelReport(excelReport);

        return reportRequestRepo.save(entity);
    }

    /**
     * Create a complete RequestReportEntity with PDFReportEntity and ExcelReportEntity
     * Use the completed RequestReportEntity to create report files through Excel and PDF Services
     * @param request user's report request
     * @return the requesting report data which may contain the report file location for future download
     */
    @Override
    public ReportVO generateReportsSync(ReportRequest request) {
        persistToLocal(request);
        long startParallel = System.currentTimeMillis();
        sendDirectRequestsParallel(request);
        long startSingle = System.currentTimeMillis();
//        sendDirectRequests(request);
//        long end = System.currentTimeMillis();
        log.info("Parallel time:" + (startSingle - startParallel));
//        log.info("Single time:" + (end - startSingle));
        return new ReportVO(reportRequestRepo.findById(request.getReqId()).orElseThrow());
    }

    /**
     * Create report files through Excel and PDF Services and get the response data, which may contain the saved file location
     * Update the ExcelReportEntity and PDFReportEntity in the previous saved RequestReportEntity
     * @param reportRequest user's report request
     */
    //TODO:Change to parallel process using Threadpool? CompletableFuture?
    private void sendDirectRequestsParallel(ReportRequest reportRequest) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ReportRequest> httpEntity = new HttpEntity<>(reportRequest, headers);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);
        CompletableFuture<Void> excelReportFuture = CompletableFuture.runAsync(() -> {
            ExcelResponse excelResponse = new ExcelResponse();
            String url = excelClient.getNextServerFromEureka("excel-service", false).getHomePageUrl();
            log.info("Get Excel Service url: " + url);
            try {
                excelResponse = restTemplate.postForEntity( "http://excel-service/excel", httpEntity, ExcelResponse.class).getBody();
            } catch(Exception e){
                log.error("Excel Generation Error (Sync) : e", e);
                excelResponse.setReqId(reportRequest.getReqId());
                excelResponse.setFailed(true);
            } finally {
                updateLocal(excelResponse);
            }

        }, executor);
        CompletableFuture<Void> pdfReportFuture = CompletableFuture.runAsync(() -> {
            PDFResponse pdfResponse = new PDFResponse();
            String url = pdfClient.getNextServerFromEureka("pdf-service", false).getHomePageUrl();
            log.info("Get PDF Service url: " + url);
            try {
                pdfResponse = restTemplate.postForEntity("http://pdf-service/pdf", httpEntity, PDFResponse.class).getBody();
            } catch(Exception e){
                log.error("PDF Generation Error (Sync) : e", e);
                pdfResponse.setReqId(reportRequest.getReqId());
                pdfResponse.setFailed(true);
            } finally {
                updateLocal(pdfResponse);
            }
        }, executor);

        CompletableFuture<Void> reportFuture = CompletableFuture.allOf(excelReportFuture, pdfReportFuture);
        try {
            reportFuture.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }


    }

    private void sendDirectRequests(ReportRequest reportRequest) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ExcelResponse excelResponse = new ExcelResponse();
        PDFResponse pdfResponse = new PDFResponse();
        log.info("Request :" + reportRequest);
        HttpEntity<ReportRequest> httpEntity = new HttpEntity<>(reportRequest, headers);
        try {
            excelResponse = restTemplate.postForEntity("http://localhost:8888/excel", httpEntity, ExcelResponse.class).getBody();
        } catch(Exception e){
            log.error("Excel Generation Error (Sync) : e", e);
            excelResponse.setReqId(reportRequest.getReqId());
            excelResponse.setFailed(true);
        } finally {
            updateLocal(excelResponse);
        }

        try {
            pdfResponse = restTemplate.postForEntity("http://localhost:9999/pdf", httpEntity, PDFResponse.class).getBody();
        } catch(Exception e){
            log.error("PDF Generation Error (Sync) : e", e);
            pdfResponse.setReqId(reportRequest.getReqId());
            pdfResponse.setFailed(true);
        } finally {
            updateLocal(pdfResponse);
        }



    }
    /**
     * Update the ExcelReportEntity with generated file location if ExcelService generated the file successfully
     * @param excelResponse ExcelService response
     */
    private void updateLocal(ExcelResponse excelResponse) {
        SqsResponse response = new SqsResponse();
        BeanUtils.copyProperties(excelResponse, response);
        updateAsyncExcelReport(response);
    }

    /**
     * Update the PDFReportEntity with generated file location if PDFService generated the file successfully
     * @param pdfResponse PDFService response
     */
    private void updateLocal(PDFResponse pdfResponse) {
        SqsResponse response = new SqsResponse();
        BeanUtils.copyProperties(pdfResponse, response);
        updateAsyncPDFReport(response);
    }

    /**
     * Create a simple RequestReportEntity without PDFReportEntity and ExcelReportEntity
     * Send the request to SNS to assign report request to PDF and Excel Services
     * After the file is generated, the file location will be updated in the RequestReportEntity
     * @param request user's report request
     * @return the requesting report data which may contain the report file location for future download
     */
    @Override
    @Transactional
    public ReportVO generateReportsAsync(ReportRequest request) {
        ReportRequestEntity entity = persistToLocal(request);
        snsService.sendReportNotification(request);
        log.info("Send SNS the message: {}",request);
        return new ReportVO(entity);
    }

    /**
     * Update the PDFEntity in the previous saved ReportRequestEntity if the file is successfully generated
     * Send an email afterward.
     * @param response SqsResponse that converted from the PDFService response
     */
    @Override
//    @Transactional // why this? email could fail
    public void updateAsyncPDFReport(SqsResponse response) {
        ReportRequestEntity entity = reportRequestRepo.findById(response.getReqId()).orElseThrow(RequestNotFoundException::new);
        var pdfReport = entity.getPdfReport();
        pdfReport.setUpdatedTime(LocalDateTime.now());
        if (response.isFailed()) {
            pdfReport.setStatus(ReportStatus.FAILED);
        } else{
            pdfReport.setStatus(ReportStatus.COMPLETED);
            pdfReport.setFileId(response.getFileId());
            pdfReport.setFileLocation(response.getFileLocation());
            pdfReport.setFileSize(response.getFileSize());
        }
        entity.setUpdatedTime(LocalDateTime.now());
        reportRequestRepo.save(entity);
        String to = "youremail@gmail.com";
        emailService.sendEmail(to, EmailType.SUCCESS, entity.getSubmitter());
    }


    /**
     * Update the ExcelEntity in the previous saved ReportRequestEntity if the file is successfully generated
     * Send an email afterward.
     * @param response SqsResponse that converted from the ExcelService response
     */
    @Override
//    @Transactional
    public void updateAsyncExcelReport(SqsResponse response) {
        ReportRequestEntity entity = reportRequestRepo.findById(response.getReqId()).orElseThrow(RequestNotFoundException::new);
        var excelReport = entity.getExcelReport();
        excelReport.setUpdatedTime(LocalDateTime.now());
        if (response.isFailed()) {
            excelReport.setStatus(ReportStatus.FAILED);
        } else{
            excelReport.setStatus(ReportStatus.COMPLETED);
            excelReport.setFileId(response.getFileId());
            excelReport.setFileLocation(response.getFileLocation());
            excelReport.setFileSize(response.getFileSize());
        }
        entity.setUpdatedTime(LocalDateTime.now());
        reportRequestRepo.save(entity);
        String to = "youremail@gmail.com";
        emailService.sendEmail(to, EmailType.SUCCESS, entity.getSubmitter());
    }


    /**
     * Get all ReportRequestEntity and map them into ReportValueObject in list
     * @return ReportValueObject in list
     */
    @Override
    @Transactional(readOnly = true)
    public List<ReportVO> getReportList() {
        return reportRequestRepo.findAll().stream().map(ReportVO::new).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ReportVO getReport(String reqId) {
        return new ReportVO(reportRequestRepo.findById(reqId).orElse(null));

    }

    @Override
    @Transactional
    public String deleteReport(String reqId) {
        reportRequestRepo.deleteById(reqId);
        return "report " + reqId + " is deleted.";
    }

    @Override
    public InputStream getFileBodyByReqId(String reqId, FileType type) {
        RestTemplate restTemplate = new RestTemplate();
        ReportRequestEntity entity = reportRequestRepo.findById(reqId).orElseThrow(RequestNotFoundException::new);
        if (type == FileType.PDF) {
            String fileLocation = entity.getPdfReport().getFileLocation(); // this location is s3 "bucket/key"
            String bucket = fileLocation.split("/")[0];
            String key = fileLocation.split("/")[1];
            return s3Client.getObject(bucket, key).getObjectContent();
        } else if (type == FileType.EXCEL) {
            String fileId = entity.getExcelReport().getFileId();
//            String fileLocation = entity.getExcelReport().getFileLocation();
//            try {
//                return new FileInputStream(fileLocation);// this location is in local, definitely sucks
//            } catch (FileNotFoundException e) {
//                log.error("No file found", e);
//            }
//            InputStream is = restTemplate.execute(, HttpMethod.GET, null, ClientHttpResponse::getBody, fileId);
            ResponseEntity<Resource> exchange = restTemplate.exchange("http://localhost:8888/excel/{id}/content",
                    HttpMethod.GET, null, Resource.class, fileId);
            try {
                return exchange.getBody().getInputStream();
            } catch (IOException e) {
                log.error("Cannot download excel",e);
            }
        }
        return null;
    }
}
