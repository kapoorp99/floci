package io.github.hectorvent.floci.lifecycle;

import io.github.hectorvent.floci.core.common.ServiceRegistry;
import io.github.hectorvent.floci.lifecycle.inithook.InitializationHook;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.github.hectorvent.floci.core.storage.StorageFactory;
import io.github.hectorvent.floci.services.lambda.SqsEventSourcePoller;
import io.github.hectorvent.floci.services.lambda.KinesisEventSourcePoller;
import io.github.hectorvent.floci.services.lambda.DynamoDbStreamsEventSourcePoller;
import io.github.hectorvent.floci.services.pipes.PipesPoller;
import io.github.hectorvent.floci.services.eventbridge.RuleScheduler;
import io.github.hectorvent.floci.services.elbv2.ElbV2HealthChecker;
import io.github.hectorvent.floci.services.lambda.LambdaConcurrencyLimiter;
import io.github.hectorvent.floci.services.lambda.LambdaFunctionStore;
import io.github.hectorvent.floci.services.s3.S3Service;
import io.github.hectorvent.floci.services.sqs.SqsService;
import io.github.hectorvent.floci.services.sns.SnsService;
import io.github.hectorvent.floci.services.resourcegroupstagging.ResourceGroupsTaggingService;
import io.github.hectorvent.floci.services.transcribe.TranscribeService;
import io.github.hectorvent.floci.services.textract.TextractService;
import io.github.hectorvent.floci.services.stepfunctions.StepFunctionsService;
import io.github.hectorvent.floci.services.rdsdata.RdsDataService;
import io.github.hectorvent.floci.services.rds.RdsService;
import io.github.hectorvent.floci.services.ssm.SsmCommandService;
import io.github.hectorvent.floci.services.scheduler.ScheduleDispatcher;
import io.github.hectorvent.floci.services.apigatewayv2.websocket.WebSocketConnectionManager;
import jakarta.ws.rs.POST;

import java.util.LinkedHashMap;
import java.util.Map;

@Path("{prefix:(_floci|_localstack)}")
@Produces(MediaType.APPLICATION_JSON)
public class EmulatorInfoController {

    private final ServiceRegistry serviceRegistry;
    private final InitLifecycleState initLifecycleState;
    private final String version;

    private final StorageFactory storageFactory;
    private final SqsEventSourcePoller sqsEventSourcePoller;
    private final KinesisEventSourcePoller kinesisEventSourcePoller;
    private final DynamoDbStreamsEventSourcePoller dynamoDbStreamsEventSourcePoller;
    private final PipesPoller pipesPoller;
    private final RuleScheduler ruleScheduler;
    private final ElbV2HealthChecker elbV2HealthChecker;
    private final LambdaConcurrencyLimiter lambdaConcurrencyLimiter;
    private final LambdaFunctionStore lambdaFunctionStore;
    private final S3Service s3Service;
    private final SqsService sqsService;
    private final SnsService snsService;
    private final ResourceGroupsTaggingService resourceGroupsTaggingService;
    private final TranscribeService transcribeService;
    private final TextractService textractService;
    private final StepFunctionsService stepFunctionsService;
    private final RdsDataService rdsDataService;
    private final RdsService rdsService;
    private final SsmCommandService ssmCommandService;
    private final ScheduleDispatcher scheduleDispatcher;
    private final WebSocketConnectionManager webSocketConnectionManager;

