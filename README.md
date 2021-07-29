# Reportimg System Aws - Chun-Liang Yang

## Development notes

### Environment
- Environment setup, including SNS, S3, and sendEmail Lambda function.
- Solving issue of Gmail authentication for sendEmail function
- Add description for most methods in Component classes in Javadoc comment.
- Implement Eureka service. Add one Eureka server and make three existing service into Eureka clients.
- Find suitable Spring Cloud version(`Hoxton.SR5`) which is compatible to current Spring Boot version (`2.3.0.RELEASE`)
- Solving dependency issue due to Jersey RestTemplate bean conflict problem.

### Client Service
- Add descriptions to most methods in Component level in Javadoc comment format.
- Improve functionality of using Sample Data to demo. Replace copy-paste method to button generated content.
- Replace `sendDirectRequests` with `sendDirectRequestsParallel`, using `CompletableFuture` and `FixedThreadPool`
- Use Eureka discovery for calling request API of `ExcelService` and `PDFService` in `sendDirectRequestsParallel`
- Add HttpHeaders as specifying JSON as content type in `sendDirectRequestsParallel`
- Add multiple Rest APIs
    - GET single report by id
    - DELETE single report by id
    - UPDATE single report by id
- Separate sendEmail from `@Transactional updateReport` with Sqsresponse function
- Merge `updateAsyncExcelReport` and `updateAsyncPDFReport` and use extra param FileType for choosing updating report file type

### ExcelService
- Save Excel file to S3
- Remove saving Excel file on local in `excelGenerationService`
- Annotate the consumes and produces of `createExcel` as JSON to avoid content type error
- Implement delete report by id, while also delete file on S3
- Change the fileLocation to ExcelService port, and make the download api download Excel file from S3

### PDFService
- Annotate the consumes and produces of `createExcel` as JSON to avoid content type error

## Other Discussions

- Naming : following the current naming format, but naming of `Pdf` / `PDF` is inconsistent. Most part in the project is using `PDF`, while ReportSQSListener is using `Pdf`.