    @Inject
    public EmulatorInfoController(ServiceRegistry serviceRegistry,
                                  InitLifecycleState initLifecycleState,
                                  StorageFactory storageFactory,
                                  SqsEventSourcePoller sqsEventSourcePoller,
                                  KinesisEventSourcePoller kinesisEventSourcePoller,
                                  DynamoDbStreamsEventSourcePoller dynamoDbStreamsEventSourcePoller,
                                  PipesPoller pipesPoller,
                                  RuleScheduler ruleScheduler,
                                  ElbV2HealthChecker elbV2HealthChecker,
                                  LambdaConcurrencyLimiter lambdaConcurrencyLimiter,
                                  LambdaFunctionStore lambdaFunctionStore,
                                  S3Service s3Service,
                                  SqsService sqsService,
                                  SnsService snsService,
                                  ResourceGroupsTaggingService resourceGroupsTaggingService,
                                  TranscribeService transcribeService,
                                  TextractService textractService,
                                  StepFunctionsService stepFunctionsService,
                                  RdsDataService rdsDataService,
                                  RdsService rdsService,
                                  SsmCommandService ssmCommandService,
                                  ScheduleDispatcher scheduleDispatcher,
                                  WebSocketConnectionManager webSocketConnectionManager) {
        this.serviceRegistry = serviceRegistry;
        this.initLifecycleState = initLifecycleState;
        this.storageFactory = storageFactory;
        this.sqsEventSourcePoller = sqsEventSourcePoller;
        this.kinesisEventSourcePoller = kinesisEventSourcePoller;
        this.dynamoDbStreamsEventSourcePoller = dynamoDbStreamsEventSourcePoller;
        this.pipesPoller = pipesPoller;
        this.ruleScheduler = ruleScheduler;
        this.elbV2HealthChecker = elbV2HealthChecker;
        this.lambdaConcurrencyLimiter = lambdaConcurrencyLimiter;
        this.lambdaFunctionStore = lambdaFunctionStore;
        this.s3Service = s3Service;
        this.sqsService = sqsService;
        this.snsService = snsService;
        this.resourceGroupsTaggingService = resourceGroupsTaggingService;
        this.transcribeService = transcribeService;
        this.textractService = textractService;
        this.stepFunctionsService = stepFunctionsService;
        this.rdsDataService = rdsDataService;
        this.rdsService = rdsService;
        this.ssmCommandService = ssmCommandService;
        this.scheduleDispatcher = scheduleDispatcher;
        this.webSocketConnectionManager = webSocketConnectionManager;
        this.version = resolveVersion();
    }

    @GET
    @Path("/health")
    public Response health() {
        return Response.ok(Map.of(
                "services", serviceRegistry.getServices(),
                "edition", "community",
                "original_edition", "floci-always-free",
                "version", version)).build();
    }

    @GET
    @Path("/init")
    public Response init() {
        Map<String, Object> completed = new LinkedHashMap<>();
        completed.put("boot", initLifecycleState.isBootCompleted());
        completed.put("start", initLifecycleState.isStartCompleted());
        completed.put("ready", initLifecycleState.isReadyCompleted());
        completed.put("shutdown", initLifecycleState.isShutdownStarted());

        Map<String, Object> scripts = new LinkedHashMap<>();
        for (InitializationHook hook : InitializationHook.values()) {
            scripts.put(hook.getResponseKey(), initLifecycleState.getScripts(hook).stream()
                    .map(r -> Map.of("script", r.script(), "state", r.state(), "return_code", r.returnCode()))
                    .toList());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("completed", completed);
        body.put("scripts", scripts);
        return Response.ok(body).build();
    }

    @GET
    @Path("/info")
    public Response info() {
        return Response.ok(Map.of("version", version, "edition", "community", "original_edition", "floci-always-free")).build();
    }

    @GET
    @Path("/diagnose")
    public Response diagnose() {
        return Response.ok(Map.of()).build();
    }

    @GET
    @Path("/config")
    public Response config() {
        return Response.ok(Map.of()).build();
    }

    @POST
    @Path("/state/reset")
    public Response reset() {
        performReset();
        return Response.ok(Map.of("status", "OK")).build();
    }

    @POST
    @Path("/state/nuke")
    public Response nuke() {
        return reset();
    }

    private void performReset() {
        sqsEventSourcePoller.clear();
        kinesisEventSourcePoller.clear();
        dynamoDbStreamsEventSourcePoller.clear();
        pipesPoller.clear();
        ruleScheduler.clear();
        elbV2HealthChecker.clear();
        lambdaConcurrencyLimiter.clear();
        lambdaFunctionStore.clear();
        s3Service.clear();
        sqsService.clear();
        snsService.clear();
        resourceGroupsTaggingService.clear();
        transcribeService.clear();
        textractService.clear();
        stepFunctionsService.clear();
        rdsDataService.clear();
        rdsService.clear();
        ssmCommandService.clear();
        scheduleDispatcher.clear();
        webSocketConnectionManager.clear();

        storageFactory.clearAll();
    }

    static String resolveVersion() {
        String env = System.getenv("FLOCI_VERSION");
        if (env != null && !env.isBlank()) {
            return env;
        }
        return "dev";
    }
}
